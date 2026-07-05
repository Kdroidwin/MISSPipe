package org.schabi.newpipe.extractor.services.kissjav;

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

public final class KissJavParsingHelper {
    public static final String BASE_URL = "https://kissjav.li";

    private static final Pattern[] VIDEO_URL_VALUE_PATTERNS = new Pattern[] {
            Pattern.compile(
                    "[\"']?video_(?:alt_)?url(?:_hd)?[\"']?\\s*[:=]\\s*[\"']([^\"']+)[\"']",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile(
                    "flashvars\\s*\\[\\s*[\"']video_(?:alt_)?url(?:_hd)?[\"']\\s*]"
                            + "\\s*=\\s*[\"']([^\"']+)[\"']",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile(
                    "atob\\s*\\(\\s*[\"']([^\"']{20,})[\"']\\s*\\)",
                    Pattern.CASE_INSENSITIVE)
    };
    private static final Pattern DIRECT_VIDEO_URL_PATTERN = Pattern.compile(
            "https?://[^\"'\\s<>]+(?:get_file|contents/videos_sources)[^\"'\\s<>]+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VIDEO_URL_ENTRY_PATTERN = Pattern.compile(
            "[\"']?(video_(?:alt_)?url(?:_hd)?)[\"']?\\s*[:=]\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FLASHVARS_VIDEO_URL_ENTRY_PATTERN = Pattern.compile(
            "flashvars\\s*\\[\\s*[\"'](video_(?:alt_)?url(?:_hd)?)[\"']\\s*]"
                    + "\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DOWNLOAD_TOKEN_FILE_PATTERN = Pattern.compile(
            "download_token\\.php\\?file=([^\"'&<>]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QUALITY_LABEL_PATTERN = Pattern.compile(
            "(\\d{3,4}p)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMBED_URL_PATTERN = Pattern.compile(
            "\"embedUrl\"\\s*:\\s*\"([^\"]+)\"|<iframe[^>]+src=[\"']([^\"']*/embed/\\d+[^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE);
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

    private KissJavParsingHelper() {
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
                referer == null || referer.isEmpty() ? BASE_URL + "/" : referer));
        return headers;
    }

    public static Document fetchDocument(final String url) throws IOException, ExtractionException {
        return fetchDocument(url, BASE_URL + "/");
    }

    public static Document fetchDocument(final String url, final String referer)
            throws IOException, ExtractionException {
        final String normalizedUrl = normalizeUrl(url);
        final String normalizedReferer = referer == null || referer.isEmpty()
                ? BASE_URL + "/" : normalizeUrl(referer);
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
        String normalized = url.replaceFirst("^https?://(?:www\\.)?kissjav\\.[^/]+", BASE_URL);
        if (normalized.startsWith("//")) {
            normalized = "https:" + normalized;
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

    public static List<KissJavSearchResult> listVideos(final String url, final int count)
            throws IOException, ExtractionException {
        return extractVideoCards(fetchDocument(url), count);
    }

    public static List<KissJavSearchResult> search(final String query, final int count)
            throws IOException, ExtractionException {
        return extractVideoCards(fetchDocument(BASE_URL + "/search/"
                + encodeQuery(query) + "/"), count);
    }

    public static List<KissJavSearchResult> extractVideoCards(final Document document,
                                                              final int count) {
        final LinkedHashMap<String, KissJavSearchResult> results = new LinkedHashMap<>();
        for (final Element link : document.select("a[href*=/video/]")) {
            final String href = normalizeUrl(link.absUrl("href")).split("[?#]", 2)[0];
            final String id = extractIdOrEmpty(href);
            if (id.isEmpty() || results.containsKey(id)) {
                continue;
            }
            final Element card = findVideoCard(link);
            results.put(id, new KissJavSearchResult(id, href,
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
            throw new ParsingException("Could not extract KissJAV id from URL: " + url);
        }
        return id;
    }

    public static String extractTitle(final Document document) {
        String title = attr(document.selectFirst("meta[property=og:title]"), "content");
        if (title.isEmpty()) {
            title = text(document.selectFirst("h1.title, h1"));
        }
        return title;
    }

    public static String extractDescription(final Document document) {
        String description = attr(document.selectFirst("meta[property=og:description]"), "content");
        if (description.isEmpty()) {
            description = attr(document.selectFirst("meta[name=description]"), "content");
        }
        return description;
    }

    public static String extractThumbnail(final Document document) {
        return absoluteUrl(attr(document.selectFirst("meta[property=og:image]"), "content"));
    }

    public static long extractDuration(final Document document) {
        final String duration = attr(document.selectFirst("meta[property=video:duration]"), "content");
        try {
            return duration.isEmpty() ? -1 : Long.parseLong(duration);
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    public static List<String> extractTags(final Document document) {
        final List<String> tags = new ArrayList<>();
        for (final Element tag : document.select("meta[property=video:tag], .info a[href*=/categories/]")) {
            final String value = tag.hasAttr("content") ? tag.attr("content").trim() : text(tag);
            if (!value.isEmpty() && !tags.contains(value)) {
                tags.add(value);
            }
        }
        return tags;
    }

    public static String extractVideoUrl(final Document document) throws ParsingException {
        final String videoUrl = tryExtractVideoUrl(document);
        if (!videoUrl.isEmpty()) {
            return videoUrl;
        }
        throw new ParsingException("Could not find KissJAV video URL");
    }

    public static String tryExtractVideoUrl(final Document document) {
        final List<KissJavVideoSource> sources = extractVideoSources(document);
        return sources.isEmpty() ? "" : sources.get(0).url;
    }

    public static List<KissJavVideoSource> extractVideoSources(final Document document) {
        final LinkedHashMap<String, KissJavVideoSource> sources = new LinkedHashMap<>();
        final String html = document.html();
        final String normalizedHtml = unescapeUrl(html);
        collectPlayerVideoSources(sources, VIDEO_URL_ENTRY_PATTERN.matcher(html));
        collectPlayerVideoSources(sources, FLASHVARS_VIDEO_URL_ENTRY_PATTERN.matcher(html));

        for (final Pattern pattern : VIDEO_URL_VALUE_PATTERNS) {
            final Matcher matcher = pattern.matcher(html);
            while (matcher.find()) {
                final String value = matcher.group(1);
                if ("MQ==".equals(value) || value.isEmpty()) {
                    continue;
                }
                final String decoded = decodeVideoUrlValue(value);
                if (isPlayableVideoUrl(decoded)) {
                    putVideoSource(sources, decoded, "mp4", "MP4");
                }
            }
        }

        final Matcher directMatcher = DIRECT_VIDEO_URL_PATTERN.matcher(normalizedHtml);
        while (directMatcher.find()) {
            putVideoSource(sources, unescapeUrl(directMatcher.group()), "direct", "MP4");
        }

        final Matcher downloadMatcher = DOWNLOAD_TOKEN_FILE_PATTERN.matcher(normalizedHtml);
        while (downloadMatcher.find()) {
            try {
                final String decoded = URLDecoder.decode(downloadMatcher.group(1),
                        StandardCharsets.UTF_8.name());
                if (isPlayableVideoUrl(decoded)) {
                    putVideoSource(sources, decoded, "download", guessResolution(decoded, ""));
                }
            } catch (final IllegalArgumentException | IOException ignored) {
                // Keep falling through to the empty result.
            }
        }

        for (final Element downloadLink : document.select("a[href*=/download_token.php]")) {
            final String href = unescapeUrl(downloadLink.absUrl("href"));
            final Matcher fileMatcher = DOWNLOAD_TOKEN_FILE_PATTERN.matcher(href);
            if (!fileMatcher.find()) {
                continue;
            }
            try {
                final String decoded = URLDecoder.decode(fileMatcher.group(1),
                        StandardCharsets.UTF_8.name());
                if (isPlayableVideoUrl(decoded)) {
                    final String label = guessResolution(href + " " + text(downloadLink),
                            "MP4");
                    putVideoSource(sources, decoded, "download", label);
                }
            } catch (final IllegalArgumentException | IOException ignored) {
                // Ignore a single malformed download link and keep other stream candidates.
            }
        }
        return new ArrayList<>(sources.values());
    }

    public static String extractEmbedUrl(final Document document, final String fallbackId) {
        final Matcher matcher = EMBED_URL_PATTERN.matcher(document.html());
        if (matcher.find()) {
            final String jsonUrl = matcher.group(1);
            final String iframeUrl = matcher.group(2);
            return absoluteUrl(jsonUrl == null || jsonUrl.isEmpty() ? iframeUrl : jsonUrl);
        }
        return BASE_URL + "/embed/" + fallbackId;
    }

    public static String extractCanonicalVideoUrl(final Document document, final String id) {
        final String canonical = absoluteUrl(attr(document.selectFirst("link[rel=canonical]"),
                "href"));
        if (!canonical.isEmpty() && canonical.contains("/video/" + id + "/")) {
            return canonical;
        }
        final String ogUrl = absoluteUrl(attr(document.selectFirst("meta[property=og:url]"),
                "content"));
        if (!ogUrl.isEmpty() && ogUrl.contains("/video/" + id + "/")) {
            return ogUrl;
        }
        return "";
    }

    private static void collectPlayerVideoSources(
            final LinkedHashMap<String, KissJavVideoSource> sources,
            final Matcher matcher) {
        while (matcher.find()) {
            final String key = matcher.group(1);
            final String value = matcher.group(2);
            if ("MQ==".equals(value) || value.isEmpty()) {
                continue;
            }
            final String decoded = decodeVideoUrlValue(value);
            if (isPlayableVideoUrl(decoded)) {
                putVideoSource(sources, decoded, key,
                        key != null && key.toLowerCase(Locale.ROOT).contains("_hd")
                                ? "HD MP4" : guessResolution(decoded, "MP4"));
            }
        }
    }

    private static void putVideoSource(final LinkedHashMap<String, KissJavVideoSource> sources,
                                       final String url,
                                       final String idPrefix,
                                       final String resolution) {
        final String markedUrl = appendKissJavMarker(unescapeUrl(url));
        final String key = canonicalStreamKey(markedUrl);
        if (sources.containsKey(key)) {
            final KissJavVideoSource existing = sources.get(key);
            if (existing != null && existing.resolution.equals("MP4")
                    && !resolution.equals("MP4")) {
                sources.put(key, new KissJavVideoSource(existing.id, existing.url, resolution));
            }
            return;
        }
        sources.put(key, new KissJavVideoSource(
                idPrefix + "-" + (sources.size() + 1),
                markedUrl,
                resolution.isEmpty() ? "MP4" : resolution));
    }

    private static String canonicalStreamKey(final String url) {
        return url.replace("#kissjav=1", "")
                .replaceAll("([?&])download_filename=[^&#]*", "$1")
                .replaceAll("([?&])download=true", "$1")
                .replace("?&", "?")
                .replace("&&", "&");
    }

    private static String guessResolution(final String value, final String fallback) {
        final Matcher matcher = QUALITY_LABEL_PATTERN.matcher(value == null ? "" : value);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase(Locale.ROOT);
        }
        return fallback == null || fallback.isEmpty() ? "MP4" : fallback;
    }

    private static String decodeVideoUrlValue(final String value) {
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return unescapeUrl(value);
        }
        try {
            return unescapeUrl(new String(java.util.Base64.getDecoder().decode(value),
                    StandardCharsets.UTF_8));
        } catch (final IllegalArgumentException ignored) {
            return "";
        }
    }

    private static boolean isPlayableVideoUrl(final String url) {
        return url != null
                && (url.startsWith("http://") || url.startsWith("https://"))
                && (url.contains(".mp4")
                || url.contains("/get_file/")
                || url.contains("/contents/videos_sources/"));
    }

    private static String appendKissJavMarker(final String url) {
        return url.contains("#kissjav=1") ? url : url + "#kissjav=1";
    }

    private static String unescapeUrl(final String url) {
        return url.replace("\\/", "/")
                .replace("\\u002F", "/")
                .replace("\\u0026", "&")
                .replace("&amp;", "&");
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
            if (current.selectFirst("img") != null
                    && current.selectFirst("a[href*=/video/]") != null) {
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
        final String dataOriginal = image.absUrl("data-original");
        if (!dataOriginal.isEmpty()) {
            return absoluteUrl(dataOriginal);
        }
        final String dataSrc = image.absUrl("data-src");
        if (!dataSrc.isEmpty()) {
            return absoluteUrl(dataSrc);
        }
        final String dataWebp = image.absUrl("data-webp");
        if (!dataWebp.isEmpty()) {
            return absoluteUrl(dataWebp);
        }
        return absoluteUrl(image.absUrl("src"));
    }

    private static long extractCardDuration(final Element card) {
        final String value = text(card.selectFirst(".time, .duration, .thumb-bottom .time"));
        if (value.isEmpty()) {
            return -1;
        }
        final String[] parts = value.split(":");
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

    private static String attr(final Element element, final String attr) {
        return element == null ? "" : element.attr(attr).trim();
    }

    private static String extractIdOrEmpty(final String url) {
        final Matcher matcher = Pattern.compile("/(?:video|embed)/(\\d+)(?:/|$)")
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
