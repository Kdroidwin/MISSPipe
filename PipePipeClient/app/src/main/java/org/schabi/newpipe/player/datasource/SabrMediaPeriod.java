package org.schabi.newpipe.player.datasource;

import android.util.Log;

import androidx.annotation.Nullable;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.TrackGroup;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.drm.DrmSessionEventListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSourceEventListener;
import androidx.media3.exoplayer.source.SampleStream;
import androidx.media3.exoplayer.source.SequenceableLoader;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.source.chunk.ChunkSampleStream;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;

import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tier-2 {@link MediaPeriod} for SABR: exposes the audio and video tracks and backs each selected
 * one with a {@link ChunkSampleStream} over a {@link SabrChunkSource}. Seeking is handled by the
 * chunk streams (time -> chunk index), so it actually lands, unlike the v1 byte-stream source.
 */
final class SabrMediaPeriod implements MediaPeriod,
        SequenceableLoader.Callback<ChunkSampleStream<SabrChunkSource>> {
    private static final String TAG = "SabrMediaPeriod";

    private final SabrSessionStore.Holder holder;
    private final Localization localization;
    private final long durationUs;
    private final Allocator allocator;
    private final DrmSessionManager drmSessionManager;
    private final DrmSessionEventListener.EventDispatcher drmEventDispatcher;
    private final MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher;
    private final LoadErrorHandlingPolicy loadErrorHandlingPolicy =
            new DefaultLoadErrorHandlingPolicy();

    private final TrackGroupArray trackGroups;
    private final YoutubeSabrFormat[] sabrFormats;
    private final int[] trackTypes;

    private final List<ChunkSampleStream<SabrChunkSource>> streams = new ArrayList<>();
    private SequenceableLoader compositeLoader = new EmptyLoader();
    @Nullable
    private MediaPeriod.Callback callback;

    SabrMediaPeriod(final SabrSessionStore.Holder holder,
                    final Format audioFormat,
                    final Format videoFormat,
                    final long durationUs,
                    final Allocator allocator,
                    final DrmSessionManager drmSessionManager,
                    final DrmSessionEventListener.EventDispatcher drmEventDispatcher,
                    final MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
                    final Localization localization) {
        this.holder = holder;
        this.localization = localization;
        this.durationUs = durationUs;
        this.allocator = allocator;
        this.drmSessionManager = drmSessionManager;
        this.drmEventDispatcher = drmEventDispatcher;
        this.mediaSourceEventDispatcher = mediaSourceEventDispatcher;
        this.sabrFormats = new YoutubeSabrFormat[]{holder.videoFormat, holder.audioFormat};
        this.trackTypes = new int[]{C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO};
        this.trackGroups = new TrackGroupArray(
                new TrackGroup("sabr-video", videoFormat),
                new TrackGroup("sabr-audio", audioFormat));
        Log.d(TAG, "create period video=" + holder.videoId
                + " videoItag=" + holder.videoFormat.getItag()
                + " audioItag=" + holder.audioFormat.getItag());
    }

    @Override
    public void prepare(final MediaPeriod.Callback cb, final long positionUs) {
        this.callback = cb;
        Log.d(TAG, "prepare video=" + holder.videoId + " positionUs=" + positionUs);
        cb.onPrepared(this);
    }

    @Override
    public void maybeThrowPrepareError() {
    }

    @Override
    public TrackGroupArray getTrackGroups() {
        return trackGroups;
    }

    @Override
    public long selectTracks(final ExoTrackSelection[] selections, final boolean[] mayRetainFlags,
                             final SampleStream[] outStreams, final boolean[] streamResetFlags,
                             final long positionUs) {
        Log.d(TAG, "selectTracks video=" + holder.videoId
                + " selections=" + selections.length + " positionUs=" + positionUs);
        // Release streams no longer wanted; create streams for newly selected tracks.
        for (int i = 0; i < selections.length; i++) {
            if (outStreams[i] instanceof ChunkSampleStream && (selections[i] == null
                    || !mayRetainFlags[i])) {
                @SuppressWarnings("unchecked")
                final ChunkSampleStream<SabrChunkSource> s =
                        (ChunkSampleStream<SabrChunkSource>) outStreams[i];
                streams.remove(s);
                s.release();
                outStreams[i] = null;
            }
            if (outStreams[i] == null && selections[i] != null) {
                final ChunkSampleStream<SabrChunkSource> s = buildStream(selections[i], positionUs);
                streams.add(s);
                outStreams[i] = s;
                streamResetFlags[i] = true;
            }
        }
        updateActiveTracks(selections);
        rebuildCompositeLoader();
        return positionUs;
    }

    private void updateActiveTracks(final ExoTrackSelection[] selections) {
        boolean videoActive = false;
        boolean audioActive = false;
        for (final ExoTrackSelection selection : selections) {
            if (selection == null) {
                continue;
            }
            final int groupIndex = trackGroups.indexOf(selection.getTrackGroup());
            if (groupIndex < 0) {
                continue;
            }
            if (trackTypes[groupIndex] == C.TRACK_TYPE_VIDEO) {
                videoActive = true;
            } else if (trackTypes[groupIndex] == C.TRACK_TYPE_AUDIO) {
                audioActive = true;
            }
        }
        holder.setActiveTracks(videoActive, audioActive);
        Log.d(TAG, "activeTracks video=" + holder.videoId
                + " video=" + videoActive + " audio=" + audioActive);
    }

    private ChunkSampleStream<SabrChunkSource> buildStream(final ExoTrackSelection selection,
                                                           final long positionUs) {
        final TrackGroup group = selection.getTrackGroup();
        final int groupIndex = trackGroups.indexOf(group);
        final Format trackFormat = group.getFormat(0);
        Log.d(TAG, "buildStream video=" + holder.videoId
                + " groupIndex=" + groupIndex
                + " trackType=" + trackTypes[groupIndex]
                + " itag=" + sabrFormats[groupIndex].getItag()
                + " positionUs=" + positionUs);
        final SabrChunkSource chunkSource = new SabrChunkSource(holder, sabrFormats[groupIndex],
                trackFormat, trackTypes[groupIndex], localization);
        // Last 3 args are new in media3 1.10 (handleInitialDiscontinuity, firstChunkStartTimeUs,
        // downloadExecutor); false / TIME_UNSET / null reproduces the pre-1.10 behaviour.
        return new ChunkSampleStream<>(trackTypes[groupIndex], null, null, chunkSource, this,
                allocator, positionUs, drmSessionManager, drmEventDispatcher,
                loadErrorHandlingPolicy, mediaSourceEventDispatcher,
                false, C.TIME_UNSET, null);
    }

    private void rebuildCompositeLoader() {
        // Simplest correct loader: drive each stream; report the min buffered / max load position.
        compositeLoader = new SequenceableLoader() {
            @Override
            public long getBufferedPositionUs() {
                // Skip tracks already buffered to the end (END_OF_SOURCE = Long.MIN_VALUE), else a
                // finished shorter track (audio) would collapse the min and make media3 think the
                // whole period is buffered to the end, starving the still-loading video near the end.
                long min = Long.MAX_VALUE;
                for (final ChunkSampleStream<SabrChunkSource> s : streams) {
                    final long b = s.getBufferedPositionUs();
                    if (b != C.TIME_END_OF_SOURCE) {
                        min = Math.min(min, b);
                    }
                }
                return min == Long.MAX_VALUE ? C.TIME_END_OF_SOURCE : min;
            }

            @Override
            public long getNextLoadPositionUs() {
                long min = Long.MAX_VALUE;
                for (final ChunkSampleStream<SabrChunkSource> s : streams) {
                    final long n = s.getNextLoadPositionUs();
                    if (n != C.TIME_END_OF_SOURCE) {
                        min = Math.min(min, n);
                    }
                }
                return min == Long.MAX_VALUE ? C.TIME_END_OF_SOURCE : min;
            }

            @Override
            public boolean continueLoading(final LoadingInfo loadingInfo) {
                boolean any = false;
                for (final ChunkSampleStream<SabrChunkSource> s : streams) {
                    any |= s.continueLoading(loadingInfo);
                }
                return any;
            }

            @Override
            public boolean isLoading() {
                for (final ChunkSampleStream<SabrChunkSource> s : streams) {
                    if (s.isLoading()) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void reevaluateBuffer(final long positionUs) {
                for (final ChunkSampleStream<SabrChunkSource> s : streams) {
                    s.reevaluateBuffer(positionUs);
                }
            }
        };
    }

    @Override
    public void discardBuffer(final long positionUs, final boolean toKeyframe) {
        for (final ChunkSampleStream<SabrChunkSource> s : streams) {
            s.discardBuffer(positionUs, toKeyframe);
        }
    }

    @Override
    public long readDiscontinuity() {
        return C.TIME_UNSET;
    }

    @Override
    public long seekToUs(final long positionUs) {
        for (final ChunkSampleStream<SabrChunkSource> s : streams) {
            s.seekToUs(positionUs);
        }
        return positionUs;
    }

    @Override
    public long getAdjustedSeekPositionUs(final long positionUs, final SeekParameters params) {
        for (final ChunkSampleStream<SabrChunkSource> s : streams) {
            return s.getAdjustedSeekPositionUs(positionUs, params);
        }
        return positionUs;
    }

    @Override
    public long getBufferedPositionUs() {
        return compositeLoader.getBufferedPositionUs();
    }

    @Override
    public long getNextLoadPositionUs() {
        return compositeLoader.getNextLoadPositionUs();
    }

    @Override
    public boolean continueLoading(final LoadingInfo loadingInfo) {
        return compositeLoader.continueLoading(loadingInfo);
    }

    @Override
    public boolean isLoading() {
        return compositeLoader.isLoading();
    }

    @Override
    public void reevaluateBuffer(final long positionUs) {
        compositeLoader.reevaluateBuffer(positionUs);
    }

    @Override
    public void onContinueLoadingRequested(final ChunkSampleStream<SabrChunkSource> source) {
        if (callback != null) {
            callback.onContinueLoadingRequested(this);
        }
    }

    void release() {
        Log.d(TAG, "release period video=" + holder.videoId);
        for (final ChunkSampleStream<SabrChunkSource> s : streams) {
            s.release();
        }
        streams.clear();
        holder.setActiveTracks(false, false);
    }

    /** No-op loader used before any track is selected. */
    private static final class EmptyLoader implements SequenceableLoader {
        @Override
        public long getBufferedPositionUs() {
            return C.TIME_END_OF_SOURCE;
        }

        @Override
        public long getNextLoadPositionUs() {
            return C.TIME_END_OF_SOURCE;
        }

        @Override
        public boolean continueLoading(final LoadingInfo loadingInfo) {
            return false;
        }

        @Override
        public boolean isLoading() {
            return false;
        }

        @Override
        public void reevaluateBuffer(final long positionUs) {
        }
    }
}
