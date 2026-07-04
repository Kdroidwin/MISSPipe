package org.schabi.newpipe.extractor.services.missav;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

public final class MissAvStreamExtractor extends StreamExtractor {
    private static final int MAX_RELATED_ITEMS = 24;
    private Document document;
    private String hlsUrl;

    public MissAvStreamExtractor(final StreamingService service, final LinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        document = MissAvParsingHelper.fetchDocument(getUrl());
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        ensureFetched();
        final String title = MissAvParsingHelper.text(
                document.selectFirst("h1.text-base, h1"));
        return title.isEmpty() ? getId() : title;
    }

    @Nonnull
    @Override
    public String getThumbnailUrl() throws ParsingException {
        ensureFetched();
        return MissAvParsingHelper.extractThumbnail(document);
    }

    @Nonnull
    @Override
    public Description getDescription() throws ParsingException {
        ensureFetched();
        final List<String> lines = new ArrayList<>();
        addLine(lines, "\u52d5\u753b\u306e\u8a73\u7d30",
                MissAvParsingHelper.extractPageDescription(document));
        addLine(lines, "Code", getVideoCode());
        addLine(lines, "Actress", getUploaderName());
        addLine(lines, "\u5973\u512a\u30bf\u30b0", String.join(" ", getActressTags()));
        addLine(lines, "Manufacturer", getManufacturer());
        addLine(lines, "Label", getLabel());
        addLine(lines, "Genres", String.join(", ", getGenres()));
        addLine(lines, "Published", getTextualUploadDate());
        return lines.isEmpty()
                ? Description.EMPTY_DESCRIPTION
                : new Description(String.join("\n", lines), Description.PLAIN_TEXT);
    }

    @Override
    public String getTextualUploadDate() throws ParsingException {
        ensureFetched();
        return MissAvParsingHelper.firstMetaValue(document, 0);
    }

    public String getVideoCode() throws ParsingException {
        ensureFetched();
        return MissAvParsingHelper.firstMetaValue(document, 1);
    }

    public List<String> getGenres() throws ParsingException {
        ensureFetched();
        return MissAvParsingHelper.linkedMetaValues(document, 3);
    }

    public String getSeries() throws ParsingException {
        ensureFetched();
        return firstLinkedMeta(4);
    }

    public String getManufacturer() throws ParsingException {
        ensureFetched();
        return firstLinkedMeta(5);
    }

    public String getLabel() throws ParsingException {
        ensureFetched();
        return firstLinkedMeta(6);
    }

    public List<String> getActressTags() throws ParsingException {
        ensureFetched();
        final List<String> actresses = MissAvParsingHelper.actressNames(document);
        final List<String> tags = new ArrayList<>();
        for (final String actress : actresses) {
            if (!actress.isEmpty()) {
                tags.add("#" + actress.replace(" ", "_"));
            }
        }
        return tags;
    }

    @Nonnull
    @Override
    public List<String> getTags() throws ParsingException {
        return getActressTags();
    }

    @Nonnull
    @Override
    public String getUploaderUrl() throws ParsingException {
        ensureFetched();
        final List<String> urls = MissAvParsingHelper.actressUrls(document);
        if (!urls.isEmpty()) {
            return urls.get(0);
        }
        final List<String> actresses = MissAvParsingHelper.actressNames(document);
        return actresses.isEmpty()
                ? ""
                : MissAvParsingHelper.BASE_URL + "/search/"
                        + MissAvParsingHelper.encodeQuery(actresses.get(0));
    }

    @Nonnull
    @Override
    public String getUploaderName() throws ParsingException {
        ensureFetched();
        final List<String> actresses = MissAvParsingHelper.actressNames(document);
        return actresses.isEmpty() ? "MissAV" : String.join(" / ", actresses);
    }

    @Nonnull
    @Override
    public String getHlsUrl() throws ParsingException {
        ensureFetched();
        if (hlsUrl == null || hlsUrl.isEmpty()) {
            hlsUrl = MissAvParsingHelper.extractHlsUrl(document);
        }
        return hlsUrl;
    }

    @Override
    public List<AudioStream> getAudioStreams() {
        return Collections.emptyList();
    }

    @Override
    public List<VideoStream> getVideoStreams() throws IOException, ExtractionException {
        final List<VideoStream> streams = new ArrayList<>();
        addHlsStream(streams, "hls", "HLS", getHlsUrl());
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
        final Set<String> seenUrls = new HashSet<>();
        final String currentUrl = MissAvParsingHelper.localizeUrl(getUrl()).split("[?#]", 2)[0];

        if (collector.getItems().isEmpty()) {
            appendRecommendedRelatedItems(collector, seenUrls, currentUrl);
        }
        if (collector.getItems().isEmpty()) {
            appendDocumentRelatedItems(document, collector, seenUrls, currentUrl);
        }
        if (collector.getItems().isEmpty()) {
            try {
                appendSearchFallbackRelatedItems(collector, seenUrls, currentUrl);
            } catch (final IOException | ExtractionException ignored) {
                // Keep DOM/recommendation results if the search fallback fails.
            }
        }
        return collector;
    }

    @Nonnull
    @Override
    public List<MetaInfo> getMetaInfo() {
        return Collections.emptyList();
    }

    private void ensureFetched() throws ParsingException {
        if (document == null) {
            throw new ParsingException("MissAV page was not fetched");
        }
    }

    private String firstLinkedMeta(final int index) {
        final List<String> values = MissAvParsingHelper.linkedMetaValues(document, index);
        return values.isEmpty() ? "" : values.get(0);
    }

    private static void addHlsStream(final List<VideoStream> streams,
                                     final String id,
                                     final String resolution,
                                     final String url) throws ParsingException {
        streams.add(new VideoStream.Builder()
                .setId(id)
                .setContent(url, true)
                .setIsVideoOnly(false)
                .setResolution(resolution)
                .setMediaFormat(MediaFormat.MPEG_4)
                .setDeliveryMethod(DeliveryMethod.HLS)
                .setManifestUrl(url + "#missav=1")
                .build());
    }

    private static Element findVideoCard(final Element link) {
        Element current = link;
        for (int depth = 0; depth < 5 && current.parent() != null; depth++) {
            if (current.selectFirst("img") != null
                    && current.selectFirst("a[href]") != null
                    && !MissAvParsingHelper.text(current).isEmpty()) {
                return current;
            }
            current = current.parent();
        }
        return link;
    }

    private static boolean isRelatedVideoUrl(final String url) {
        if (!MissAvStreamLinkHandlerFactory.getInstance().onAcceptUrl(url)) {
            return false;
        }
        try {
            return MissAvParsingHelper.isVideoId(MissAvParsingHelper.extractId(url));
        } catch (final ParsingException e) {
            return false;
        }
    }

    private static void appendDocumentRelatedItems(final Document sourceDocument,
                                                   final StreamInfoItemsCollector collector,
                                                   final Set<String> seenUrls,
                                                   final String currentUrl)
            throws ParsingException {
        for (final Element link : sourceDocument.select("a[href]")) {
            final String href = MissAvParsingHelper.localizeUrl(link.absUrl("href"));
            final String cleanHref = href.split("[?#]", 2)[0];
            if (!seenUrls.add(cleanHref) || cleanHref.equals(currentUrl)) {
                continue;
            }
            if (isRelatedVideoUrl(cleanHref)) {
                final Element card = findVideoCard(link);
                if (card.selectFirst("img") != null) {
                    collector.commit(new MissAvKioskInfoItemExtractor(card, cleanHref));
                } else {
                    collector.commit(new MissAvSimpleInfoItemExtractor(cleanHref,
                            MissAvParsingHelper.text(link)));
                }
                if (collector.getItems().size() >= MAX_RELATED_ITEMS) {
                    break;
                }
            }
        }
    }

    private void appendRecommendedRelatedItems(final StreamInfoItemsCollector collector,
                                               final Set<String> seenUrls,
                                               final String currentUrl)
            throws ParsingException {
        final String id = MissAvParsingHelper.extractId(getUrl());
        if (!MissAvParsingHelper.isVideoId(id)) {
            return;
        }
        try {
            appendSearchResults(collector, seenUrls, currentUrl,
                    MissAvParsingHelper.recommendRelated(id, 24));
        } catch (final IOException | ExtractionException ignored) {
            // Fall back to DOM and search based extraction below.
        }
    }

    private void appendSearchFallbackRelatedItems(final StreamInfoItemsCollector collector,
                                                  final Set<String> seenUrls,
                                                  final String currentUrl)
            throws IOException, ExtractionException {
        String query = getUploaderName();
        if ("MissAV".equals(query) || query.isEmpty()) {
            query = getManufacturer();
        }
        if (query == null || query.isEmpty()) {
            return;
        }
        appendSearchResults(collector, seenUrls, currentUrl, MissAvParsingHelper.search(query, 24));
    }

    private static void appendSearchResults(final StreamInfoItemsCollector collector,
                                            final Set<String> seenUrls,
                                            final String currentUrl,
                                            final List<MissAvSearchResult> results) {
        for (final MissAvSearchResult result : results) {
            final String cleanUrl = result.url.split("[?#]", 2)[0];
            if (!cleanUrl.equals(currentUrl)
                    && MissAvParsingHelper.isVideoId(result.id)
                    && seenUrls.add(cleanUrl)) {
                collector.commit(new MissAvSearchInfoItemExtractor(result));
            }
        }
    }

    private static void addLine(final List<String> lines, final String label, final String value) {
        if (value != null && !value.isEmpty()) {
            lines.add(label + ": " + value);
        }
    }
}
