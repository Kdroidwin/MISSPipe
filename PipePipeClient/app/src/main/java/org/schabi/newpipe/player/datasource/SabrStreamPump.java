package org.schabi.newpipe.player.datasource;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment;
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession;

import java.io.IOException;
import java.util.List;

/**
 * Single consumer of a {@link YoutubeSabrSession}: one daemon thread pumps the server-driven SABR
 * stream and fills the session's (concurrent) segment cache ahead of the play head. The server
 * paces us with policy-only responses once we are far enough ahead. Both the audio and video
 * {@link SabrSegmentDataSource}s only read the cache, so they never fight over the session or block each
 * other on a network round-trip, which is exactly what starved a track in the old on-demand approach.
 */
final class SabrStreamPump {

    private static final String TAG = "SabrStreamPump";
    private static final long IDLE_POLL_MS = 400;     // server paced us / nothing new this round
    private static final long ERROR_RETRY_MS = 1000;  // transient network error
    private static final int MAX_CONSECUTIVE_IO_ERRORS = 5;
    // no reads for this long -> playback is gone. MUST stay above READAHEAD_CUSHION_MS: once the
    // player buffer is full it stops reading us for ~cushion seconds, and killing the pump in that
    // window left the cache to drain dry -> periodic rebuffering.
    private static final long IDLE_STOP_MS = 90_000;
    // Margin the buffered edge stays ahead of the furthest-read track. Driven off the reader (not the
    // play head) it only needs to cover a few segments, so it stays small -> bounded memory even at 4K.
    private static final long READAHEAD_CUSHION_MS = 30_000;
    // Hard byte ceiling on read-ahead so a high-bitrate (4K) stream can't OOM the heap: 50s of 4K is
    // ~160MB and crashed. ~100MB still covers the player's ~30s read-ahead, well under the OOM line.
    private static final long MAX_AHEAD_BYTES = 100L * 1024 * 1024;
    // Keep this much already-played video in the cache so a short backward seek lands on cached
    // segments instead of a hole (eviction used to drop everything the reader passed, so any rewind
    // hit an evicted segment the pump never re-fetches -> dead buffer). Bounded, same order as the
    // forward cushion. Rewinds beyond this still need a session re-request (separate follow-up).
    private static final long BACK_BUFFER_MS = 30_000;
    // Fallback back-buffer used when the cache is already over the byte budget: at high bitrate (4K)
    // a 30s back-buffer + readahead exceeds MAX_AHEAD_BYTES, and since eviction can't drop segments
    // within the back-buffer window the cache can't drain -> the pump throttles forever and stalls.
    // Shrinking the back-buffer when over budget lets eviction free bytes so playback keeps fetching.
    private static final long MIN_BACK_BUFFER_MS = 5_000;
    // The back-buffer is sized by BYTES, not a fixed 30s: 30s of already-played video is ~60MB at 4K
    // (mostly wasted, rewinds are rare) but only ~12MB at 1080p. Holding a constant ~16MB keeps
    // low-res rewinds generous without ballooning the 4K heap. Rewinds past it re-fetch, so
    // correctness is intact; read-ahead (what playback needs) is untouched, so no extra rebuffering.
    private static final long BACK_BUFFER_BYTES = 16L * 1024 * 1024;

    private final YoutubeSabrSession session;
    private final SabrSessionStore.Holder holder;
    private final Localization localization;

    private volatile boolean started;
    private volatile boolean stopped;
    private volatile boolean clearCacheOnStop;
    private volatile boolean fatal;
    private volatile long lastReadMs;
    // Set by a reader blocked on an evicted segment behind the edge (backward seek); the loop
    // repositions the session onto it next round. Single-slot: the latest rewind target wins.
    private volatile SabrSegmentRequest pendingRefetch;
    // Set by a reader blocked on a segment far AHEAD of the buffered edge (cold/forward seek:
    // SponsorBlock skip at start, resume-from-history). The forward pump fills from edge 0 and would
    // take minutes to reach it, so the loop jumps the session onto it next round. Single-slot.
    private volatile SabrSegmentRequest pendingForwardSeek;
    private Thread thread;

    SabrStreamPump(@NonNull final YoutubeSabrSession session,
                   @NonNull final SabrSessionStore.Holder holder,
                   @NonNull final Localization localization) {
        this.session = session;
        this.holder = holder;
        this.localization = localization;
    }

    /** Start (or restart, if it idled out) the pump thread, and mark the session as actively read. */
    void ensureStarted() {
        lastReadMs = System.currentTimeMillis();
        if (fatal || (started && !stopped)) {
            return;
        }
        synchronized (this) {
            if (fatal || (started && !stopped)) {
                return;
            }
            stopped = false;
            started = true;
            thread = new Thread(this::loop, "SabrStreamPump");
            thread.setDaemon(true);
            thread.start();
        }
    }

    /** Stop the pump thread and release it (called on eviction / playback teardown). */
    void stop() {
        synchronized (this) {
            stopped = true;
            clearCacheOnStop = true;
            // Don't self-interrupt: stop() is also reached from the pump thread itself via
            // evict-on-fatal, and setting our own interrupt flag could break a later blocking call.
            if (thread != null && thread != Thread.currentThread()) {
                thread.interrupt();
            }
        }
    }

    @Nullable
    SabrMediaSegment getCached(@NonNull final SabrSegmentRequest request) {
        // revive the pump if it idled out: any read means playback is live again.
        ensureStarted();
        return session.getCachedSegment(request);
    }

    boolean isFatal() {
        return fatal;
    }

    /** A reader is blocked on an evicted segment behind the buffered edge (backward seek). Ask the
     * loop to reposition the session onto it so the server re-sends from there. */
    void requestRefetchFrom(@NonNull final SabrSegmentRequest request) {
        pendingRefetch = request;
        ensureStarted();
    }

    /** A reader is blocked on a segment far ahead of the buffered edge (cold/forward seek, e.g. a
     * SponsorBlock skip at the start). Ask the loop to jump the session onto it so the server streams
     * from there instead of crawling forward from the start. */
    void requestForwardSeekTo(@NonNull final SabrSegmentRequest request) {
        pendingForwardSeek = request;
        ensureStarted();
    }

    private void loop() {
        int consecutiveIoErrors = 0;
        try {
            while (!stopped) {
                // Don't die on completion/idle while a reposition is pending: a backward seek after
                // playback buffered to the end arrives with isComplete()=true, and breaking here
                // meant the restarted pump exited before ever processing the refetch -> the reader
                // waited forever (full buffer on rewind-after-end). prepareForRewind resets the
                // buffered head, so isComplete() turns false again once the reposition runs.
                if (pendingRefetch == null && pendingForwardSeek == null
                        && (System.currentTimeMillis() - lastReadMs > IDLE_STOP_MS
                                || session.isComplete())) {
                    break;
                }
                try {
                    // Drive off what the player has ACTUALLY read, not the play head: the play head
                    // freezes while buffering and that deadlocked the pump. readerHead = furthest
                    // track read; readerTail = slowest track read (safe to evict below).
                    final long readerHeadMs = holder.getReaderHeadMs();
                    // Evict what both tracks have read past, EVERY round (or a full cache never drains
                    // and the throttle latches forever -> freeze), keeping BACK_BUFFER_MS behind the
                    // reader so a short backward seek finds its segments cached. But when the cache is
                    // already over the byte budget (high bitrate), shrink the back-buffer so eviction
                    // can actually drain it, otherwise the pump throttles forever and playback stalls.
                    final long backBufferMs = session.getCachedBytes() > MAX_AHEAD_BYTES
                            ? MIN_BACK_BUFFER_MS : targetBackBufferMs();
                    session.setPlayHeadMs(Math.max(0, holder.getReaderTailMs() - backBufferMs));
                    session.evictPlayed();
                    final long edgeMs = session.getStreamState().getMinBufferedEndMs();
                    // Backward seek beyond the back-buffer: a reader is blocked on an evicted segment
                    // behind the edge. Reposition the session onto it (prepareForMediaSegment sets
                    // buffered=up-to-(seg-1) + playerTime=seg start, so the server re-sends from there)
                    // instead of fetching forward this round. Bypasses the throttle by design.
                    final SabrSegmentRequest refetch = pendingRefetch;
                    if (refetch != null) {
                        pendingRefetch = null;
                        session.prepareForRewind(refetch);
                        session.pumpOnce(localization);
                        consecutiveIoErrors = 0;
                        continue;
                    }
                    // Cold/forward seek (SponsorBlock skip, user seek far ahead): a reader is blocked
                    // on a segment far ahead of the edge. Jump the session onto it
                    // (prepareForForwardJump moves the buffered head to the target, so the edge-driven
                    // pacing follows the new position instead of ping-ponging back to the old span).
                    final SabrSegmentRequest forwardSeek = pendingForwardSeek;
                    if (forwardSeek != null) {
                        pendingForwardSeek = null;
                        session.prepareForForwardJump(forwardSeek);
                        session.pumpOnce(localization);
                        consecutiveIoErrors = 0;
                        continue;
                    }
                    final boolean throttled = edgeMs - readerHeadMs > READAHEAD_CUSHION_MS
                            || session.getCachedBytes() > MAX_AHEAD_BYTES;
                    if (throttled) {
                        Thread.sleep(IDLE_POLL_MS);
                        continue;
                    }
                    // Report the CONTIGUOUS buffered edge (not readerHead): the server fills from the
                    // reported position, so reporting readerHead (ahead of a laggard track) made it
                    // skip past the gap and the slow track's edge never advanced. Pace on readerHead,
                    // report on edge.
                    session.getStreamState().setPlayerTimeMs(edgeMs);
                    final List<SabrMediaSegment> segments = session.pumpOnce(localization);
                    consecutiveIoErrors = 0;
                    if (segments.isEmpty()) {
                        Thread.sleep(IDLE_POLL_MS);
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (final IOException e) {
                    consecutiveIoErrors++;
                    if (consecutiveIoErrors >= MAX_CONSECUTIVE_IO_ERRORS) {
                        Log.w(TAG, "SABR pump network failure; evicting session "
                                + holder.videoId, e);
                        fatal = true;
                        SabrSessionStore.evict(holder.videoId);
                        break;
                    }
                    sleepQuietly(ERROR_RETRY_MS);
                } catch (final ExtractionException e) {
                    Log.i(TAG, "SABR pump fatal: " + e.getMessage());
                    fatal = true;
                    // Drop the dead session so a re-open rebuilds a fresh one (new token, new state).
                    SabrSessionStore.evict(holder.videoId);
                    break;
                } catch (final OutOfMemoryError e) {
                    Log.e(TAG, "SABR pump OOM; evicting session " + holder.videoId, e);
                    fatal = true;
                    SabrSessionStore.evict(holder.videoId);
                    break;
                }
            }
        } finally {
            if (clearCacheOnStop) {
                session.clearCache();
            }
            synchronized (this) {
                stopped = true;
            }
        }
    }

    /** Back-buffer duration for THIS stream's bitrate, so it holds ~{@link #BACK_BUFFER_BYTES}
     * regardless of resolution. Clamped to [MIN, MAX]; falls back to the time-based default when the
     * bitrate is unknown. */
    private long targetBackBufferMs() {
        final long bitsPerSec = (long) holder.videoFormat.getBitrate()
                + Math.max(0, holder.audioFormat.getBitrate());
        if (bitsPerSec <= 0) {
            return BACK_BUFFER_MS;
        }
        final long bytesPerMs = Math.max(1, bitsPerSec / 8 / 1000);
        return Math.max(MIN_BACK_BUFFER_MS,
                Math.min(BACK_BUFFER_MS, BACK_BUFFER_BYTES / bytesPerMs));
    }

    private static void sleepQuietly(final long ms) {
        try {
            Thread.sleep(ms);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
