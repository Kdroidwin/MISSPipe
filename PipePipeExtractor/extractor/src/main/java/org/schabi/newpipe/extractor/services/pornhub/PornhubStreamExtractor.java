package org.schabi.newpipe.extractor.services.pornhub;

import org.jsoup.nodes.Document;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.InfoItemExtractor;
import org.schabi.newpipe.extractor.InfoItemsCollector;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.MetaInfo;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nonnull;

public final class PornhubStreamExtractor extends StreamExtractor {
    private static final int MAX_RELATED_ITEMS = 24;
    private static final int MIN_RELATED_ITEMS = 8;
    private Document document;
    private PornhubSearchResult fallbackSearchResult;

    public PornhubStreamExtractor(final StreamingService service,
                                  final LinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        document = PornhubParsingHelper.fetchDocument(getUrl(), PornhubParsingHelper.BASE_URL + "/");
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        ensureFetched();
        final String title = PornhubParsingHelper.extractTitle(document);
        if (!title.isEmpty()) {
            return title;
        }
        final PornhubSearchResult fallback = findFallbackSearchResult();
        return fallback == null || fallback.title.isEmpty() ? getId() : fallback.title;
    }

    @Nonnull
    @Override
    public String getThumbnailUrl() throws ParsingException {
        ensureFetched();
        final String thumbnail = PornhubParsingHelper.extractThumbnail(document);
        if (!thumbnail.isEmpty()) {
            return thumbnail;
        }
        final PornhubSearchResult fallback = findFallbackSearchResult();
        return fallback == null ? "" : fallback.thumbnail;
    }

    @Nonnull
    @Override
    public Description getDescription() throws ParsingException {
        ensureFetched();
        final List<String> lines = new ArrayList<>();
        final String description = PornhubParsingHelper.extractDescription(document);
        if (!description.isEmpty()) {
            lines.add(description);
        }
        final List<String> tags = PornhubParsingHelper.extractTags(document);
        if (!tags.isEmpty()) {
            lines.add("Tags: " + String.join(", ", tags));
        }
        return lines.isEmpty()
                ? Description.EMPTY_DESCRIPTION
                : new Description(String.join("\n", lines), Description.PLAIN_TEXT);
    }

    @Override
    public long getLength() throws ParsingException {
        ensureFetched();
        return PornhubParsingHelper.extractDuration(document);
    }

    @Nonnull
    @Override
    public String getUploaderName() throws ParsingException {
        ensureFetched();
        return PornhubParsingHelper.extractUploaderName(document);
    }

    @Nonnull
    @Override
    public String getUploaderUrl() throws ParsingException {
        ensureFetched();
        return PornhubParsingHelper.extractUploaderUrl(document);
    }

    @Nonnull
    @Override
    public List<String> getTags() throws ParsingException {
        ensureFetched();
        return PornhubParsingHelper.extractTags(document);
    }

    @Override
    public String getTextualUploadDate() {
        return "";
    }

    @Override
    public List<AudioStream> getAudioStreams() {
        return Collections.emptyList();
    }

    @Override
    public List<VideoStream> getVideoStreams() throws IOException, ExtractionException {
        ensureFetched();
        final List<PornhubVideoSource> sources =
                PornhubParsingHelper.extractVideoSources(document, getUrl());
        if (sources.isEmpty()) {
            throw new ParsingException("Could not find Pornhub video URL");
        }
        final List<VideoStream> streams = new ArrayList<>();
        for (final PornhubVideoSource source : sources) {
            final VideoStream.Builder builder = new VideoStream.Builder()
                    .setId(source.id)
                    .setContent(source.url, true)
                    .setIsVideoOnly(false)
                    .setResolution(source.resolution)
                    .setDeliveryMethod(source.deliveryMethod);
            if (source.deliveryMethod == DeliveryMethod.HLS) {
                builder.setMediaFormat(MediaFormat.MPEG_4)
                        .setManifestUrl(source.url);
            } else {
                builder.setMediaFormat(MediaFormat.MPEG_4);
            }
            streams.add(builder.build());
        }
        return streams;
    }

    @Override
    public List<VideoStream> getVideoOnlyStreams() {
        return Collections.emptyList();
    }

    @Override
    public StreamType getStreamType() {
        return StreamType.VIDEO_STREAM;
    }

    @Override
    public InfoItemsCollector<? extends InfoItem, ? extends InfoItemExtractor> getRelatedItems()
            throws IOException, ExtractionException {
        ensureFetched();
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        final Set<String> seen = new HashSet<>();
        appendRelatedItems(collector, seen,
                PornhubParsingHelper.extractRelatedVideoCards(document, getId(),
                        MAX_RELATED_ITEMS + 1, getUrl()));
        if (collector.getItems().size() < MIN_RELATED_ITEMS) {
            appendQueryFallbackRelatedItems(collector, seen);
        }
        if (collector.getItems().isEmpty()) {
            appendListingFallbackRelatedItems(collector, seen);
        }
        return collector;
    }

    @Nonnull
    @Override
    public List<MetaInfo> getMetaInfo() {
        return Collections.emptyList();
    }

    private void appendRelatedItems(final StreamInfoItemsCollector collector,
                                    final Set<String> seen,
                                    final List<PornhubSearchResult> results)
            throws ParsingException {
        final String currentId = getId();
        for (final PornhubSearchResult result : results) {
            if (!result.id.equals(currentId) && seen.add(result.id)) {
                collector.commit(new PornhubInfoItemExtractor(result));
            }
            if (collector.getItems().size() >= MAX_RELATED_ITEMS) {
                return;
            }
        }
    }

    private void appendQueryFallbackRelatedItems(final StreamInfoItemsCollector collector,
                                                 final Set<String> seen) {
        final List<String> queries = new ArrayList<>();
        addQuery(queries, PornhubParsingHelper.extractUploaderName(document));
        for (final String tag : PornhubParsingHelper.extractTags(document)) {
            addQuery(queries, tag);
            if (queries.size() >= 6) {
                break;
            }
        }
        for (final String token : PornhubParsingHelper.extractTitle(document)
                .split("[\\s,./\\\\|()\\[\\]_-]+")) {
            if (isUsefulQuery(token)) {
                addQuery(queries, token);
            }
            if (queries.size() >= 10) {
                break;
            }
        }
        for (final String query : queries) {
            try {
                appendRelatedItems(collector, seen,
                        PornhubParsingHelper.search(query, MAX_RELATED_ITEMS + 1));
                if (collector.getItems().size() >= MIN_RELATED_ITEMS) {
                    return;
                }
            } catch (final IOException | ExtractionException ignored) {
                // Try the next current-video-specific query.
            }
        }
    }

    private void appendListingFallbackRelatedItems(final StreamInfoItemsCollector collector,
                                                   final Set<String> seen) {
        final String[] urls = {
                PornhubParsingHelper.BASE_URL + "/video?o=tr&t=m",
                PornhubParsingHelper.BASE_URL + "/video?o=mv&t=m"
        };
        for (final String url : urls) {
            try {
                appendRelatedItems(collector, seen,
                        PornhubParsingHelper.listVideos(url, MAX_RELATED_ITEMS + 1));
                if (!collector.getItems().isEmpty()) {
                    return;
                }
            } catch (final IOException | ExtractionException ignored) {
                // Public lists are only a last-resort fallback.
            }
        }
    }

    private PornhubSearchResult findFallbackSearchResult() {
        if (fallbackSearchResult != null) {
            return fallbackSearchResult;
        }
        try {
            for (final PornhubSearchResult result : PornhubParsingHelper.search(safeGetId(), 20)) {
                if (safeGetId().equals(result.id)) {
                    fallbackSearchResult = result;
                    return result;
                }
            }
        } catch (final IOException | ExtractionException ignored) {
            // Search fallback must never block playback.
        }
        return null;
    }

    private static void addQuery(final List<String> queries, final String query) {
        if (isUsefulQuery(query) && !queries.contains(query.trim())) {
            queries.add(query.trim());
        }
    }

    private static boolean isUsefulQuery(final String query) {
        if (query == null || query.trim().length() < 3) {
            return false;
        }
        final String lower = query.trim().toLowerCase(Locale.ROOT);
        return !lower.equals("pornhub") && !lower.equals("video") && !lower.equals("videos");
    }

    private String safeGetId() {
        try {
            return getId();
        } catch (final ParsingException e) {
            return "";
        }
    }

    private void ensureFetched() throws ParsingException {
        if (document == null) {
            throw new ParsingException("Pornhub page was not fetched");
        }
    }
}
