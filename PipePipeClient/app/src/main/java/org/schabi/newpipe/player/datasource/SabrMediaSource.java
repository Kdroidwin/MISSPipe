package org.schabi.newpipe.player.datasource;

import android.util.Log;

import androidx.annotation.Nullable;

import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.source.BaseMediaSource;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.SinglePeriodTimeline;
import androidx.media3.exoplayer.upstream.Allocator;

import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat;

/**
 * Tier-2 {@link androidx.media3.exoplayer.source.MediaSource} for SABR. Unlike the v1
 * ProgressiveMediaSource over a byte stream (which could not seek), this exposes a seekable
 * single-period timeline and a {@link SabrMediaPeriod} backed by the chunk framework, so seeking is
 * time-based and lands correctly. The session is created by the resolver and handed in.
 */
public final class SabrMediaSource extends BaseMediaSource {
    private static final String TAG = "SabrMediaSource";

    private final MediaItem mediaItem;
    private final SabrSessionStore.Holder holder;
    private final Localization localization;
    private final Format audioFormat;
    private final Format videoFormat;
    private final long durationUs;
    private boolean released;

    public SabrMediaSource(final MediaItem mediaItem,
                           final SabrSessionStore.Holder holder,
                           final Localization localization) {
        this.mediaItem = mediaItem;
        this.holder = holder;
        this.localization = localization;
        this.holder.retainSource();
        Log.d(TAG, "create source video=" + holder.videoId);
        this.audioFormat = toMedia3Format(holder.audioFormat);
        this.videoFormat = toMedia3Format(holder.videoFormat);
        this.durationUs = Math.max(holder.audioFormat.getApproxDurationMs(),
                holder.videoFormat.getApproxDurationMs()) * 1000L;
    }

    @Override
    public MediaItem getMediaItem() {
        return mediaItem;
    }

    @Override
    protected void prepareSourceInternal(@Nullable final TransferListener mediaTransferListener) {
        refreshSourceInfo(new SinglePeriodTimeline(durationUs, /* isSeekable= */ true,
                /* isDynamic= */ false, /* useLiveConfiguration= */ false,
                /* manifest= */ null, mediaItem));
    }

    @Override
    public void maybeThrowSourceInfoRefreshError() {
    }

    @Override
    public MediaPeriod createPeriod(final MediaPeriodId id, final Allocator allocator,
                                    final long startPositionUs) {
        Log.d(TAG, "createPeriod video=" + holder.videoId + " startUs=" + startPositionUs);
        return new SabrMediaPeriod(holder, audioFormat, videoFormat, durationUs, allocator,
                DrmSessionManager.DRM_UNSUPPORTED, createDrmEventDispatcher(id),
                createEventDispatcher(id), localization);
    }

    @Override
    public void releasePeriod(final MediaPeriod mediaPeriod) {
        Log.d(TAG, "releasePeriod video=" + holder.videoId);
        ((SabrMediaPeriod) mediaPeriod).release();
    }

    @Override
    protected void releaseSourceInternal() {
        if (!released) {
            released = true;
            Log.d(TAG, "release source video=" + holder.videoId);
            holder.releaseSource();
        }
    }

    private static Format toMedia3Format(final YoutubeSabrFormat f) {
        final String mime = f.getMimeType();
        String container = mime;
        String codecs = null;
        final int sc = mime.indexOf(';');
        if (sc > 0) {
            container = mime.substring(0, sc).trim();
        }
        final int ci = mime.indexOf("codecs=");
        if (ci >= 0) {
            codecs = mime.substring(ci + "codecs=".length()).replace("\"", "").trim();
        }
        final Format.Builder b = new Format.Builder()
                .setId(String.valueOf(f.getItag()))
                .setContainerMimeType(container)
                .setCodecs(codecs)
                .setSampleMimeType(codecs != null ? MimeTypes.getMediaMimeType(codecs) : container)
                .setAverageBitrate(f.getBitrate());
        if (f.isVideo()) {
            b.setWidth(f.getWidth()).setHeight(f.getHeight());
        }
        return b.build();
    }
}
