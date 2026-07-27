package org.schabi.newpipe.extractor.services.tokyomotion;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;

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

public final class TokyoMotionParsingHelper {
    public static final String BASE_URL = "https://www.tokyomotion.net";
    private static final String STREAM_MARKER = "#tokyomotion=1";
    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile(
            "/(?:video|embed)/(\\d+)(?:[/?#]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(\\d{1,2}:\\d{2}(?::\\d{2})?)");

    private TokyoMotionParsingHelper() {
    }

    public static Map<String, List<String>> browserHeaders(final String referer) {
        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("User-Agent", Collections.singletonList(
                "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/138.0.0.0 Mobile Safari/537.36"));
        headers.put("Accept", Collections.singletonList(
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"));
        headers.put("Accept-Language", Collections.singletonList("ja-JP,ja;q=0.9,en-US;q=0.8"));
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
        final Response response = NewPipe.getDownloader().get(normalizedUrl,
                browserHeaders(referer));
        return Jsoup.parse(response.responseBody(), normalizedUrl);
    }

    public static String normalizeUrl(final String url) {
        if (url == null || url.trim().isEmpty()) {
            return "";
        }
        String normalized = url.trim().replace("&amp;", "&");
        if (normalized.startsWith("//")) {
            normalized = "https:" + normalized;
        }
        normalized = normalized.replaceFirst("^https?://(?:www\\.)?tokyomotion\\.net(?::\\d+)?",
                BASE_URL);
        return normalized.startsWith("/") ? BASE_URL + normalized : normalized;
    }

    public static String videoUrlFromId(final String id) {
        return BASE_URL + "/video/" + id;
    }

    public static String extractId(final String url) throws ParsingException {
        final Matcher matcher = VIDEO_ID_PATTERN.matcher(normalizeUrl(url));
        if (!matcher.find()) {
            throw new ParsingException("Could not extract TOKYO Motion id from URL: " + url);
        }
        return matcher.group(1);
    }

    public static List<TokyoMotionSearchResult> search(final String query, final int count)
            throws IOException, ExtractionException {
        return extractVideoCards(fetchDocument(BASE_URL + "/search?search_query="
                + encodeQuery(query) + "&search_type=videos"), count);
    }

    public static List<TokyoMotionSearchResult> extractVideoCards(final Element document,
                                                                    final int count) {
        final LinkedHashMap<String, TokyoMotionSearchResult> results = new LinkedHashMap<>();
        for (final Element link : document.select("a.thumb-popu[href*='/video/'], "
                + "a[href*='/video/']")) {
            final String url = normalizeUrl(link.absUrl("href")).split("[?#]", 2)[0];
            final String id = extractIdOrEmpty(url);
            if (id.isEmpty() || results.containsKey(id)) {
                continue;
            }
            final Element card = findCard(link);
            final String title = firstNonEmpty(text(card.selectFirst(".video-title")),
                    attr(card.selectFirst("img[alt]"), "alt"), text(link));
            final Element image = card.selectFirst("img[src], img[data-src]");
            final String thumbnail = image == null ? "" : firstNonEmpty(
                    normalizeUrl(image.absUrl("data-src")), normalizeUrl(image.absUrl("src")));
            results.put(id, new TokyoMotionSearchResult(id, url, title, thumbnail,
                    parseDuration(text(card.selectFirst(".duration"))),
                    extractUploaderName(card), extractUploaderUrl(card)));
            if (results.size() >= count) {
                break;
            }
        }
        return new ArrayList<>(results.values());
    }

    public static List<TokyoMotionSearchResult> extractRelatedVideoCards(final Document document,
                                                                           final int count) {
        final Element related = document.selectFirst("#related_videos");
        return related == null ? Collections.emptyList() : extractVideoCards(related, count);
    }

    public static String extractUploaderName(final Document document) {
        return extractUploaderName((Element) document);
    }

    public static String extractUploaderUrl(final Document document) {
        return extractUploaderUrl((Element) document);
    }

    public static String extractChannelName(final Document document, final String fallback) {
        final Element profileLink = document.selectFirst(
                ".panel-heading a[href^=/user/], a[href^=/user/]");
        if (profileLink == null) {
            return fallback;
        }
        final String profileUrl = normalizeUrl(profileLink.absUrl("href"));
        final int userIndex = profileUrl.lastIndexOf("/user/");
        if (userIndex >= 0) {
            final String userName = profileUrl.substring(userIndex + "/user/".length())
                    .split("[?#/]", 2)[0];
            if (!userName.isEmpty()) {
                return userName;
            }
        }
        return firstNonEmpty(text(profileLink), fallback);
    }

    public static String extractChannelAvatarUrl(final Document document) {
        final Element avatar = document.selectFirst(
                ".panel.panel-default a[href^=/user/] img[src], a[href^=/user/] img[src]");
        return avatar == null ? "" : normalizeUrl(avatar.absUrl("src"));
    }

    public static String extractChannelDescription(final Document document) {
        return text(document.selectFirst("#info-container .text-white, #info-container"));
    }

    public static String extractTitle(final Document document) {
        return firstNonEmpty(attr(document.selectFirst("meta[property=og:title]"), "content"),
                text(document.selectFirst("h1, h3.big-title-truncate, h4.big-title-truncate")));
    }

    public static String extractDescription(final Document document) {
        return firstNonEmpty(attr(document.selectFirst("meta[property=og:description]"), "content"),
                attr(document.selectFirst("meta[name=description]"), "content"));
    }

    public static String extractThumbnail(final Document document) {
        return normalizeUrl(attr(document.selectFirst("meta[property=og:image]"), "content"));
    }

    public static long extractDuration(final Document document) {
        final String duration = attr(document.selectFirst("meta[property='video:duration']"), "content");
        try {
            return Math.round(Double.parseDouble(duration));
        } catch (final NumberFormatException ignored) {
            return parseDuration(document.text());
        }
    }

    public static List<String> extractTags(final Document document) {
        final List<String> tags = new ArrayList<>();
        for (final Element tag : document.select("meta[property='video:tag']")) {
            final String value = attr(tag, "content");
            if (!value.isEmpty() && !tags.contains(value)) {
                tags.add(value);
            }
        }
        return tags;
    }

    public static List<TokyoMotionVideoSource> findVideoSources(final Document document,
                                                                  final String pageUrl) {
        final LinkedHashMap<String, TokyoMotionVideoSource> sources = new LinkedHashMap<>();
        for (final Element source : document.select("#vjsplayer source[src], video source[src]")) {
            final String url = normalizeUrl(source.absUrl("src"));
            if (!url.isEmpty()) {
                final String label = firstNonEmpty(attr(source, "title"), attr(source, "label"), "MP4");
                sources.put(url, new TokyoMotionVideoSource("mp4-" + (sources.size() + 1),
                        url + STREAM_MARKER, label.toUpperCase(Locale.ROOT),
                        DeliveryMethod.PROGRESSIVE_HTTP));
            }
        }
        return new ArrayList<>(sources.values());
    }

    /**
     * Resolves TOKYO Motion's redirecting player endpoints before handing them to Media3.
     *
     * <p>The site generates a new CDN URL for every request to {@code /vsrc/...}. Keeping the
     * redirect endpoint as the media URI means that a seek or reconnect can combine byte ranges
     * from different generated resources. A one-byte ranged GET pins each selected source to its
     * current CDN URL and verifies the response is video data.</p>
     */
    public static List<TokyoMotionVideoSource> resolveVideoSources(final Downloader downloader,
                                                                     final List<TokyoMotionVideoSource> sources,
                                                                     final String pageUrl)
            throws IOException, ExtractionException {
        final LinkedHashMap<String, TokyoMotionVideoSource> resolvedSources = new LinkedHashMap<>();
        for (final TokyoMotionVideoSource source : sources) {
            final String sourceUrl = source.url.replace(STREAM_MARKER, "");
            try {
                final Response response = downloader.get(sourceUrl, mediaHeaders(pageUrl));
                final String latestUrl = response.latestUrl();
                final String contentType = response.getHeader("Content-Type");
                if ((response.responseCode() == 200 || response.responseCode() == 206)
                        && contentType != null
                        && contentType.toLowerCase(Locale.ROOT).startsWith("video/")
                        && hasIsoBmffSignature(response.rawResponseBody())
                        && isTokyoMotionMediaUrl(latestUrl)) {
                    resolvedSources.put(latestUrl, new TokyoMotionVideoSource(source.id,
                            latestUrl + STREAM_MARKER, source.resolution, source.deliveryMethod));
                }
            } catch (final IOException | ExtractionException ignored) {
                // The unresolved source is kept only when every candidate fails verification.
            }
        }
        return new ArrayList<>(resolvedSources.values());
    }

    private static Map<String, List<String>> mediaHeaders(final String referer) {
        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("User-Agent", Collections.singletonList(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:152.0) Gecko/20100101 Firefox/152.0"));
        headers.put("Accept", Collections.singletonList(
                "video/webm,video/ogg,video/*;q=0.9,application/ogg;q=0.7,audio/*;q=0.6,*/*;q=0.5"));
        headers.put("Accept-Encoding", Collections.singletonList("identity"));
        headers.put("Range", Collections.singletonList("bytes=0-4095"));
        headers.put("Referer", Collections.singletonList(
                referer == null || referer.isEmpty() ? BASE_URL + "/" : referer));
        return headers;
    }

    private static boolean isTokyoMotionMediaUrl(final String url) {
        if (url == null || !url.startsWith("https://")) {
            return false;
        }
        final String hostAndPath = url.substring("https://".length()).toLowerCase(Locale.ROOT);
        final int pathIndex = hostAndPath.indexOf('/');
        final String host = pathIndex < 0 ? hostAndPath : hostAndPath.substring(0, pathIndex);
        return (host.equals("tokyomotion.net") || host.endsWith(".tokyomotion.net"))
                && pathIndex >= 0 && hostAndPath.substring(pathIndex).contains("/video/");
    }

    private static boolean hasIsoBmffSignature(final byte[] bytes) {
        if (bytes == null || bytes.length < 8) {
            return false;
        }
        final int searchLimit = Math.min(bytes.length - 3, 64);
        for (int offset = 4; offset < searchLimit; offset++) {
            if (bytes[offset] == 'f' && bytes[offset + 1] == 't'
                    && bytes[offset + 2] == 'y' && bytes[offset + 3] == 'p') {
                return true;
            }
        }
        return false;
    }

    public static String encodeQuery(final String query) {
        try {
            return URLEncoder.encode(query == null ? "" : query.trim(), StandardCharsets.UTF_8.name());
        } catch (final java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 must be available", e);
        }
    }

    static String decodePathForDisplay(final String id) {
        try {
            return URLDecoder.decode(id, StandardCharsets.UTF_8.name());
        } catch (final IllegalArgumentException | java.io.UnsupportedEncodingException ignored) {
            return id;
        }
    }

    private static Element findCard(final Element link) {
        Element current = link;
        for (int depth = 0; depth < 5 && current.parent() != null; depth++) {
            if (current.hasClass("well") || current.hasClass("video-item")
                    || current.hasClass("thumb-overlay") || current.id().startsWith("video_")) {
                return current;
            }
            current = current.parent();
        }
        return link;
    }

    private static String extractUploaderName(final Element card) {
        final Element uploader = card.selectFirst(".user-container a[href^=/user/], a[href^=/user/]");
        return firstNonEmpty(text(uploader), "TOKYO Motion");
    }

    private static String extractUploaderUrl(final Element card) {
        final Element uploader = card.selectFirst(".user-container a[href^=/user/], a[href^=/user/]");
        return uploader == null ? BASE_URL + "/" : normalizeUrl(uploader.absUrl("href"));
    }

    private static String extractIdOrEmpty(final String url) {
        try {
            return extractId(url);
        } catch (final ParsingException ignored) {
            return "";
        }
    }

    private static long parseDuration(final String value) {
        final Matcher matcher = DURATION_PATTERN.matcher(value == null ? "" : value);
        if (!matcher.find()) {
            return -1;
        }
        long seconds = 0;
        for (final String part : matcher.group(1).split(":")) {
            seconds = seconds * 60 + Long.parseLong(part);
        }
        return seconds;
    }

    private static String text(final Element element) {
        return element == null ? "" : cleanText(element.text());
    }

    private static String attr(final Element element, final String name) {
        return element == null ? "" : cleanText(element.attr(name));
    }

    private static String firstNonEmpty(final String... values) {
        for (final String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return cleanText(value);
            }
        }
        return "";
    }

    private static String cleanText(final String value) {
        return Jsoup.parse(value == null ? "" : value).text().replaceAll("\\s+", " ").trim();
    }
}
