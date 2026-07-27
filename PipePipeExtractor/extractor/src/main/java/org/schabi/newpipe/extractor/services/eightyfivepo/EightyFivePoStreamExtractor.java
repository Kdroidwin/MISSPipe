package org.schabi.newpipe.extractor.services.eightyfivepo;

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

public final class EightyFivePoStreamExtractor extends StreamExtractor {
    private static final int MAX_RELATED_ITEMS = 24;
    private static final int MIN_RELATED_ITEMS = 8;
    private Document document;
    private String videoUrl;
    private EightyFivePoSearchResult fallbackSearchResult;

    public EightyFivePoStreamExtractor(final StreamingService service,
                                       final LinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        document = EightyFivePoParsingHelper.fetchDocument(getUrl(),
                EightyFivePoParsingHelper.BASE_URL + "/ja/");
        if (EightyFivePoParsingHelper.normalizeUrl(getUrl()).contains("/embed/")
                || EightyFivePoParsingHelper.tryExtractVideoUrl(document).isEmpty()
                || EightyFivePoParsingHelper.extractTitle(document).isEmpty()) {
            replaceWithBetterMetadataDocument();
        }
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        ensureFetched();
        final String title = EightyFivePoParsingHelper.extractTitle(document);
        final EightyFivePoSearchResult result = findFallbackSearchResult();
        if (result != null && !result.title.isEmpty()) {
            return result.title;
        }
        if (!title.isEmpty()) {
            return title;
        }
        return result == null || result.title.isEmpty() ? getId() : result.title;
    }

    @Nonnull
    @Override
    public String getThumbnailUrl() throws ParsingException {
        ensureFetched();
        final String thumbnail = EightyFivePoParsingHelper.extractThumbnail(document);
        if (!thumbnail.isEmpty()) {
            return thumbnail;
        }
        final EightyFivePoSearchResult result = findFallbackSearchResult();
        return result == null ? "" : result.thumbnail;
    }

    @Nonnull
    @Override
    public Description getDescription() throws ParsingException {
        ensureFetched();
        final List<String> lines = new ArrayList<>();
        final String description = EightyFivePoParsingHelper.extractDescription(document);
        if (!description.isEmpty()) {
            lines.add(description);
        }
        final List<String> tags = EightyFivePoParsingHelper.extractTags(document);
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
        return EightyFivePoParsingHelper.extractDuration(document);
    }

    @Nonnull
    @Override
    public String getUploaderName() throws ParsingException {
        ensureFetched();
        return EightyFivePoParsingHelper.extractUploaderName(document);
    }

    @Nonnull
    @Override
    public String getUploaderUrl() throws ParsingException {
        ensureFetched();
        return EightyFivePoParsingHelper.extractUploaderUrl(document);
    }

    @Nonnull
    @Override
    public List<String> getTags() throws ParsingException {
        ensureFetched();
        return EightyFivePoParsingHelper.extractTags(document);
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
        final List<EightyFivePoVideoSource> sources = getVideoSources();
        final List<VideoStream> streams = new ArrayList<>();
        for (final EightyFivePoVideoSource source : sources) {
            streams.add(new VideoStream.Builder()
                    .setId(source.id)
                    .setContent(source.url, true)
                    .setIsVideoOnly(false)
                    .setResolution(source.resolution)
                    .setMediaFormat(MediaFormat.MPEG_4)
                    .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                    .build());
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
        final Set<String> seenUrls = new HashSet<>();
        final String currentUrl = EightyFivePoParsingHelper.normalizeUrl(getUrl()).split("[?#]", 2)[0];
        final String currentId = safeGetId();
        appendPerVideoFallbackRelatedItems(collector, seenUrls, currentUrl, currentId);
        if (collector.getItems().size() >= MIN_RELATED_ITEMS) {
            return collector;
        }
        try {
            appendRelatedItems(collector, seenUrls, currentUrl, currentId,
                    EightyFivePoParsingHelper.extractRelatedVideoCards(
                            document, currentId, MAX_RELATED_ITEMS + 1, getUrl()));
        } catch (final IOException | ExtractionException ignored) {
            // 85po does not expose related items for every page shape.
        }
        if (collector.getItems().isEmpty()) {
            appendListingFallbackRelatedItems(collector, seenUrls, currentUrl, currentId);
        }
        return collector;
    }

    private void appendRelatedItems(final StreamInfoItemsCollector collector,
                                    final Set<String> seenUrls,
                                    final String currentUrl,
                                    final String currentId,
                                    final List<EightyFivePoSearchResult> results) {
        for (final EightyFivePoSearchResult result : results) {
            final String cleanUrl = result.url.split("[?#]", 2)[0];
            if (!cleanUrl.equals(currentUrl)
                    && !result.id.equals(currentId)
                    && seenUrls.add(cleanUrl)) {
                collector.commit(new EightyFivePoInfoItemExtractor(result));
            }
            if (collector.getItems().size() >= MAX_RELATED_ITEMS) {
                break;
            }
        }
    }

    private void appendPerVideoFallbackRelatedItems(final StreamInfoItemsCollector collector,
                                                    final Set<String> seenUrls,
                                                    final String currentUrl,
                                                    final String currentId) {
        final List<String> queries = new ArrayList<>();
        final EightyFivePoSearchResult result = findFallbackSearchResult();
        final String title = result == null || result.title.isEmpty()
                ? EightyFivePoParsingHelper.extractTitle(document)
                : result.title;
        addSearchQuery(queries, title);
        if (result != null && !result.title.isEmpty()) {
            final String documentTitle = EightyFivePoParsingHelper.extractTitle(document);
            if (!documentTitle.equals(result.title)) {
                addSearchQuery(queries, documentTitle);
            }
        }
        addSearchQuery(queries, queryFromUrlSlug(safeGetUrl()));
        if (result != null) {
            addSearchQuery(queries, queryFromUrlSlug(result.url));
        }
        for (final String token : title.split("[\\s,./\\\\|()\\[\\]_-]+")) {
            if (isSpecificRelatedQuery(token)) {
                addSearchQuery(queries, token);
            }
        }
        appendRelatedFromQueries(collector, seenUrls, currentUrl, currentId, queries);
        if (collector.getItems().size() >= MIN_RELATED_ITEMS) {
            return;
        }

        appendUploaderRelatedItems(collector, seenUrls, currentUrl, currentId);
        if (collector.getItems().size() >= MIN_RELATED_ITEMS) {
            return;
        }

        final List<String> tags = EightyFivePoParsingHelper.extractTags(document);
        final List<String> combinedTags = new ArrayList<>();
        for (int i = 0; i < tags.size(); i++) {
            final String first = tags.get(i);
            if (!isSpecificRelatedQuery(first)) {
                continue;
            }
            for (int j = i + 1; j < tags.size(); j++) {
                final String second = tags.get(j);
                if (isSpecificRelatedQuery(second)) {
                    addSearchQuery(combinedTags, first + " " + second);
                    break;
                }
            }
            if (combinedTags.size() >= 4) {
                break;
            }
        }
        appendRelatedFromQueries(collector, seenUrls, currentUrl, currentId, combinedTags);
        if (collector.getItems().size() >= MIN_RELATED_ITEMS) {
            return;
        }

        final List<String> singleTags = new ArrayList<>();
        for (final String tag : tags) {
            if (isSpecificRelatedQuery(tag)) {
                addSearchQuery(singleTags, tag);
            }
            if (singleTags.size() >= 6) {
                break;
            }
        }
        appendRelatedFromQueries(collector, seenUrls, currentUrl, currentId, singleTags);
    }

    private void appendRelatedFromQueries(final StreamInfoItemsCollector collector,
                                          final Set<String> seenUrls,
                                          final String currentUrl,
                                          final String currentId,
                                          final List<String> queries) {
        for (final String query : queries) {
            if (!isSpecificRelatedQuery(query)) {
                continue;
            }
            try {
                appendRelatedItems(collector, seenUrls, currentUrl, currentId,
                        EightyFivePoParsingHelper.search(query, MAX_RELATED_ITEMS + 1));
                if (collector.getItems().size() >= MIN_RELATED_ITEMS) {
                    return;
                }
            } catch (final IOException | ExtractionException ignored) {
                // Try the next per-video query before returning an empty section.
            }
        }
    }

    private void appendUploaderRelatedItems(final StreamInfoItemsCollector collector,
                                            final Set<String> seenUrls,
                                            final String currentUrl,
                                            final String currentId) {
        try {
            final String uploaderUrl = EightyFivePoParsingHelper.extractUploaderUrl(document);
            if (!uploaderUrl.isEmpty()
                    && !uploaderUrl.equals(EightyFivePoParsingHelper.BASE_URL + "/ja/")) {
                appendRelatedItems(collector, seenUrls, currentUrl, currentId,
                        EightyFivePoParsingHelper.listVideos(uploaderUrl, MAX_RELATED_ITEMS + 1));
            }
        } catch (final IOException | ExtractionException ignored) {
            // Uploader page is only a related-items fallback.
        }
    }

    private void appendListingFallbackRelatedItems(final StreamInfoItemsCollector collector,
                                                   final Set<String> seenUrls,
                                                   final String currentUrl,
                                                   final String currentId) {
        final String[] urls = {
                EightyFivePoParsingHelper.BASE_URL + "/ja/top-rated/",
                EightyFivePoParsingHelper.BASE_URL + "/ja/latest-updates/"
        };
        for (final String url : urls) {
            try {
                appendRelatedItems(collector, seenUrls, currentUrl, currentId,
                        EightyFivePoParsingHelper.listVideos(url, MAX_RELATED_ITEMS + 1));
                if (!collector.getItems().isEmpty()) {
                    return;
                }
            } catch (final IOException | ExtractionException ignored) {
                // Public listings are only a last-resort fallback for an otherwise empty rail.
            }
        }
    }

    private static boolean isSpecificRelatedQuery(final String query) {
        if (query == null) {
            return false;
        }
        final String normalized = query.trim();
        if (normalized.length() < 3) {
            return false;
        }
        final String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.equals("85po") || lower.equals("jav") || lower.equals("fc2")
                || lower.equals("video") || lower.equals("videos")) {
            return false;
        }
        int lettersOrDigits = 0;
        boolean hasNonAscii = false;
        for (int i = 0; i < normalized.length(); i++) {
            final char c = normalized.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                lettersOrDigits++;
                if (c > 0x7f) {
                    hasNonAscii = true;
                }
            }
        }
        return lettersOrDigits >= 3 && (hasNonAscii || normalized.length() >= 4);
    }

    @Nonnull
    @Override
    public List<MetaInfo> getMetaInfo() {
        return Collections.emptyList();
    }

    private String getVideoUrl() throws IOException, ExtractionException {
        if (videoUrl == null || videoUrl.isEmpty()) {
            final List<EightyFivePoVideoSource> sources = getVideoSources();
            if (sources.isEmpty()) {
                throw new ParsingException("Could not find 85po video URL");
            }
            videoUrl = sources.get(0).url;
        }
        return videoUrl;
    }

    private List<EightyFivePoVideoSource> getVideoSources()
            throws IOException, ExtractionException {
        final String pageUrl = EightyFivePoParsingHelper.normalizeUrl(getUrl());
        final List<EightyFivePoVideoSource> sources =
                EightyFivePoParsingHelper.findVideoSources(pageUrl, getId(), document);
        if (!sources.isEmpty()) {
            return sources;
        }
        throw new ParsingException("Could not find 85po video URL");
    }

    private void replaceWithBetterMetadataDocument() throws IOException, ExtractionException {
        final List<String> preferredCandidates = new ArrayList<>();
        final List<String> fallbackCandidates = new ArrayList<>();
        final EightyFivePoSearchResult result = findFallbackSearchResult();
        if (result != null) {
            addCandidate(preferredCandidates, result.url);
        }
        addCandidate(preferredCandidates, EightyFivePoParsingHelper.extractJapaneseVideoUrl(document, getId()));
        addCandidate(preferredCandidates, EightyFivePoParsingHelper.BASE_URL + "/ja/v/" + getId() + "/");
        addCandidate(fallbackCandidates, EightyFivePoParsingHelper.extractCanonicalVideoUrl(document, getId()));
        addCandidate(fallbackCandidates, EightyFivePoParsingHelper.extractEmbedUrl(document, getId()));
        addCandidate(fallbackCandidates, EightyFivePoParsingHelper.BASE_URL + "/ja/embed/" + getId());
        addCandidate(fallbackCandidates, EightyFivePoParsingHelper.BASE_URL + "/embed/" + getId());

        if (replaceDocumentFromCandidates(preferredCandidates, true)) {
            return;
        }
        replaceDocumentFromCandidates(fallbackCandidates, false);
    }

    private boolean replaceDocumentFromCandidates(final List<String> candidates,
                                                  final boolean requireJapaneseVideoPage) {
        for (final String candidate : candidates) {
            try {
                final Document candidateDocument = EightyFivePoParsingHelper.fetchDocument(
                        candidate, getUrl());
                final String canonical = EightyFivePoParsingHelper.extractJapaneseVideoUrl(
                        candidateDocument, safeGetId());
                if (requireJapaneseVideoPage
                        && !candidate.contains("/ja/v/")
                        && canonical.isEmpty()) {
                    continue;
                }
                if (isUsableMetadataDocument(candidateDocument)) {
                    document = candidateDocument;
                    return true;
                }
            } catch (final IOException | ExtractionException ignored) {
                // Keep the originally fetched document if no metadata candidate is usable.
            }
        }
        return false;
    }

    private static boolean isUsableMetadataDocument(final Document candidateDocument) {
        return !EightyFivePoParsingHelper.extractTitle(candidateDocument).isEmpty()
                || !EightyFivePoParsingHelper.tryExtractVideoUrl(candidateDocument).isEmpty()
                || !EightyFivePoParsingHelper.extractVideoCards(candidateDocument, 1).isEmpty();
    }

    private EightyFivePoSearchResult findFallbackSearchResult() {
        if (fallbackSearchResult != null) {
            return fallbackSearchResult;
        }
        final String id = safeGetId();
        final List<String> queries = new ArrayList<>();
        addSearchQuery(queries, id);
        addSearchQuery(queries, queryFromUrlSlug(safeGetUrl()));
        addSearchQuery(queries, EightyFivePoParsingHelper.extractTitle(document));
        for (final String query : queries) {
            try {
                for (final EightyFivePoSearchResult result
                        : EightyFivePoParsingHelper.search(query, 20)) {
                    if (id.equals(result.id)) {
                        fallbackSearchResult = result;
                        return result;
                    }
                }
            } catch (final IOException | ExtractionException ignored) {
                // Search is only a metadata fallback. Keep the stream usable if it fails.
            }
        }
        return null;
    }

    private static void addSearchQuery(final List<String> queries, final String query) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }
        final String normalized = query.trim();
        if (!queries.contains(normalized)) {
            queries.add(normalized);
        }
    }

    private static String queryFromUrlSlug(final String url) {
        final String normalized = EightyFivePoParsingHelper.normalizeUrl(url);
        if (normalized == null) {
            return "";
        }
        final String cleanUrl = normalized.split("[?#]", 2)[0];
        final int markerIndex = cleanUrl.indexOf("/v/");
        if (markerIndex < 0) {
            return "";
        }
        final String[] parts = cleanUrl.substring(markerIndex + 3).split("/");
        if (parts.length < 2) {
            return "";
        }
        return parts[1].replace('-', ' ').trim();
    }

    private String safeGetId() {
        try {
            return getId();
        } catch (final ParsingException e) {
            return "";
        }
    }

    private String safeGetUrl() {
        try {
            return getUrl();
        } catch (final ParsingException e) {
            return "";
        }
    }

    private static void addCandidate(final List<String> candidates, final String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        final String normalized = EightyFivePoParsingHelper.normalizeUrl(url);
        if (normalized != null && !normalized.isEmpty() && !candidates.contains(normalized)) {
            candidates.add(normalized);
        }
    }

    private void ensureFetched() throws ParsingException {
        if (document == null) {
            throw new ParsingException("85po page was not fetched");
        }
    }
}
