package org.schabi.newpipe.player.resolver;

import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;
import static org.schabi.newpipe.player.helper.PlayerDataSource.LIVE_STREAM_EDGE_GAP_MILLIS;

import android.net.Uri;
import android.util.Log;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.DashManifestParser;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser;
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource;
import androidx.media3.exoplayer.smoothstreaming.manifest.SsManifest;
import androidx.media3.exoplayer.smoothstreaming.manifest.SsManifestParser;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.CreationException;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubePostLiveStreamDvrDashManifestCreator;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.helper.NonUriHlsPlaylistParserFactory;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.mediaitem.StreamInfoTag;
import org.schabi.newpipe.util.StreamTypeUtil;
import org.schabi.newpipe.App;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo;
import org.schabi.newpipe.player.datasource.SabrMediaSource;
import org.schabi.newpipe.player.datasource.SabrSessionStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public interface PlaybackResolver extends Resolver<StreamInfo, MediaSource> {
    String TAG = PlaybackResolver.class.getSimpleName();

    @Nullable
    static MediaSource maybeBuildLiveMediaSource(@NonNull final PlayerDataSource dataSource,
                                                 @NonNull final StreamInfo info) {
        final StreamType streamType = info.getStreamType();
        if (!StreamTypeUtil.isLiveStream(streamType)) {
            return null;
        }

        final StreamInfoTag tag = StreamInfoTag.of(info);
        // Prefer DASH over HLS because of an exoPlayer bug that causes the background player to
        // also fetch the video stream even if it is supposed to just fetch the audio stream.
        if (!info.getDashMpdUrl().isEmpty()) {
            return buildLiveMediaSource(
                    dataSource, info.getDashMpdUrl(), C.CONTENT_TYPE_DASH, tag);
        }
        if (!info.getHlsUrl().isEmpty()) {
            return buildLiveMediaSource(dataSource, info.getHlsUrl(), C.CONTENT_TYPE_HLS, tag);
        }

        return null;
    }

    @NonNull
    static MediaSource buildLiveMediaSource(@NonNull final PlayerDataSource dataSource,
                                            @NonNull final String sourceUrl,
                                            @C.ContentType final int type,
                                            @NonNull final MediaItemTag metadata) {
        final MediaSource.Factory factory;
        if(sourceUrl.contains("live.nicovideo.jp/watch")){
            factory = dataSource.getNicoLiveHlsMediaSourceFactory(sourceUrl);
            return factory.createMediaSource(
                    new MediaItem.Builder()
                            .setTag(metadata)
                            .setUri(Uri.parse(sourceUrl))
                            .setLiveConfiguration(
                                    new MediaItem.LiveConfiguration.Builder()
                                            .setTargetOffsetMs(LIVE_STREAM_EDGE_GAP_MILLIS)
                                            .build())
                            .build()
            );
        }
        switch (type) {
            case C.CONTENT_TYPE_SS:
                factory = dataSource.getLiveSsMediaSourceFactory();
                break;
            case C.CONTENT_TYPE_DASH:
                if (metadata.getServiceId() == ServiceList.YouTube.getServiceId()) {
                    factory = dataSource.getLiveYoutubeDashMediaSourceFactory();
                } else {
                    factory = dataSource.getLiveDashMediaSourceFactory();
                }
                break;
            case C.CONTENT_TYPE_HLS:
                factory = dataSource.getLiveHlsMediaSourceFactory();
                break;
            case C.CONTENT_TYPE_OTHER:
            case C.CONTENT_TYPE_RTSP:
            default:
                throw new IllegalStateException("Unsupported type: " + type);
        }

        return factory.createMediaSource(
                new MediaItem.Builder()
                        .setTag(metadata)
                        .setUri(Uri.parse(sourceUrl))
                        .setLiveConfiguration(
                                new MediaItem.LiveConfiguration.Builder()
                                        .setTargetOffsetMs(LIVE_STREAM_EDGE_GAP_MILLIS)
                                        .build())
                        .build());
    }

    @NonNull
    static MediaSource buildMediaSource(@NonNull final PlayerDataSource dataSource,
                                        @NonNull final Stream stream,
                                        @NonNull final StreamInfo streamInfo,
                                        @NonNull final String cacheKey,
                                        @NonNull final MediaItemTag metadata)
            throws IOException {
        StreamingService service = streamInfo.getService();
        if (ServiceList.YouTube.equals(service)) {
            return createYoutubeMediaSource(stream, streamInfo, dataSource, cacheKey, metadata);
        } else if (ServiceList.NicoNico.equals(service)) {
            return createNicoNicoMediaSource(stream, streamInfo, dataSource, cacheKey, metadata);
        } else if (ServiceList.BiliBili.equals(service)) {
            return createBiliBiliMediaSource(stream, streamInfo, dataSource, cacheKey, metadata);
        } else if (ServiceList.MissAV.equals(service)
                && stream.getDeliveryMethod() == DeliveryMethod.HLS) {
            return buildMissAvHlsMediaSource(dataSource, stream, streamInfo, cacheKey, metadata);
        } else if (ServiceList.MissAV.equals(service)
                && stream.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP
                && stream.getContent().contains("#85po=1")) {
            return buildEightyFivePoProgressiveMediaSource(dataSource, stream, cacheKey, metadata);
        } else if (ServiceList.KissJAV.equals(service)
                && stream.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP
                && stream.getContent().contains("#85po=1")) {
            return buildEightyFivePoProgressiveMediaSource(dataSource, stream, cacheKey, metadata);
        } else if (ServiceList.KissJAV.equals(service)
                && stream.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP) {
            return buildKissJavProgressiveMediaSource(dataSource, stream, cacheKey, metadata);
        } else if (ServiceList.EightyFivePo.equals(service)
                && stream.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP) {
            return buildEightyFivePoProgressiveMediaSource(dataSource, stream, cacheKey, metadata);
        } else if (ServiceList.Pornhub.equals(service)
                && stream.getDeliveryMethod() == DeliveryMethod.HLS) {
            return buildPornhubHlsMediaSource(dataSource, stream, cacheKey, metadata);
        } else if (ServiceList.Pornhub.equals(service)
                && stream.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP) {
            return buildPornhubProgressiveMediaSource(dataSource, stream, cacheKey, metadata);
        }

        final DeliveryMethod deliveryMethod = stream.getDeliveryMethod();
        switch (deliveryMethod) {
            case PROGRESSIVE_HTTP:
                return buildProgressiveMediaSource(dataSource, stream, cacheKey, metadata);
            case DASH:
                return buildDashMediaSource(dataSource, stream, cacheKey, metadata);
            case HLS:
                return buildHlsMediaSource(dataSource, stream, cacheKey, metadata);
            case SS:
                return buildSSMediaSource(dataSource, stream, cacheKey, metadata);
            // Torrent streams are not supported by ExoPlayer
            default:
                throw new IllegalArgumentException("Unsupported delivery type: " + deliveryMethod);
        }
    }

    @NonNull
    private static <T extends Stream> ProgressiveMediaSource buildKissJavProgressiveMediaSource(
            @NonNull final PlayerDataSource dataSource,
            @NonNull final T stream,
            @NonNull final String cacheKey,
            @NonNull final MediaItemTag metadata) throws IOException {
        final String url = stream.getContent();

        if (isNullOrEmpty(url)) {
            throw new IOException(
                    "Try to generate a KissJAV progressive media source from an empty URL");
        }
        return dataSource.getKissJavProgressiveMediaSourceFactory().createMediaSource(
                new MediaItem.Builder()
                        .setTag(metadata)
                        .setUri(Uri.parse(url.replace("#kissjav=1", "")))
                        .setCustomCacheKey(cacheKey)
                        .build());
    }

    @NonNull
    private static <T extends Stream> ProgressiveMediaSource buildEightyFivePoProgressiveMediaSource(
            @NonNull final PlayerDataSource dataSource,
            @NonNull final T stream,
            @NonNull final String cacheKey,
            @NonNull final MediaItemTag metadata) throws IOException {
        final String url = stream.getContent();

        if (isNullOrEmpty(url)) {
            throw new IOException(
                    "Try to generate an 85po progressive media source from an empty URL");
        }
        return dataSource.getEightyFivePoProgressiveMediaSourceFactory().createMediaSource(
                new MediaItem.Builder()
                        .setTag(metadata)
                        .setUri(Uri.parse(url.replace("#85po=1", "")))
                        .setCustomCacheKey(cacheKey)
                        .build());
    }

    @NonNull
    private static <T extends Stream> HlsMediaSource buildPornhubHlsMediaSource(
            @NonNull final PlayerDataSource dataSource,
            @NonNull final T stream,
            @NonNull final String cacheKey,
            @NonNull final MediaItemTag metadata) throws IOException {
        final String url = stream.getContent();

        if (isNullOrEmpty(url)) {
            throw new IOException("Try to generate a Pornhub HLS media source from an empty URL");
        }
        return dataSource.getPornhubHlsMediaSourceFactory().createMediaSource(
                new MediaItem.Builder()
                        .setTag(metadata)
                        .setUri(Uri.parse(url.replace("#pornhub=1", "")))
                        .setCustomCacheKey(cacheKey)
                        .build());
    }

    @NonNull
    private static <T extends Stream> ProgressiveMediaSource buildPornhubProgressiveMediaSource(
            @NonNull final PlayerDataSource dataSource,
            @NonNull final T stream,
            @NonNull final String cacheKey,
            @NonNull final MediaItemTag metadata) throws IOException {
        final String url = stream.getContent();

        if (isNullOrEmpty(url)) {
            throw new IOException(
                    "Try to generate a Pornhub progressive media source from an empty URL");
        }
        return dataSource.getPornhubProgressiveMediaSourceFactory().createMediaSource(
                new MediaItem.Builder()
                        .setTag(metadata)
                        .setUri(Uri.parse(url.replace("#pornhub=1", "")))
                        .setCustomCacheKey(cacheKey)
                        .build());
    }

    @NonNull
    private static <T extends Stream> ProgressiveMediaSource buildProgressiveMediaSource(
            @NonNull final PlayerDataSource dataSource,
            @NonNull final T stream,
            @NonNull final String cacheKey,
            @NonNull final MediaItemTag metadata) throws IOException {
        final String url = stream.getContent();

        if (isNullOrEmpty(url)) {
            throw new IOException(
                    "Try to generate a progressive media source from an empty string or from a "
                            + "null object");
        } else {
            return dataSource.getProgressiveMediaSourceFactory().createMediaSource(
                    new MediaItem.Builder()
                            .setTag(metadata)
                            .setUri(Uri.parse(url))
                            .setCustomCacheKey(cacheKey)
                            .build());
        }
    }

    @NonNull
    private static <T extends Stream> DashMediaSource buildDashMediaSource(
            @NonNull final PlayerDataSource dataSource,
            @NonNull final T stream,
            @NonNull final String cacheKey,
            @NonNull final MediaItemTag metadata) throws IOException {
        final boolean isUrlStream = stream.isUrl();
        if (isUrlStream && isNullOrEmpty(stream.getContent())) {
            throw new IOException("Try to generate a DASH media source from an empty string or "
                    + "from a null object");
        }

        if (isUrlStream) {
            return dataSource.getDashMediaSourceFactory().createMediaSource(
                    new MediaItem.Builder()
                            .setTag(metadata)
                            .setUri(Uri.parse(stream.getContent()))
                            .setCustomCacheKey(cacheKey)
                            .build());
        } else {
            String baseUrl = stream.getManifestUrl();
            if (baseUrl == null) {
                baseUrl = "";
            }

            final Uri uri = Uri.parse(baseUrl);

            return dataSource.getDashMediaSourceFactory().createMediaSource(
                    createDashManifest(stream.getContent(), stream),
                    new MediaItem.Builder()
                            .setTag(metadata)
                            .setUri(uri)
                            .setCustomCacheKey(cacheKey)
                            .build());
        }
    }

    @NonNull
    private static <T extends Stream> DashManifest createDashManifest(
            @NonNull final String manifestContent,
            @NonNull final T stream) throws IOException {
        try {
            final ByteArrayInputStream dashManifestInput = new ByteArrayInputStream(
                    manifestContent.getBytes(StandardCharsets.UTF_8));
            String baseUrl = stream.getManifestUrl();
            if (baseUrl == null) {
                baseUrl = "";
            }

            return new DashManifestParser().parse(Uri.parse(baseUrl), dashManifestInput);
        } catch (final IOException e) {
            throw new IOException("Error when parsing manual DASH manifest", e);
        }
    }

    @NonNull
    private static <T extends Stream> HlsMediaSource buildHlsMediaSource(
            @NonNull final PlayerDataSource dataSource,
            @NonNull final T stream,
            @NonNull final String cacheKey,
            @NonNull final MediaItemTag metadata) throws IOException {
        final boolean isUrlStream = stream.isUrl();
        if (isUrlStream && isNullOrEmpty(stream.getContent())) {
            throw new IOException("Try to generate an HLS media source from an empty string or "
                    + "from a null object");
        }

        if (isUrlStream) {
            return dataSource.getHlsMediaSourceFactory(null).createMediaSource(
                    new MediaItem.Builder()
                            .setTag(metadata)
                            .setUri(Uri.parse(stream.getContent()))
                            .setCustomCacheKey(cacheKey)
                            .build());
        } else {
            String baseUrl = stream.getManifestUrl();
            if (baseUrl == null) {
                baseUrl = "";
            }

            final Uri uri = Uri.parse(baseUrl);

            final HlsPlaylist hlsPlaylist;
            try {
                final ByteArrayInputStream hlsManifestInput = new ByteArrayInputStream(
                        stream.getContent().getBytes(StandardCharsets.UTF_8));
                hlsPlaylist = new HlsPlaylistParser().parse(uri, hlsManifestInput);
            } catch (final IOException e) {
                throw new IOException("Error when parsing manual HLS manifest", e);
            }

            return dataSource.getHlsMediaSourceFactory(
                            new NonUriHlsPlaylistParserFactory(hlsPlaylist))
                    .createMediaSource(new MediaItem.Builder()
                            .setTag(metadata)
                            .setUri(Uri.parse(stream.getContent()))
                            .setCustomCacheKey(cacheKey)
                            .build());
        }
    }

    @NonNull
    private static <T extends Stream> HlsMediaSource buildMissAvHlsMediaSource(
            @NonNull final PlayerDataSource dataSource,
            @NonNull final T stream,
            @NonNull final StreamInfo streamInfo,
            @NonNull final String cacheKey,
            @NonNull final MediaItemTag metadata) throws IOException {
        if (!stream.isUrl() || isNullOrEmpty(stream.getContent())) {
            throw new IOException("Try to generate a MissAV HLS media source from an empty URL");
        }

        return dataSource.getMissAvHlsMediaSourceFactory(streamInfo.getUrl()).createMediaSource(
                new MediaItem.Builder()
                        .setTag(metadata)
                        .setUri(Uri.parse(stream.getContent()))
                        .setCustomCacheKey(cacheKey)
                        .build());
    }

    @NonNull
    private static <T extends Stream> SsMediaSource buildSSMediaSource(
            @NonNull final PlayerDataSource dataSource,
            @NonNull final T stream,
            @NonNull final String cacheKey,
            @NonNull final MediaItemTag metadata) throws IOException {
        final boolean isUrlStream = stream.isUrl();
        if (isUrlStream && isNullOrEmpty(stream.getContent())) {
            throw new IOException("Try to generate an SmoothStreaming media source from an empty "
                    + "string or from a null object");
        }

        if (isUrlStream) {
            return dataSource.getSSMediaSourceFactory().createMediaSource(
                    new MediaItem.Builder()
                            .setTag(metadata)
                            .setUri(Uri.parse(stream.getContent()))
                            .setCustomCacheKey(cacheKey)
                            .build());
        } else {
            String baseUrl = stream.getManifestUrl();
            if (baseUrl == null) {
                baseUrl = "";
            }

            final Uri uri = Uri.parse(baseUrl);

            final SsManifest smoothStreamingManifest;
            try {
                final ByteArrayInputStream smoothStreamingManifestInput = new ByteArrayInputStream(
                        stream.getContent().getBytes(StandardCharsets.UTF_8));
                smoothStreamingManifest = new SsManifestParser().parse(uri,
                        smoothStreamingManifestInput);
            } catch (final IOException e) {
                throw new IOException("Error when parsing manual SmoothStreaming manifest", e);
            }

            return dataSource.getSSMediaSourceFactory().createMediaSource(
                    smoothStreamingManifest,
                    new MediaItem.Builder()
                            .setTag(metadata)
                            .setUri(uri)
                            .setCustomCacheKey(cacheKey)
                            .build());
        }
    }

    private static <T extends Stream> MediaSource createYoutubeMediaSource(
            final T stream,
            final StreamInfo streamInfo,
            final PlayerDataSource dataSource,
            final String cacheKey,
            final MediaItemTag metadata) throws IOException {
        if (!(stream instanceof AudioStream || stream instanceof VideoStream)) {
            throw new IOException("Try to generate a DASH manifest of a YouTube "
                    + stream.getClass() + " " + stream.getContent());
        }

        final StreamType streamType = streamInfo.getStreamType();
        if (streamType == StreamType.VIDEO_STREAM) {
            return createYoutubeMediaSourceOfVideoStreamType(dataSource, stream, streamInfo,
                    cacheKey, metadata);
        } else if (streamType == StreamType.POST_LIVE_STREAM) {
            if (stream.getDeliveryMethod() == DeliveryMethod.HLS) {
                return buildHlsMediaSource(dataSource, stream, cacheKey, metadata);
            }
            // If the content is not an URL, uses the DASH delivery method and if the stream type
            // of the stream is a post live stream, it means that the content is an ended
            // livestream so we need to generate the manifest corresponding to the content
            // (which is the last segment of the stream)

            try {
                final ItagItem itagItem = Objects.requireNonNull(stream.getItagItem());
                final String manifestString = YoutubePostLiveStreamDvrDashManifestCreator
                        .fromPostLiveStreamDvrStreamingUrl(stream.getContent(),
                                itagItem,
                                itagItem.getTargetDurationSec(),
                                streamInfo.getDuration());
                return buildYoutubeManualDashMediaSource(dataSource,
                        createDashManifest(manifestString, stream), stream, cacheKey,
                        metadata);
            } catch (final CreationException | NullPointerException e) {
                Log.e(TAG, "Error when generating the DASH manifest of YouTube ended live stream",
                        e);
                throw new IOException("Error when generating the DASH manifest of YouTube ended "
                        + "live stream " + stream.getContent(), e);
            }
        } else {
            throw new IllegalArgumentException("DASH manifest generation of YouTube livestreams is "
                    + "not supported");
        }
    }

    private static <T extends Stream> MediaSource createYoutubeMediaSourceOfVideoStreamType(
            @NonNull final PlayerDataSource dataSource,
            @NonNull final T stream,
            @NonNull final StreamInfo streamInfo,
            @NonNull final String cacheKey,
            @NonNull final MediaItemTag metadata) throws IOException {
        final DeliveryMethod deliveryMethod = stream.getDeliveryMethod();
        switch (deliveryMethod) {
            case PROGRESSIVE_HTTP:
                if ((stream instanceof VideoStream && ((VideoStream) stream).isVideoOnly())
                        || stream instanceof AudioStream) {
                    try {
                        final String manifestString = YoutubeProgressiveDashManifestCreator
                                .fromProgressiveStreamingUrl(stream.getContent(),
                                        Objects.requireNonNull(stream.getItagItem()),
                                        streamInfo.getDuration());
                        return buildYoutubeManualDashMediaSource(dataSource,
                                createDashManifest(manifestString, stream), stream, cacheKey,
                                metadata);
                    } catch (final CreationException | IOException | NullPointerException e) {
                        Log.w(TAG, "Error when generating or parsing DASH manifest of "
                                + "YouTube progressive stream, falling back to a "
                                + "ProgressiveMediaSource.", e);
                        return buildYoutubeProgressiveMediaSource(dataSource, stream, cacheKey,
                                metadata);
                    }
                } else {
                    // Legacy progressive streams, subtitles are handled by
                    // VideoPlaybackResolver
                    return buildYoutubeProgressiveMediaSource(dataSource, stream, cacheKey,
                            metadata);
                }
            case DASH:
                // If the content is not a URL, uses the DASH delivery method and if the stream
                // type of the stream is a video stream, it means the content is an OTF stream
                // so we need to generate the manifest corresponding to the content (which is
                // the base URL of the OTF stream).

                try {
                    final String manifestString = YoutubeOtfDashManifestCreator
                            .fromOtfStreamingUrl(stream.getContent(),
                                    Objects.requireNonNull(stream.getItagItem()),
                                    streamInfo.getDuration());
                    return buildYoutubeManualDashMediaSource(dataSource,
                            createDashManifest(manifestString, stream), stream, cacheKey,
                            metadata);
                } catch (final CreationException | NullPointerException e) {
                    Log.e(TAG,
                            "Error when generating the DASH manifest of YouTube OTF stream", e);
                    throw new IOException(
                            "Error when generating the DASH manifest of YouTube OTF stream "
                                    + stream.getContent(), e);
                }
            case HLS:
                return dataSource.getYoutubeHlsMediaSourceFactory().createMediaSource(
                        new MediaItem.Builder()
                                .setTag(metadata)
                                .setUri(Uri.parse(stream.getContent()))
                                .setCustomCacheKey(cacheKey)
                                .build());
            case SABR:
                return buildSabrMediaSource(stream, streamInfo, cacheKey, metadata);
            default:
                throw new IOException("Unsupported delivery method for YouTube contents: "
                        + deliveryMethod);
        }
    }

    @NonNull
    private static MediaSource buildSabrMediaSource(@NonNull final Stream stream,
                                                    @NonNull final StreamInfo streamInfo,
                                                    @NonNull final String cacheKey,
                                                    @NonNull final MediaItemTag metadata)
            throws IOException {
        final String videoId = streamInfo.getId();
        // Honour the user-selected video quality instead of forcing the highest (4K is heavy and
        // hits the device VP9 decoder wall); audio-only playback passes 0 and keeps the best audio.
        final int preferredVideoItag =
                (stream instanceof VideoStream) ? ((VideoStream) stream).getItag() : 0;
        final YoutubeSabrInfo sabrInfo = getSabrInfo(stream);
        final SabrSessionStore.Holder holder;
        try {
            holder = SabrSessionStore.getOrCreate(App.getApp(), videoId, preferredVideoItag,
                    sabrInfo);
        } catch (final ExtractionException e) {
            throw new IOException("Could not start SABR session for " + videoId, e);
        }
        enrichSabrAudioTracks(streamInfo, holder.info);
        // One source carries both tracks; media3 track selection picks audio-only when there's no
        // video renderer (background/popup). Seeking is real because it's chunk-based, not a byte
        // stream. The audio resolver path skips its own SABR source (see VideoPlaybackResolver).
        final MediaItem mediaItem = new MediaItem.Builder()
                .setTag(metadata)
                .setUri(Uri.parse("sabr://" + videoId))
                .setCustomCacheKey(cacheKey)
                .build();
        return new SabrMediaSource(mediaItem, holder, new Localization("en", "US"));
    }

    @Nullable
    private static YoutubeSabrInfo getSabrInfo(@NonNull final Stream stream) {
        final Serializable info = stream.getDeliveryMethodInfo();
        return info instanceof YoutubeSabrInfo ? (YoutubeSabrInfo) info : null;
    }

    /**
     * SABR's main player response only carries the original-language audio; the dubbed tracks live
     * in the probe's {@link YoutubeSabrInfo}. Add the missing tracks (one {@link AudioStream} per
     * audioTrackId) to the shared {@link StreamInfo} so the generic audio-track selector (also used
     * by HLS) lists them. The added streams are UI markers; SABR playback still picks the format via
     * the session, so they clone the original stream's content/format and only swap the track info.
     */
    private static void enrichSabrAudioTracks(@NonNull final StreamInfo streamInfo,
                                              @NonNull final YoutubeSabrInfo info) {
        final List<AudioStream> audioStreams = streamInfo.getAudioStreams();
        if (audioStreams.isEmpty()) {
            return;
        }
        final AudioStream template = audioStreams.get(0);
        final Set<String> present = new HashSet<>();
        for (final AudioStream a : audioStreams) {
            present.add(Objects.toString(a.getAudioTrackId(), ""));
        }
        for (final YoutubeSabrFormat f : info.getFormats()) {
            final String trackId = f.getAudioTrackId();
            if (!f.isAudio() || trackId == null || !present.add(trackId)) {
                continue;
            }
            final String langPart = trackId.split("\\.")[0];
            final String displayName = f.getAudioTrackDisplayName();
            audioStreams.add(new AudioStream.Builder()
                    .setId(template.getId() + "-" + trackId)
                    .setContent(template.getContent(), template.isUrl())
                    .setMediaFormat(template.getFormat())
                    .setAverageBitrate(f.getBitrate())
                    .setItagItem(template.getItagItem())
                    .setDeliveryMethod(DeliveryMethod.SABR)
                    .setAudioTrackId(trackId)
                    .setAudioTrackName(displayName != null ? displayName : langPart)
                    .setAudioLocale(langPart.split("-")[0])
                    .build());
        }
    }

    @NonNull
    private static <T extends Stream> DashMediaSource buildYoutubeManualDashMediaSource(
            @NonNull final PlayerDataSource dataSource,
            @NonNull final DashManifest dashManifest,
            @NonNull final T stream,
            @NonNull final String cacheKey,
            @NonNull final MediaItemTag metadata) {
        return dataSource.getYoutubeDashMediaSourceFactory().createMediaSource(dashManifest,
                new MediaItem.Builder()
                        .setTag(metadata)
                        .setUri(Uri.parse(stream.getContent()))
                        .setCustomCacheKey(cacheKey)
                        .build());
    }

    @NonNull
    private static <T extends Stream> ProgressiveMediaSource buildYoutubeProgressiveMediaSource(
            @NonNull final PlayerDataSource dataSource,
            @NonNull final T stream,
            @NonNull final String cacheKey,
            @NonNull final MediaItemTag metadata) {
        return dataSource.getYoutubeProgressiveMediaSourceFactory()
                .createMediaSource(new MediaItem.Builder()
                        .setTag(metadata)
                        .setUri(Uri.parse(stream.getContent()))
                        .setCustomCacheKey(cacheKey)
                        .build());
    }
    private static <T extends Stream> MediaSource createNicoNicoMediaSource(
            final T stream,
            final StreamInfo streamInfo,
            final PlayerDataSource dataSource,
            final String cacheKey,
            final MediaItemTag metadata) throws IOException{
        String sourceUrl = stream.getContent();
        MediaSource.Factory factory;
        String additionalParam = URLDecoder.decode(sourceUrl.split("cookie=")[1]);
        String cookie = additionalParam.split("&length=")[0];
        String length = additionalParam.split("&length=")[1];
        sourceUrl = sourceUrl.split("#cookie=")[0];
        Uri uri = Uri.parse(sourceUrl);
        factory = dataSource.getNicoMediaSourceFactory(cookie);
        return factory.createMediaSource(
                new MediaItem.Builder()
                        .setTag(metadata)
                        .setUri(uri)
                        .setCustomCacheKey(cacheKey)
                        .build()
        );
    }

    private static <T extends Stream> MediaSource createBiliBiliMediaSource(
            final T stream,
            final StreamInfo streamInfo,
            final PlayerDataSource dataSource,
            final String cacheKey,
            final MediaItemTag metadata) throws IOException{
        final String url = stream.getContent();
        final String manifest = createBiliBiliDashManifest(stream, streamInfo);
        if (manifest != null) {
            return dataSource.getBiliDashMediaSourceFactory().createMediaSource(
                    createDashManifest(manifest, stream),
                    new MediaItem.Builder()
                            .setTag(metadata)
                            .setUri(Uri.parse(url))
                            .setCustomCacheKey(cacheKey)
                            .build());
        }
        return dataSource.getBiliMediaSourceFactory(streamInfo.getUrl()).createMediaSource(
                new MediaItem.Builder()
                        .setTag(metadata)
                        .setUri(Uri.parse(url))
                        .setCustomCacheKey(cacheKey)
                        .build());
    }

    @Nullable
    private static <T extends Stream> String createBiliBiliDashManifest(
            final T stream,
            final StreamInfo streamInfo) {
        final boolean isAudio = stream instanceof AudioStream;
        final boolean isVideo = stream instanceof VideoStream;
        if (!isAudio && !isVideo) {
            return null;
        }
        final int initStart;
        final int initEnd;
        final int indexStart;
        final int indexEnd;
        final String mimeType;
        final String codecs;
        final int bandwidth;
        final String extraAttributes;
        if (isAudio) {
            final AudioStream audioStream = (AudioStream) stream;
            initStart = audioStream.getInitStart();
            initEnd = audioStream.getInitEnd();
            indexStart = audioStream.getIndexStart();
            indexEnd = audioStream.getIndexEnd();
            mimeType = "audio/mp4";
            codecs = audioStream.getCodec();
            bandwidth = audioStream.getBitrate() > 0
                    ? audioStream.getBitrate() : audioStream.getAverageBitrate();
            extraAttributes = "";
        } else {
            final VideoStream videoStream = (VideoStream) stream;
            initStart = videoStream.getInitStart();
            initEnd = videoStream.getInitEnd();
            indexStart = videoStream.getIndexStart();
            indexEnd = videoStream.getIndexEnd();
            mimeType = "video/mp4";
            codecs = videoStream.getCodec();
            bandwidth = videoStream.getBitrate();
            extraAttributes = (videoStream.getWidth() > 0 ? " width=\"" + videoStream.getWidth() + "\"" : "")
                    + (videoStream.getHeight() > 0 ? " height=\"" + videoStream.getHeight() + "\"" : "")
                    + (videoStream.getFps() > 0 ? " frameRate=\"" + videoStream.getFps() + "\"" : "");
        }
        if (initEnd <= initStart || indexEnd <= indexStart || bandwidth <= 0
                || codecs == null || codecs.isEmpty()) {
            return null;
        }
        final String contentType = isAudio ? "audio" : "video";
        final long duration = Math.max(1, streamInfo.getDuration());
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" type=\"static\" profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\" minBufferTime=\"PT1.5S\" mediaPresentationDuration=\"PT" + duration + "S\">"
                + "<Period duration=\"PT" + duration + "S\">"
                + "<AdaptationSet contentType=\"" + contentType + "\" mimeType=\"" + mimeType + "\" subsegmentAlignment=\"true\">"
                + "<Representation id=\"" + escapeXml(stream.getId()) + "\" bandwidth=\"" + bandwidth + "\" codecs=\"" + escapeXml(codecs) + "\"" + extraAttributes + ">"
                + "<BaseURL>" + escapeXml(stream.getContent()) + "</BaseURL>"
                + "<SegmentBase indexRange=\"" + indexStart + "-" + indexEnd + "\">"
                + "<Initialization range=\"" + initStart + "-" + initEnd + "\"/>"
                + "</SegmentBase>"
                + "</Representation>"
                + "</AdaptationSet>"
                + "</Period>"
                + "</MPD>";
    }

    private static String escapeXml(final String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
