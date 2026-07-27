package org.schabi.newpipe.extractor.services.youtube.sabr;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class YoutubeSabrSession {
    private static final int MAX_REQUESTS_PER_SEGMENT = 16;
    private static final int MAX_POLICY_ONLY_RESPONSES_PER_SEGMENT = 3;
    private static final int MAX_REDIRECTS_PER_SESSION = 3;
    // server can ask us to reload the player response (URLs/config expired on a long watch). re-probe
    // and resume in place instead of killing the session. bounded so a reload loop can't run forever.
    private static final int MAX_RELOADS_PER_SESSION = 2;
    // How many times a stale/rejected PO token may be force-re-minted before giving up (token
    // expiry mid-playback). Bounded so a genuinely-rejected token can't loop forever.
    private static final int MAX_PO_TOKEN_REFRESHES = 2;
    private static final int MAX_BACKOFF_MS = 30_000;
    // Cap the cached media bytes so a high-bitrate (4K VP9/AV1) stream can't fill the heap and OOM.
    // 32 MiB ≈ ~50s of 4K video, far more than the read-lag, so forward playback never starves.
    private static final long MAX_CACHE_BYTES = 32L * 1024 * 1024;
    private static final int MIN_CACHED_SEGMENTS = 6;
    @Nonnull
    // not final: a server-requested reload swaps in a freshly probed info (new URL + ustreamer config)
    private YoutubeSabrInfo info;
    @Nonnull
    private final YoutubeSabrFormat audioFormat;
    @Nonnull
    private final YoutubeSabrFormat videoFormat;
    @Nonnull
    private final YoutubeSabrStreamState streamState;
    @Nullable
    private final SabrPoTokenProvider poTokenProvider;
    private final Map<String, SabrMediaSegment> segmentCache = new ConcurrentHashMap<>();
    private String serverAbrStreamingUrl;
    private int requestNumber;
    private int redirectCount;
    private int poTokenRefreshes;
    private int reloads;
    // Insertion order + total bytes of cached MEDIA segments (init segments are never evicted).
    // Mutated only by the single pump thread in pumpOnce; readers only do concurrent-map gets.
    private final Deque<String> cacheOrder = new ArrayDeque<>();
    private long cachedBytes;
    // Real play head (ms) fed by the pump, so eviction never drops a segment the player still needs.
    private volatile long playHeadMs;
    // Keep this much already-played media before evicting: the two tracks read slightly apart and a
    // segment ending right at the play head may still be in use (race that starved audio at the edge).
    private static final long EVICT_BEHIND_MS = 10_000;
    // After a seek, collapse the cache to a window of +-this around the target. A large seek leaves the
    // old pre-fetched span disconnected from the new position by a gap; play-head eviction can't drop it
    // (it depends on the reader position, which a seek leaves stale until both tracks read at the
    // target), so the cache held two disjoint spans far over MAX_CACHE_BYTES -> OOM at 4K. The pump
    // re-fetches contiguously from the target, so the out-of-window segments are pure waste.
    private static final long SEEK_KEEP_WINDOW_MS = 30_000;

    public YoutubeSabrSession(@Nonnull final YoutubeSabrInfo info,
                               @Nonnull final YoutubeSabrFormat audioFormat,
                               @Nonnull final YoutubeSabrFormat videoFormat) {
        this(info, audioFormat, videoFormat, null);
    }

    public YoutubeSabrSession(@Nonnull final YoutubeSabrInfo info,
                              @Nonnull final YoutubeSabrFormat audioFormat,
                              @Nonnull final YoutubeSabrFormat videoFormat,
                              @Nullable final SabrPoTokenProvider poTokenProvider) {
        if (!audioFormat.isAudio()) {
            throw new IllegalArgumentException("SABR audio format must be audio: itag="
                    + audioFormat.getItag());
        }
        if (!videoFormat.isVideo()) {
            throw new IllegalArgumentException("SABR video format must be video: itag="
                    + videoFormat.getItag());
        }
        if (audioFormat.getItag() == videoFormat.getItag()) {
            throw new IllegalArgumentException("SABR audio/video formats must be distinct");
        }
        if (info.getServerAbrStreamingUrl() == null || info.getServerAbrStreamingUrl().isEmpty()) {
            throw new IllegalArgumentException("Missing SABR streaming URL");
        }
        this.info = info;
        this.audioFormat = audioFormat;
        this.videoFormat = videoFormat;
        this.streamState = new YoutubeSabrStreamState(audioFormat, videoFormat);
        this.poTokenProvider = poTokenProvider;
        this.serverAbrStreamingUrl = info.getServerAbrStreamingUrl();
    }

    @Nonnull
    public SabrMediaSegment fetchSegment(@Nonnull final SabrSegmentRequest request,
                                         @Nonnull final Localization localization)
            throws IOException, ExtractionException {
        final SabrMediaSegment cachedSegment = segmentCache.get(cacheKey(request));
        if (cachedSegment != null) {
            return cachedSegment;
        }
        failIfKnownOutOfBounds(request);

        boolean targetPrepared = maybePrepareForDistantMediaSegment(request);
        int policyOnlyResponses = 0;
        for (int attempts = 0; attempts < MAX_REQUESTS_PER_SEGMENT; attempts++) {
            final YoutubeSabrProbeResult result = fetchNextResponse(localization);
            final SabrDecodedResponse decoded = result.getDecodedResponse();
            validateResponseIntegrity(decoded, request);
            streamState.ingest(decoded);
            final List<SabrMediaSegment> segments = result.getSegments();
            for (final SabrMediaSegment segment : segments) {
                streamState.ingest(segment);
                segmentCache.put(cacheKey(segment), segment);
            }
            final SabrMediaSegment segment = segmentCache.get(cacheKey(request));
            if (segment != null) {
                return segment;
            }
            failIfKnownOutOfBounds(request);
            if (!targetPrepared) {
                targetPrepared = maybePrepareForDistantMediaSegment(request);
            }
            if (decoded.getSabrErrorDetails() != null) {
                throw new SabrProtocolException("SABR error while fetching "
                        + describeRequest(request) + ": " + decoded.getSabrErrorDetails().summarize());
            }
            if (decoded.isReloadRequested()) {
                if (maybeReload(localization)) {
                    continue;
                }
                throw new SabrProtocolException("SABR requested player reload while fetching "
                        + describeRequest(request) + " (reload budget spent): "
                        + decoded.summarizeNoMediaResponse());
            }
            if (decoded.isProtectionBoundaryNoMediaResponse()) {
                if (applyPoTokenForProtectedResponse()) {
                    if (decoded.getBackoffTimeMs() > 0) {
                        sleepBackoff(decoded.getBackoffTimeMs());
                    }
                    continue;
                }
                throw new SabrProtocolException("SABR protected no-media response while fetching "
                        + describeRequest(request) + ": " + decoded.summarizeNoMediaResponse());
            }
            if (decoded.isPolicyOnlyResponse()) {
                policyOnlyResponses++;
                if (policyOnlyResponses >= MAX_POLICY_ONLY_RESPONSES_PER_SEGMENT) {
                    throw new SabrProtocolException("SABR repeated policy-only responses while fetching "
                            + describeRequest(request) + ": " + decoded.summarizeNoMediaResponse());
                }
            } else if (!segments.isEmpty()) {
                policyOnlyResponses = 0;
            }
            if (decoded.getBackoffTimeMs() > 0) {
                sleepBackoff(decoded.getBackoffTimeMs());
            }
            if (streamState.isComplete()) {
                break;
            }
        }
        throw new SabrProtocolException("Requested SABR segment was not returned: itag="
                + request.getFormat().getItag()
                + (request.isInitializationSegment()
                ? ":init"
                : ":seq=" + request.getSequenceNumber()));
    }

    @Nonnull
    public YoutubeSabrProbeResult fetchNextResponse(@Nonnull final Localization localization)
            throws IOException, ExtractionException {
        final YoutubeSabrProbeResult result;
        if (requestNumber == 0) {
            result = YoutubeSabrProbe.probeFirstMediaResponse(info, audioFormat, videoFormat, streamState,
                    serverAbrStreamingUrl, localization);
        } else {
            result = YoutubeSabrProbe.probeFollowUpMediaResponse(info, audioFormat, videoFormat,
                    streamState, requestNumber, serverAbrStreamingUrl, localization);
        }
        if (result.getDecodedResponse().getRedirectUrl() != null
                && !result.getDecodedResponse().getRedirectUrl().isEmpty()) {
            redirectCount++;
            if (redirectCount > MAX_REDIRECTS_PER_SESSION) {
                throw new SabrProtocolException("SABR redirect limit exceeded: redirects="
                        + redirectCount);
            }
            serverAbrStreamingUrl = result.getDecodedResponse().getRedirectUrl();
        }
        requestNumber++;
        return result;
    }

    /**
     * Server asked us to reload the player response (its URLs / ustreamer config expired on a long
     * watch). Re-probe a fresh {@link YoutubeSabrInfo} and swap it in, keeping the stream state
     * (player time, buffered ranges, token) so the next request resumes in place instead of
     * restarting from 0. Bounded by {@link #MAX_RELOADS_PER_SESSION}.
     *
     * @return true if a fresh info was applied and the caller should retry, false if the reload
     *     budget is spent or the re-probe came back unusable.
     */
    private boolean maybeReload(@Nonnull final Localization localization)
            throws IOException, ExtractionException {
        if (reloads >= MAX_RELOADS_PER_SESSION) {
            return false;
        }
        reloads++;
        final ContentCountry contentCountry = localization.getCountryCode().isEmpty()
                ? ContentCountry.DEFAULT
                : new ContentCountry(localization.getCountryCode());
        final YoutubeSabrInfo fresh = YoutubeSabrProbe.fetchSabrInfo(
                info.getVideoId(), info.getProfile(), localization, contentCountry);
        if (fresh.getServerAbrStreamingUrl() == null || fresh.getServerAbrStreamingUrl().isEmpty()) {
            return false;
        }
        info = fresh;
        serverAbrStreamingUrl = fresh.getServerAbrStreamingUrl();
        redirectCount = 0;
        // keep requestNumber > 0 so the next request is a follow-up carrying the current player time
        // and buffered ranges: the new streaming URL resumes in place, not from the start.
        return true;
    }

    /**
     * Server-driven advance: issue one request with the current state and ingest whatever the
     * server returns (segments for either or both formats), instead of demanding one specific
     * per-track segment. A single consumer pumping this keeps audio and video coherent and lets the
     * server feed the track that is behind the play head, so neither track starves the other.
     */
    @Nonnull
    public List<SabrMediaSegment> pumpOnce(@Nonnull final Localization localization)
            throws IOException, ExtractionException {
        final YoutubeSabrProbeResult result = fetchNextResponse(localization);
        final SabrDecodedResponse decoded = result.getDecodedResponse();
        final List<String> integrityIssues = decoded.getIntegrityIssues();
        if (!integrityIssues.isEmpty()) {
            throw new SabrProtocolException("SABR media integrity issue: " + integrityIssues);
        }
        streamState.ingest(decoded);
        final List<SabrMediaSegment> segments = result.getSegments();
        for (final SabrMediaSegment segment : segments) {
            streamState.ingest(segment);
            final String key = cacheKey(segment);
            final SabrMediaSegment prev = segmentCache.put(key, segment);
            if (prev == null && !segment.getHeader().isInitSegment()) {
                cacheOrder.addLast(key);
                cachedBytes += segment.getLength();
            }
        }
        evictCacheIfNeeded();
        if (decoded.getSabrErrorDetails() != null) {
            throw new SabrProtocolException("SABR error: "
                    + decoded.getSabrErrorDetails().summarize());
        }
        if (decoded.isReloadRequested()) {
            if (maybeReload(localization)) {
                // fresh session applied; this round yields no media, the pump will call us again
                return Collections.emptyList();
            }
            throw new SabrProtocolException("SABR requested player reload (reload budget spent): "
                    + decoded.summarizeNoMediaResponse());
        }
        if (decoded.isProtectionBoundaryNoMediaResponse()) {
            // Mint / re-mint the token as soon as SABR reaches the protection boundary. Do not throw
            // on a single no-media round: the server usually clears it next round. The pump keeps
            // trying; the stall watchdog is the real give-up.
            applyPoTokenForProtectedResponse();
        }
        if (!segments.isEmpty()) {
            // A media-bearing response means the current token works and CDN hops are normal: clear
            // the cumulative redirect and token-refresh budgets so a long session isn't capped.
            redirectCount = 0;
            poTokenRefreshes = 0;
        }
        if (decoded.getBackoffTimeMs() > 0) {
            sleepBackoff(decoded.getBackoffTimeMs());
        }
        return segments;
    }

    /** Drop the oldest cached media segments (furthest behind the play head) to bound memory. */
    /** Fed by the pump every round: the real player position, used to evict only played segments. */
    public void setPlayHeadMs(final long ms) {
        this.playHeadMs = ms;
    }

    /** Total cached media bytes. The pump throttles on this so high-bitrate (4K) can't OOM the heap. */
    public long getCachedBytes() {
        return cachedBytes;
    }

    /**
     * Drop cached media bytes when the owning media period is released. Init segments are cheap to
     * refetch and keeping old period caches alive during rapid video switches can fill the app heap.
     */
    public void clearCache() {
        segmentCache.clear();
        cacheOrder.clear();
        cachedBytes = 0;
    }

    /**
     * Free already-played segments. The pump calls this every round (not just on fetch): when it is
     * byte-throttled it skips {@link #pumpOnce}, so eviction had to run here too or the cache stayed
     * full forever and throttling never released -> playback froze.
     */
    public void evictPlayed() {
        evictCacheIfNeeded();
    }

    private void evictCacheIfNeeded() {
        while (cachedBytes > MAX_CACHE_BYTES && cacheOrder.size() > MIN_CACHED_SEGMENTS) {
            final String oldKey = cacheOrder.peekFirst();
            if (oldKey == null) {
                break;
            }
            final SabrMediaSegment old = segmentCache.get(oldKey);
            // Never evict a segment the play head hasn't passed yet: dropping it (FIFO did) starves
            // the reader, which is sitting exactly on the oldest seq. Stop here and let the cache ride
            // over budget; the pump's read-ahead cushion bounds how far we get.
            if (old != null) {
                final long endMs = old.getHeader().getStartMs() + old.getHeader().getDurationMs();
                if (endMs > playHeadMs - EVICT_BEHIND_MS) {
                    break;
                }
            }
            cacheOrder.pollFirst();
            final SabrMediaSegment removed = segmentCache.remove(oldKey);
            if (removed != null) {
                cachedBytes -= removed.getLength();
            }
        }
    }

    /**
     * Collapse the cache to a single {@link #SEEK_KEEP_WINDOW_MS} window around a seek target: drop
     * MEDIA segments ending before {@code fromMs - window} or starting after {@code fromMs + window}.
     * Used by both seek directions so the cache can't hold two disjoint spans over the byte cap (the
     * 4K OOM root cause). Unlike {@link #evictCacheIfNeeded} this does NOT depend on the reader
     * position, so it works even when a track is blocked on the (uncached) seek target, which is what
     * deadlocked a forward seek (old span never freed -> heap full -> pump can't fetch the target ->
     * track stays blocked). The pump re-fetches contiguously, so dropped segments are pure waste.
     * Runs on the pump thread (same as cache mutation), so no extra locking is needed.
     */
    private void evictOutsideSeekWindow(final long fromMs) {
        final long lowMs = fromMs - SEEK_KEEP_WINDOW_MS;
        final long highMs = fromMs + SEEK_KEEP_WINDOW_MS;
        final Iterator<String> it = cacheOrder.iterator();
        while (it.hasNext()) {
            final String key = it.next();
            final SabrMediaSegment seg = segmentCache.get(key);
            if (seg == null) {
                continue;
            }
            final long startMs = seg.getHeader().getStartMs();
            final long endMs = startMs + seg.getHeader().getDurationMs();
            if (endMs < lowMs || startMs > highMs) {
                it.remove();
                segmentCache.remove(key);
                cachedBytes -= seg.getLength();
            }
        }
    }

    @Nullable
    public SabrMediaSegment getCachedSegment(@Nonnull final SabrSegmentRequest request) {
        return segmentCache.get(cacheKey(request));
    }

    public void discardCachedSegment(@Nonnull final SabrSegmentRequest request) {
        final String key = cacheKey(request);
        final SabrMediaSegment removed = segmentCache.remove(key);
        if (removed != null && !removed.getHeader().isInitSegment()) {
            cacheOrder.remove(key);
            cachedBytes = Math.max(0, cachedBytes - removed.getLength());
        }
    }


    /** True once the requested media segment is known to be past the last segment of the stream. */
    public boolean isBeyondEnd(@Nonnull final SabrSegmentRequest request) {
        if (request.isInitializationSegment()) {
            return false;
        }
        final long endSegment = streamState.getEndSegment(request.getFormat());
        return endSegment > 0 && request.getSequenceNumber() > endSegment;
    }

    public boolean isComplete() {
        return streamState.isComplete();
    }

    /** True once the server has reported this is a live stream (foundation for live support). */
    public boolean isLive() {
        return streamState.isLive();
    }

    /** Latest segment the live edge has reached, or -1 if unknown / not live. */
    public long getLiveHeadSequenceNumber() {
        return streamState.getLiveHeadSequenceNumber();
    }

    /**
     * True when playback has caught up to the live head: a live-aware pump should wait for the head
     * to advance rather than treating an empty response as the end. Always false for VOD.
     */
    public boolean isAtLiveEdge() {
        return streamState.isAtLiveEdge(audioFormat, videoFormat);
    }

    @Nonnull
    public YoutubeSabrStreamState getStreamState() {
        return streamState;
    }

    public int getRequestNumber() {
        return requestNumber;
    }

    public void prepareForMediaSegment(@Nonnull final SabrSegmentRequest request) {
        if (request.isInitializationSegment()) {
            return;
        }
        final YoutubeSabrFormat targetFormat = request.getFormat();
        final YoutubeSabrFormat companionFormat = getCompanionFormat(targetFormat);
        final long targetStartMs = streamState.getSegmentStartMs(targetFormat,
                request.getSequenceNumber());
        streamState.assumeBufferedUntil(targetFormat, request.getSequenceNumber() - 1);
        streamState.assumeBufferedUntil(companionFormat,
                streamState.getSegmentNumberAtOrAfterTimeMs(companionFormat, targetStartMs));
        streamState.setPlayerTimeMs(targetStartMs);
    }

    /**
     * Backward seek onto an already-buffered-past segment: like {@link #prepareForMediaSegment} but
     * SHRINKS the buffered head back to the target so the server re-sends it. assumeBufferedUntil
     * only extends, so prepareForMediaSegment can't rewind (the request comes back empty).
     */
    public void prepareForRewind(@Nonnull final SabrSegmentRequest request) {
        if (request.isInitializationSegment()) {
            return;
        }
        final YoutubeSabrFormat targetFormat = request.getFormat();
        final YoutubeSabrFormat companionFormat = getCompanionFormat(targetFormat);
        final long targetStartMs = streamState.getSegmentStartMs(targetFormat,
                request.getSequenceNumber());
        streamState.rewindBufferedTo(targetFormat, request.getSequenceNumber());
        streamState.rewindBufferedTo(companionFormat,
                streamState.getSegmentNumberAtOrAfterTimeMs(companionFormat, targetStartMs));
        streamState.setPlayerTimeMs(targetStartMs);
        // Discard the now-disconnected forward span (old play position) so the cache doesn't hold two
        // disjoint spans over the byte cap -> OOM at 4K. The pump re-fetches forward from the target.
        evictOutsideSeekWindow(targetStartMs);
    }

    /**
     * Forward jump (cold seek far past the buffered edge): the opposite of
     * {@link #prepareForRewind}. {@link #prepareForMediaSegment} only extends maxSegment, so the
     * reported range end (the contiguous edge) stayed behind and the next rounds kept filling the
     * skipped span (ping-pong + duplicate re-sends). This moves the buffered head onto the target
     * so the server streams from there and the edge-driven pacing follows naturally.
     */
    public void prepareForForwardJump(@Nonnull final SabrSegmentRequest request) {
        if (request.isInitializationSegment()) {
            return;
        }
        final YoutubeSabrFormat targetFormat = request.getFormat();
        final YoutubeSabrFormat companionFormat = getCompanionFormat(targetFormat);
        final long targetStartMs = streamState.getSegmentStartMs(targetFormat,
                request.getSequenceNumber());
        streamState.jumpBufferedTo(targetFormat, request.getSequenceNumber());
        streamState.jumpBufferedTo(companionFormat,
                streamState.getSegmentNumberAtOrAfterTimeMs(companionFormat, targetStartMs));
        streamState.setPlayerTimeMs(targetStartMs);
        // Drop the old span behind the jump target right away (don't wait for the reader to advance:
        // a track blocked on the uncached target keeps reader_tail stale, so play-head eviction never
        // runs and the heap fills -> stuck buffering / OOM at 4K). The pump fetches from the target.
        evictOutsideSeekWindow(targetStartMs);
    }

    private void failIfKnownOutOfBounds(@Nonnull final SabrSegmentRequest request)
            throws SabrProtocolException {
        if (request.isInitializationSegment()) {
            return;
        }
        final long endSegment = streamState.getEndSegment(request.getFormat());
        if (endSegment > 0 && request.getSequenceNumber() > endSegment) {
            throw new SabrProtocolException("Requested SABR segment is beyond end: "
                    + describeRequest(request) + ", endSegment=" + endSegment);
        }
    }

    private void validateResponseIntegrity(@Nonnull final SabrDecodedResponse decoded,
                                           @Nonnull final SabrSegmentRequest request)
            throws SabrProtocolException {
        final List<String> integrityIssues = decoded.getIntegrityIssues();
        if (!integrityIssues.isEmpty()) {
            throw new SabrProtocolException("SABR media integrity issue while fetching "
                    + describeRequest(request) + ": " + integrityIssues);
        }
    }

    private boolean maybePrepareForDistantMediaSegment(
            @Nonnull final SabrSegmentRequest request) {
        if (request.isInitializationSegment() || requestNumber == 0) {
            return false;
        }
        final YoutubeSabrFormat format = request.getFormat();
        if (streamState.getEndSegment(format) <= 0) {
            return false;
        }
        if (request.getSequenceNumber() <= streamState.getMaxSegment(format) + 1) {
            return false;
        }
        prepareForMediaSegment(request);
        return true;
    }

    private static void sleepBackoff(final int backoffTimeMs) throws SabrProtocolException {
        // Clamp to [0, MAX_BACKOFF_MS]: a negative (overflowed varint) must not skip the wait, and
        // a huge server backoff must not be honoured verbatim (would stall playback for minutes).
        final long ms = Math.min(Math.max(0, backoffTimeMs), MAX_BACKOFF_MS);
        if (ms == 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (final InterruptedException e) {
            // a random interrupt during a backoff must NOT kill the session, that drags audio AND
            // video down with it in one shot. swallow it and carry on; the loop figures out by
            // itself if playback is actually dead.
        }
    }

    private boolean maybeApplyPoToken(final boolean forceRefresh)
            throws IOException, ExtractionException {
        if (poTokenProvider == null) {
            return false;
        }
        final byte[] current = streamState.getRawPoToken();
        if (current != null && current.length > 0 && !forceRefresh) {
            return false;
        }
        final byte[] poToken = poTokenProvider.getPoToken(info, streamState, forceRefresh);
        if (poToken != null && poToken.length > 0 && !Arrays.equals(poToken, current)) {
            streamState.setPoToken(poToken);
            return true;
        }
        return false;
    }

    /**
     * Handle a status=3 / no-media response: mint a token, or force a bounded re-mint if we already
     * have one but the server still rejects it (expired mid-playback). Returns false if none could
     * be applied (caller treats that as fatal).
     */
    private boolean applyPoTokenForProtectedResponse() throws IOException, ExtractionException {
        if (maybeApplyPoToken(false)) {
            return true;
        }
        if (poTokenRefreshes < MAX_PO_TOKEN_REFRESHES) {
            // Count the attempt regardless of outcome: a force-refresh triggers a ~45s WebView
            // mint, so we bound them even when the server keeps returning the same rejected token.
            poTokenRefreshes++;
            return maybeApplyPoToken(true);
        }
        return false;
    }

    @Nonnull
    private static String describeRequest(@Nonnull final SabrSegmentRequest request) {
        return "itag=" + request.getFormat().getItag()
                + (request.isInitializationSegment()
                ? ":init"
                : ":seq=" + request.getSequenceNumber());
    }

    @Nonnull
    private static String cacheKey(@Nonnull final SabrSegmentRequest request) {
        return request.getFormat().getItag() + ":"
                + (request.isInitializationSegment()
                ? "init"
                : request.getSequenceNumber());
    }

    @Nonnull
    private static String cacheKey(@Nonnull final SabrMediaSegment segment) {
        final SabrMediaHeader header = segment.getHeader();
        return header.getItag() + ":"
                + (header.isInitSegment() ? "init" : header.getSequenceNumber());
    }

    @Nonnull
    private YoutubeSabrFormat getCompanionFormat(@Nonnull final YoutubeSabrFormat targetFormat) {
        if (targetFormat.getItag() == audioFormat.getItag()) {
            return videoFormat;
        }
        if (targetFormat.getItag() == videoFormat.getItag()) {
            return audioFormat;
        }
        throw new IllegalArgumentException("Unknown SABR itag: " + targetFormat.getItag());
    }
}
