package org.schabi.newpipe.player.helper;

import androidx.media3.common.C;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.upstream.DefaultAllocator;

public class LoadController extends DefaultLoadControl {

    public static final String TAG = "LoadController";

    // Keep the player's buffer target well BELOW the SABR pump's read-ahead cushion (50s). The player
    // reads ahead to fill maxBuffer (plus up to one whole segment, ~10s for audio), and if that runs
    // past what the pump has fed it blocks on a segment the pump won't fetch yet (pump paces off the
    // play head): cold start deadlocked, and the longer-segment track (audio) dropped out mid-play.
    // 20s target -> ~30s real read-ahead, comfortably inside the 50s cushion. Fine for DASH/HLS.
    private static final int MIN_BUFFER_MS = 12_000;
    private static final int MAX_BUFFER_MS = 20_000;
    private static final int BUFFER_FOR_PLAYBACK_MS = 2_000;
    private static final int BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 3_000;

    private boolean preloadingEnabled = true;

    public LoadController() {
        // media3 1.10 split every buffer param into a normal + a "ForLocalPlayback" variant; we use
        // the same value for both so behaviour is unchanged whether the source is local or remote.
        super(new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
                MIN_BUFFER_MS, MIN_BUFFER_MS,
                MAX_BUFFER_MS, MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS, BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS, BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                C.LENGTH_UNSET, // no byte cap: the SABR cache bounds memory, time bounds the player
                // MUST be true: with false, media3 prioritises its (huge, ~128MB) default byte target
                // and ignores maxBufferMs, reading ~50s ahead = right at the pump cushion, so it
                // starved at the edge. true makes maxBufferMs (time) the real limit.
                true, true,
                DEFAULT_BACK_BUFFER_DURATION_MS,
                DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME);
    }

    @Override
    public void onPrepared() {
        preloadingEnabled = true;
        super.onPrepared();
    }

    @Override
    public void onStopped() {
        preloadingEnabled = true;
        super.onStopped();
    }

    @Override
    public void onReleased() {
        preloadingEnabled = true;
        super.onReleased();
    }

    @Override
    public boolean shouldContinueLoading(final long playbackPositionUs,
                                         final long bufferedDurationUs,
                                         final float playbackSpeed) {
        if (!preloadingEnabled) {
            return false;
        }
        return super.shouldContinueLoading(
                playbackPositionUs, bufferedDurationUs, playbackSpeed);
    }

    public void disablePreloadingOfCurrentTrack() {
        preloadingEnabled = false;
    }
}
