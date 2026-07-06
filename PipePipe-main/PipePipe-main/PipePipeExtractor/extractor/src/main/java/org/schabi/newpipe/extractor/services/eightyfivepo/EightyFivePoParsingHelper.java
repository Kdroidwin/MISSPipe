package org.schabi.newpipe.extractor.services.eightyfivepo;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EightyFivePoParsingHelper {
    public static final String BASE_URL = "https://www.85po.com";
    private static final String LANG_PREFIX = "/ja";
    private static final String MARKER = "#85po=1";
    private static final Pattern VIDEO_URL_ENTRY_PATTERN = Pattern.compile(
            "[\"']?(video(?:_[a-z0-9]+)*_url(?:_[a-z0-9]+)?)[\"']?\\s*[:=]\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DIRECT_VIDEO_URL_PATTERN = Pattern.compile(
            "(?:https?:)?//[^\"'\\s<>]+/(?:ja/)?get_file/[^\"'\\s<>]+|/(?:ja/)?get_file/[^\"'\\s<>]+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VIDEO_PAGE_URL_PATTERN = Pattern.compile(
            "https?://[^\"'\\s<>]+/(?:[a-z]{2}/)?v/\\d+(?:/[^\"'\\s<>]*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QUALITY_LABEL_PATTERN = Pattern.compile(
            "(\\d{3,4}p)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ISO_DURATION_PATTERN = Pattern.compile(
            "PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?", Pattern.CASE_INSENSITIVE);
    private static final int DOCUMENT_CACHE_SIZE = 20;
    private static final long DOCUMENT_CACHE_TTL_MS = 120_000L;
    private static final Map<String, CachedDocument> DOCUMENT_CACHE =
            new LinkedHashMap<String, CachedDocument>(DOCUMENT_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        final Map.Entry<String, CachedDocument> eldest) {
                    return size() > DOCUMENT_CACHE_SIZE;
                }
            };

    private EightyFivePoParsingHelper() {
    }

    public static Map<String, List<String>> browserHeaders(final String referer) {
        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("User-Agent", Collections.singletonList(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"));
        headers.put("Accept", Collections.singletonList(
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"));
        headers.put("Accept-Language", Collections.singletonList("ja,en-US;q=0.8,en;q=0.6"));
        headers.put("Referer", Collections.singletonList(
                referer == null || referer.isEmpty() ? BASE_URL + LANG_PREFIX + "/" : referer));
        return headers;
    }

    public static Document fetchDocument(final String url) throws IOException, ExtractionException {
        return fetchDocument(url, BASE_URL + LANG_PREFIX + "/");
    }

    public static Document fetchDocument(final String url, final String referer)
            throws IOException, ExtractionException {
        final String normalizedUrl = normalizeUrl(url);
        final String normalizedReferer = referer == null || referer.isEmpty()
                ? BASE_URL + LANG_PREFIX + "/" : normalizeUrl(referer);
        final String cacheKey = normalizedUrl + "\n" + normalizedReferer;
        synchronized (DOCUMENT_CACHE) {
            final CachedDocument cached = DOCUMENT_CACHE.get(cacheKey);
            if (cached != null && System.currentTimeMillis() - cached.timestamp < DOCUMENT_CACHE_TTL_MS) {
                return cached.document;
            }
        }

        final Response response = NewPipe.getDownloader().get(normalizedUrl,
                browserHeaders(normalizedReferer));
        final Document document = Jsoup.parse(response.responseBody(), normalizedUrl);
        synchronized (DOCUMENT_CACHE) {
            DOCUMENT_CACHE.put(cacheKey, new CachedDocument(document));
        }
        return document;
    }

    public static String normalizeUrl(final String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        String normalized = decodeUrl(unescapeUrl(url.trim()));
        final int httpsIndex = normalized.indexOf("https://");
        final int httpIndex = normalized.indexOf("http://");
        final int firstHttpIndex;
        if (httpsIndex >= 0 && httpIndex >= 0) {
            firstHttpIndex = Math.min(httpsIndex, httpIndex);
        } else {
            firstHttpIndex = Math.max(httpsIndex, httpIndex);
        }
        if (firstHttpIndex > 0) {
            normalized = normalized.substring(firstHttpIndex);
        }
        normalized = normalized.replaceFirst("^https?://(?:www\\.)?85po\\.[^/]+", BASE_URL);
        if (normalized.startsWith("//")) {
            normalized = "https:" + normalized;
        }
        if (normalized.startsWith("/")) {
            normalized = BASE_URL + normalized;
        }
        if (!normalized.startsWith("http") && normalized.contains("/get_file/")) {
            normalized = BASE_URL + (normalized.startsWith("/") ? "" : "/") + normalized;
        }
        if (normalized.startsWith("http://www.85po.com/")) {
            normalized = "https://" + normalized.substring("http://".length());
        }
        return normalized;
    }

    public static String absoluteUrl(final String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        if (url.startsWith("//")) {
            return "https:" + url;
        }
        if (url.startsWith("/")) {
            return BASE_URL + url;
        }
        return normalizeUrl(url);
    }

    public static String videoUrlFromId(final String id) {
        return BASE_URL + LANG_PREFIX + "/v/" + id + "/";
    }

    public static List<EightyFivePoSearchResult> listVideos(final String url, final int count)
            throws IOException, ExtractionException {
        return extractVideoCards(fetchDocument(url), count);
    }

    public static List<EightyFivePoSearchResult> search(final String query, final int count)
            throws IOException, ExtractionException {
        return extractVideoCards(fetchDocument(BASE_URL + LANG_PREFIX + "/search/"
                + encodeQuery(query) + "/"), count);
    }

    public static List<EightyFivePoSearchResult> extractVideoCards(final Document document,
                                                                    final int count) {
        return extractVideoCardsFromLinks(document.select("a[href*=/v/]"), count);
    }

    public static List<EightyFivePoSearchResult> extractRecommendedVideoCards(
            final Document document,
            final int count) {
        return extractVideoCardsFromLinks(document.select(
                "#list_videos_recommended_videos_items a[href*=/v/], "
                        + "#list_videos_recommended_videos a[href*=/v/], "
                        + "section[id*=recommended] a[href*=/v/]"), count);
    }

    private static List<EightyFivePoSearchResult> extractVideoCardsFromLinks(
            final Iterable<Element> links,
            final int count) {
        final LinkedHashMap<String, EightyFivePoSearchResult> results = new LinkedHashMap<>();
        for (final Element link : links) {
            final String href = normalizeUrl(link.absUrl("href")).split("[?#]", 2)[0];
            final String id = extractIdOrEmpty(href);
            if (id.isEmpty() || results.containsKey(id)) {
                continue;
            }
            final Element card = findVideoCard(link);
            results.put(id, new EightyFivePoSearchResult(id, href,
                    extractCardTitle(link, card),
                    extractCardThumbnail(card),
                    extractCardDuration(card)));
            if (results.size() >= count) {
                break;
            }
        }
        return new ArrayList<>(results.values());
    }

    public static String extractId(final String url) throws ParsingException {
        final String id = extractIdOrEmpty(url);
        if (id.isEmpty()) {
            throw new ParsingException("Could not extract 85po id from URL: " + url);
        }
        return id;
    }

    public static String extractTitle(final Document document) {
        String title = attr(document.selectFirst("meta[property=og:title]"), "content");
        if (isBadTitle(title)) {
            title = text(document.selectFirst("h1.title, h1"));
        }
        if (isBadTitle(title)) {
            title = extractJsonString(attr(document.selectFirst("script[type=application/ld+json]"),
                    "html"), "name");
        }
        if (isBadTitle(title)) {
            title = extractFlashValue(document.html(), "video_title");
        }
        if (isBadTitle(title)) {
            title = document.title();
        }
        return isBadTitle(title) ? "" : title;
    }

    public static String extractDescription(final Document document) {
        String description = attr(document.selectFirst("script[type=application/ld+json]"),
                "html");
        description = extractJsonString(description, "description");
        if (description.isEmpty()) {
            description = attr(document.selectFirst("meta[property=og:description]"), "content");
        }
        if (description.isEmpty()) {
            description = attr(document.selectFirst("meta[name=description]"), "content");
        }
        return description;
    }

    public static String extractThumbnail(final Document document) {
        return absoluteUrl(attr(document.selectFirst("meta[property=og:image]"), "content"));
    }

    public static long extractDuration(final Document document) {
        final String seconds = attr(document.selectFirst("meta[property=video:duration]"), "content");
        try {
            if (!seconds.isEmpty()) {
                return Long.parseLong(seconds);
            }
        } catch (final NumberFormatException ignored) {
            // Try JSON-LD duration below.
        }
        final String jsonDuration = extractJsonString(
                attr(document.selectFirst("script[type=application/ld+json]"), "html"),
                "duration");
        final Matcher matcher = ISO_DURATION_PATTERN.matcher(jsonDuration);
        if (!matcher.matches()) {
            return -1;
        }
        return parseDurationPart(matcher.group(1)) * 3600
                + parseDurationPart(matcher.group(2)) * 60
                + parseDurationPart(matcher.group(3));
    }

    public static List<String> extractTags(final Document document) {
        final List<String> tags = new ArrayList<>();
        for (final Element tag : document.select("meta[property=video:tag], .tags-row a[href*=/tags/]")) {
            final String value = tag.hasAttr("content") ? tag.attr("content").trim() : text(tag);
            if (!value.isEmpty() && !tags.contains(value)) {
                tags.add(value);
            }
        }
        final String flashTags = extractFlashValue(document.html(), "video_tags");
        if (!flashTags.isEmpty()) {
            for (final String tag : flashTags.split(",")) {
                final String value = tag.trim();
                if (!value.isEmpty() && !tags.contains(value)) {
                    tags.add(value);
                }
            }
        }
        return tags;
    }

    public static String extractUploaderName(final Document document) {
        final String uploader = text(document.selectFirst("a[href*=/members/] em"));
        return uploader.isEmpty() ? "85po" : uploader;
    }

    public static String extractUploaderUrl(final Document document) {
        final Element uploader = document.selectFirst("a[href*=/members/]");
        return uploader == null ? BASE_URL + LANG_PREFIX + "/" : normalizeUrl(uploader.absUrl("href"));
    }

    public static String tryExtractVideoUrl(final Document document) {
        final List<EightyFivePoVideoSource> sources = extractVideoSources(document);
        return sources.isEmpty() ? "" : sources.get(0).url;
    }

    public static List<EightyFivePoVideoSource> extractVideoSources(final Document document) {
        final LinkedHashMap<String, EightyFivePoVideoSource> sources = new LinkedHashMap<>();
        putVideoSourcesFromHtml(sources, document.html());
        for (final Element downloadLink : document.select("a[href*=/get_file/]")) {
            final String url = unescapeUrl(downloadLink.absUrl("href"));
            if (isPlayableVideoUrl(url)) {
                putVideoSource(sources, url, "download",
                        guessResolution(url + " " + text(downloadLink), "MP4"));
            }
        }
        for (final Element media : document.select("video[src], source[src]")) {
            final String url = unescapeUrl(media.absUrl("src"));
            if (isPlayableVideoUrl(url)) {
                putVideoSource(sources, url, "media", guessResolution(url, "MP4"));
            }
        }
        return new ArrayList<>(sources.values());
    }

    public static List<EightyFivePoVideoSource> fetchVideoSources(final String url,
                                                                  final String referer)
            throws IOException, ExtractionException {
        final String normalizedUrl = normalizeUrl(url);
        final String normalizedReferer = referer == null || referer.isEmpty()
                ? BASE_URL + LANG_PREFIX + "/" : normalizeUrl(referer);
        final Response response = NewPipe.getDownloader().get(normalizedUrl,
                browserHeaders(normalizedReferer));
        final LinkedHashMap<String, EightyFivePoVideoSource> sources = new LinkedHashMap<>();
        putVideoSourcesFromHtml(sources, response.responseBody());
        final Document fetchedDocument = Jsoup.parse(response.responseBody(), normalizedUrl);
        for (final EightyFivePoVideoSource source : extractVideoSources(fetchedDocument)) {
            putVideoSource(sources, source.url, source.id, source.resolution);
        }
        return new ArrayList<>(sources.values());
    }

    public static List<EightyFivePoVideoSource> findVideoSources(final String pageUrl,
                                                                  final String id,
                                                                  final Document document)
            throws IOException, ExtractionException {
        List<EightyFivePoVideoSource> sources = extractVideoSources(document);
        if (!sources.isEmpty()) {
            return sources;
        }

        final List<String> candidates = new ArrayList<>();
        addCandidate(candidates, pageUrl);
        addCandidate(candidates, extractCanonicalVideoUrl(document, id));
        addCandidate(candidates, BASE_URL + LANG_PREFIX + "/v/" + id + "/");
        addCandidate(candidates, BASE_URL + "/v/" + id + "/");
        addCandidate(candidates, extractEmbedUrl(document, id));
        addCandidate(candidates, BASE_URL + LANG_PREFIX + "/embed/" + id);
        addCandidate(candidates, BASE_URL + "/embed/" + id);

        for (final String candidate : candidates) {
            try {
                final Document fetchedDocument = fetchDocument(candidate, pageUrl);
                final String canonicalUrl = extractCanonicalVideoUrl(fetchedDocument, id);
                if (!canonicalUrl.isEmpty() && !canonicalUrl.equals(candidate)) {
                    try {
                        sources = fetchVideoSources(canonicalUrl, pageUrl);
                        if (!sources.isEmpty()) {
                            return sources;
                        }
                    } catch (final IOException | ExtractionException ignored) {
                        // Continue with the current candidate below.
                    }
                }
                sources = extractVideoSources(fetchedDocument);
                if (!sources.isEmpty()) {
                    return sources;
                }
                sources = fetchVideoSources(candidate, pageUrl);
                if (!sources.isEmpty()) {
                    return sources;
                }
            } catch (final IOException | ExtractionException ignored) {
                // Try the next canonical/embed form before giving up.
            }
        }
        return Collections.emptyList();
    }

    public static List<EightyFivePoSearchResult> extractRelatedVideoCards(
            final Document document,
            final String id,
            final int count,
            final String referer) throws IOException, ExtractionException {
        final List<String> candidates = new ArrayList<>();
        addCandidate(candidates, extractFlashValue(document.html(), "related_src"));
        addCandidate(candidates, BASE_URL + LANG_PREFIX + "/related_videos_html/" + id + "/");
        addCandidate(candidates, BASE_URL + "/related_videos_html/" + id + "/");

        final String normalizedReferer = referer == null || referer.isEmpty()
                ? BASE_URL + LANG_PREFIX + "/" : normalizeUrl(referer);
        for (final String candidate : candidates) {
            try {
                final List<EightyFivePoSearchResult> relatedItems =
                        extractRecommendedVideoCards(fetchDocument(candidate, normalizedReferer), count);
                if (!relatedItems.isEmpty()) {
                    return relatedItems;
                }
                final List<EightyFivePoSearchResult> fallbackItems =
                        extractVideoCards(fetchDocument(candidate, normalizedReferer), count);
                if (!fallbackItems.isEmpty()) {
                    return fallbackItems;
                }
            } catch (final IOException | ExtractionException ignored) {
                // Keep trying the localized/default related endpoints.
            }
        }
        return Collections.emptyList();
    }

    private static void putVideoSourcesFromHtml(
            final LinkedHashMap<String, EightyFivePoVideoSource> sources,
            final String html) {
        if (html == null || html.isEmpty()) {
            return;
        }
        final Matcher matcher = VIDEO_URL_ENTRY_PATTERN.matcher(html);
        while (matcher.find()) {
            final String key = matcher.group(1);
            final String url = unescapeUrl(matcher.group(2));
            if (isFunctionWrappedVideoUrl(url)) {
                continue;
            }
            if (isPlayableVideoUrl(url)) {
                putVideoSource(sources, url, key, extractFlashValue(html, key + "_text"));
            }
        }
        final String unescapedHtml = unescapeUrl(html);
        final Matcher directMatcher = DIRECT_VIDEO_URL_PATTERN.matcher(unescapedHtml);
        while (directMatcher.find()) {
            final String url = unescapeUrl(directMatcher.group());
            if (isFunctionWrappedVideoUrl(url)
                    || isFunctionWrappedDirectUrl(unescapedHtml, directMatcher.start())) {
                continue;
            }
            if (isPlayableVideoUrl(url)) {
                putVideoSource(sources, url, "direct", guessResolution(url, "MP4"));
            }
        }
    }

    public static String extractEmbedUrl(final Document document, final String fallbackId) {
        final String json = attr(document.selectFirst("script[type=application/ld+json]"), "html");
        final String embedUrl = absoluteUrl(extractJsonString(json, "embedUrl"));
        return embedUrl.isEmpty() ? BASE_URL + LANG_PREFIX + "/embed/" + fallbackId : embedUrl;
    }

    public static String extractJapaneseVideoUrl(final Document document, final String id) {
        final String alternate = absoluteUrl(attr(document.selectFirst(
                "link[rel=alternate][hreflang=ja]"), "href"));
        if (!alternate.isEmpty() && alternate.contains("/ja/v/" + id + "/")) {
            return alternate;
        }
        final String canonical = absoluteUrl(attr(document.selectFirst("link[rel=canonical]"),
                "href"));
        if (!canonical.isEmpty() && canonical.contains("/ja/v/" + id + "/")) {
            return canonical;
        }
        final String ogUrl = absoluteUrl(attr(document.selectFirst("meta[property=og:url]"),
                "content"));
        if (!ogUrl.isEmpty() && ogUrl.contains("/ja/v/" + id + "/")) {
            return ogUrl;
        }
        return "";
    }

    public static String extractCanonicalVideoUrl(final Document document, final String id) {
        final String canonical = absoluteUrl(attr(document.selectFirst("link[rel=canonical]"),
                "href"));
        if (!canonical.isEmpty() && canonical.contains("/v/" + id + "/")) {
            return canonical;
        }
        final String ogUrl = absoluteUrl(attr(document.selectFirst("meta[property=og:url]"),
                "content"));
        if (!ogUrl.isEmpty() && ogUrl.contains("/v/" + id + "/")) {
            return ogUrl;
        }
        final Matcher matcher = VIDEO_PAGE_URL_PATTERN.matcher(unescapeUrl(document.html()));
        while (matcher.find()) {
            final String candidate = normalizeUrl(matcher.group()).split("[?#]", 2)[0];
            if (candidate.contains("/v/" + id + "/")) {
                return candidate;
            }
        }
        return "";
    }

    private static void addCandidate(final List<String> candidates, final String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        final String normalized = normalizeUrl(url);
        if (normalized != null && !normalized.isEmpty() && !candidates.contains(normalized)) {
            candidates.add(normalized);
        }
    }

    private static void putVideoSource(final LinkedHashMap<String, EightyFivePoVideoSource> sources,
                                       final String url,
                                       final String idPrefix,
                                       final String resolution) {
        final String markedUrl = appendMarker(normalizeUrl(url));
        final String key = markedUrl.replace("#85po=1", "")
                .replaceAll("([?&])download_filename=[^&#]*", "$1")
                .replaceAll("([?&])download=true", "$1")
                .replace("?&", "?")
                .replace("&&", "&");
        if (sources.containsKey(key)) {
            final EightyFivePoVideoSource existing = sources.get(key);
            if (existing != null && existing.resolution.equals("MP4")
                    && !guessResolution(resolution, "MP4").equals("MP4")) {
                sources.put(key, new EightyFivePoVideoSource(existing.id, existing.url,
                        guessResolution(resolution, "MP4")));
            }
            return;
        }
        sources.put(key, new EightyFivePoVideoSource(
                idPrefix + "-" + (sources.size() + 1),
                markedUrl,
                guessResolution(resolution + " " + url, "MP4")));
    }

    private static String extractFlashValue(final String html, final String key) {
        final Matcher matcher = Pattern.compile("[\"']?" + Pattern.quote(key)
                        + "[\"']?\\s*[:=]\\s*[\"']([^\"']+)[\"']",
                Pattern.CASE_INSENSITIVE).matcher(html);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String extractJsonString(final String json, final String key) {
        if (json == null || json.isEmpty()) {
            return "";
        }
        final Matcher matcher = Pattern.compile("\"" + Pattern.quote(key)
                + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").matcher(json);
        return matcher.find() ? unescapeUrl(matcher.group(1)) : "";
    }

    private static String appendMarker(final String url) {
        return url.contains(MARKER) ? url : url + MARKER;
    }

    private static boolean isPlayableVideoUrl(final String url) {
        final String normalizedUrl = normalizeUrl(url);
        final String cleanUrl = normalizedUrl == null
                ? ""
                : normalizedUrl.split("[?#]", 2)[0].toLowerCase(Locale.ROOT);
        return normalizedUrl != null
                && normalizedUrl.startsWith("http")
                && (cleanUrl.endsWith(".mp4") || cleanUrl.contains("/get_file/"));
    }

    private static boolean isFunctionWrappedVideoUrl(final String url) {
        return url != null && url.trim().toLowerCase(Locale.ROOT).startsWith("function/");
    }

    private static boolean isFunctionWrappedDirectUrl(final String html, final int start) {
        final int prefixStart = Math.max(0, start - 24);
        return html.substring(prefixStart, start).toLowerCase(Locale.ROOT).contains("function/");
    }

    private static boolean isBadTitle(final String title) {
        if (title == null || title.trim().isEmpty()) {
            return true;
        }
        final String normalized = title.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("not found") || normalized.contains("404 not found")
                || normalized.contains("sorry, you have been blocked")
                || normalized.contains("you have been blocked")
                || normalized.contains("attention required")
                || normalized.contains("cloudflare");
    }

    private static String guessResolution(final String value, final String fallback) {
        final Matcher matcher = QUALITY_LABEL_PATTERN.matcher(value == null ? "" : value);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase(Locale.ROOT);
        }
        return fallback == null || fallback.isEmpty() ? "MP4" : fallback;
    }

    private static String unescapeUrl(final String url) {
        return url.replace("\\/", "/")
                .replace("\\u002F", "/")
                .replace("\\u0026", "&")
                .replace("&amp;", "&");
    }

    private static String decodeUrl(final String url) {
        if (url == null || url.isEmpty()
                || !(url.contains("%2F") || url.contains("%2f") || url.contains("%3A")
                || url.contains("%3a") || url.contains("%26"))) {
            return url;
        }
        try {
            return URLDecoder.decode(url, StandardCharsets.UTF_8.name());
        } catch (final IllegalArgumentException | java.io.UnsupportedEncodingException ignored) {
            return url;
        }
    }

    public static String text(final Element element) {
        return element == null ? "" : element.text().trim();
    }

    public static String encodeQuery(final String query) {
        return URLEncoder.encode(query == null ? "" : query.trim(), StandardCharsets.UTF_8);
    }

    private static Element findVideoCard(final Element link) {
        Element current = link;
        for (int depth = 0; depth < 6 && current.parent() != null; depth++) {
            if (current.selectFirst("img") != null && current.selectFirst("a[href*=/v/]") != null) {
                return current;
            }
            current = current.parent();
        }
        return link;
    }

    private static String extractCardTitle(final Element link, final Element card) {
        if (link.hasAttr("title") && !link.attr("title").trim().isEmpty()) {
            return link.attr("title").trim();
        }
        final Element image = card.selectFirst("img[alt]");
        if (image != null && !image.attr("alt").trim().isEmpty()) {
            return image.attr("alt").trim();
        }
        final String title = text(card.selectFirst(".title, strong, h1, h2, h3"));
        return title.isEmpty() ? text(link) : title;
    }

    private static String extractCardThumbnail(final Element card) {
        final Element image = card.selectFirst("img[data-original], img[data-src], img[data-webp], img[src]");
        if (image == null) {
            return "";
        }
        for (final String attr : new String[] {"data-original", "data-src", "data-webp", "src"}) {
            final String url = image.absUrl(attr);
            if (!url.isEmpty()) {
                return absoluteUrl(url);
            }
        }
        return "";
    }

    private static long extractCardDuration(final Element card) {
        final String value = text(card.selectFirst(".time, .duration, .count-item"));
        if (value.isEmpty()) {
            return -1;
        }
        final Matcher matcher = Pattern.compile("(\\d{1,2}:\\d{2}(?::\\d{2})?)").matcher(value);
        if (!matcher.find()) {
            return -1;
        }
        final String[] parts = matcher.group(1).split(":");
        try {
            long seconds = 0;
            for (final String part : parts) {
                seconds = seconds * 60 + Long.parseLong(part.trim());
            }
            return seconds;
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    private static int parseDurationPart(final String value) {
        return value == null || value.isEmpty() ? 0 : Integer.parseInt(value);
    }

    private static String attr(final Element element, final String attr) {
        return element == null ? "" : element.attr(attr).trim();
    }

    private static String extractIdOrEmpty(final String url) {
        final Matcher matcher = Pattern.compile("/(?:[a-z]{2}/)?(?:v|embed)/(\\d+)(?:/|$)")
                .matcher(url);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static final class CachedDocument {
        private final Document document;
        private final long timestamp;

        private CachedDocument(final Document document) {
            this.document = document;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
