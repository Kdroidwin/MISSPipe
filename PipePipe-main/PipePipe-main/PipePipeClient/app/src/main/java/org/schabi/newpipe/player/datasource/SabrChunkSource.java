package org.schabi.newpipe.player.datasource;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.datasource.DataSpec;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.source.chunk.BundledChunkExtractor;
import androidx.media3.exoplayer.source.chunk.Chunk;
import androidx.media3.exoplayer.source.chunk.ChunkExtractor;
import androidx.media3.exoplayer.source.chunk.ChunkHolder;
import androidx.media3.exoplayer.source.chunk.ChunkSource;
import androidx.media3.exoplayer.source.chunk.ContainerMediaChunk;
import androidx.media3.exoplayer.source.chunk.InitializationChunk;
import androidx.media3.exoplayer.source.chunk.MediaChunk;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.mkv.MatroskaExtractor;
import androidx.media3.extractor.mp4.FragmentedMp4Extractor;
import androidx.media3.extractor.text.SubtitleParser;

import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat;

import java.io.IOException;
import java.util.List;

/**
 * Tier-2: feeds the media3 chunk framework one SABR segment per chunk. Because the framework drives
 * loading by chunk INDEX (mapped from time), seeking is time-based and real, unlike the v1 byte
 * stream that could not land a seek. One {@link FragmentedMp4Extractor} is shared per track via a
 * {@link BundledChunkExtractor}; the init segment is loaded once as an {@link InitializationChunk},
 * then each media segment is a {@link ContainerMediaChunk}.
 */
final class SabrChunkSource implements ChunkSource {
    private static final String TAG = "SabrChunkSource";

    private final SabrSessionStore.Holder holder;
    private final YoutubeSabrFormat format;
    private final Format trackFormat;
    private final int trackType;
    private final Localization localization;
    private final ChunkExtractor extractor;

    @Nullable
    private IOException fatalError;

    SabrChunkSource(final SabrSessionStore.Holder holder,
                    final YoutubeSabrFormat format,
                    final Format trackFormat,
                    final int trackType,
                    final Localization localization) {
        this.holder = holder;
        this.format = format;
        this.trackFormat = trackFormat;
        this.trackType = trackType;
        this.localization = localization;
        final String mime = format.getMimeType();
        final Extractor extractorImpl = mime != null && mime.contains("webm")
                ? new MatroskaExtractor(SubtitleParser.Factory.UNSUPPORTED)
                : new FragmentedMp4Extractor(SubtitleParser.Factory.UNSUPPORTED);
        this.extractor = new BundledChunkExtractor(extractorImpl, trackType, trackFormat);
    }

    @Override
    public long getAdjustedSeekPositionUs(final long positionUs, final SeekParameters seekParameters) {
        return positionUs;
    }

    @Override
    public void maybeThrowError() throws IOException {
        if (fatalError != null) {
            throw fatalError;
        }
    }

    @Override
    public int getPreferredQueueSize(final long playbackPositionUs,
                                     final List<? extends MediaChunk> queue) {
        return queue.size();
    }

    @Override
    public boolean shouldCancelLoad(final long playbackPositionUs, final Chunk loadingChunk,
                                    final List<? extends MediaChunk> queue) {
        return false;
    }

    @Override
    public void getNextChunk(final LoadingInfo loadingInfo, final long loadPositionUs,
                             final List<? extends MediaChunk> queue, final ChunkHolder out) {
        if (extractor.getSampleFormats() == null) {
            Log.d(TAG, "nextInit video=" + holder.videoId
                    + " itag=" + format.getItag());
            out.chunk = new InitializationChunk(
                    new SabrSegmentDataSource(holder, format, localization,
                            /* prependInit= */ false),
                    new DataSpec(Uri.parse("sabrseg://" + format.getItag() + "/init")),
                    trackFormat, C.SELECTION_REASON_UNKNOWN, null, extractor);
            return;
        }
        final int nextSeq;
        if (queue.isEmpty()) {
            nextSeq = holder.session.getStreamState()
                    .getSegmentNumberAtOrAfterTimeMs(format, loadPositionUs / 1000);
        } else {
            nextSeq = (int) (queue.get(queue.size() - 1).getNextChunkIndex());
        }
        final long endSeq = holder.session.getStreamState().getEndSegment(format);
        if (endSeq > 0 && nextSeq > endSeq) {
            Log.d(TAG, "endOfStream video=" + holder.videoId
                    + " itag=" + format.getItag() + " seq=" + nextSeq);
            out.endOfStream = true;
            return;
        }
        Log.d(TAG, "nextChunk video=" + holder.videoId
                + " itag=" + format.getItag()
                + " seq=" + nextSeq
                + " loadPositionUs=" + loadPositionUs
                + " queue=" + queue.size());
        out.chunk = newMediaChunk(nextSeq);
    }

    private Chunk newMediaChunk(final int seq) {
        final long startMs = holder.session.getStreamState().getSegmentStartMs(format, seq);
        final long endMs = holder.session.getStreamState().getSegmentEndMs(format, seq);
        final long startUs = Math.max(0, startMs) * 1000;
        final long endUs = (endMs > 0 ? endMs : startMs) * 1000;
        final DataSpec spec = new DataSpec(Uri.parse("sabrseg://" + format.getItag() + "/" + seq));
        return new ContainerMediaChunk(
                new SabrSegmentDataSource(holder, format, localization, /* prependInit= */ false),
                spec, trackFormat, C.SELECTION_REASON_UNKNOWN, null,
                startUs, endUs, /* clippedStartTimeUs= */ startUs,
                // No end clip, on purpose. The first chunk's declared end is basically a rumor: we
                // compute it before the init metadata shows up to admit audio segments are ~10s, not 5s.
                // Clip to that rumor and you toss the real 5-10s of audio out the window, so the player
                // faceplants from 0:04 straight to 0:10. A SABR chunk is one whole segment with honest,
                // non-overlapping absolute timestamps, so we shut up and let the container have the last word.
                /* clippedEndTimeUs= */ C.TIME_UNSET,
                /* chunkIndex= */ seq, /* chunkCount= */ 1, /* sampleOffsetUs= */ 0L,
                extractor);
    }

    @Override
    public void onChunkLoadCompleted(final Chunk chunk) {
    }

    @Override
    public boolean onChunkLoadError(final Chunk chunk, final boolean cancelable,
                                    final LoadErrorHandlingPolicy.LoadErrorInfo loadErrorInfo,
                                    final LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
        // Let the framework apply its retry/backoff policy.
        return false;
    }

    @Override
    public void release() {
    }
}
