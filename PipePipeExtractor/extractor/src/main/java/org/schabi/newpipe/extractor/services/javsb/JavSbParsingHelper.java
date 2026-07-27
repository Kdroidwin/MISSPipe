package org.schabi.newpipe.extractor.services.javsb;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;

import java.io.IOException;
import java.math.BigInteger;
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

public final class JavSbParsingHelper {
    public static final String BASE_URL = "https://jav.sb";
    private static final String MARKER = "#javsb=1";
    private static final Pattern JAVSB_PLAYER_CODE_PATTERN = Pattern.compile(
            "[?&]src=([A-Za-z0-9]{16,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "\\\"token\\\"\\s*:\\s*\\\"([^\\\"]+)\\\".*?\\\"ts\\\"\\s*:\\s*(\\d+)",
            Pattern.DOTALL);
    private static final Pattern DIRECT_HLS_SOURCE_PATTERN = Pattern.compile(
            "\\\"source\\\"\\s*:\\s*\\\"((?:https?:)?//[^\\\"]+\\.m3u8(?:\\?[^\\\"]*)?)\\\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CACHE_HLS_FILE_PATTERN = Pattern.compile(
            "\\\"m3u8_file\\\"\\s*:\\s*\\\"([^\\\"]+\\.m3u8)\\\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QUALITY_LABEL_PATTERN =
            Pattern.compile("(\\d{3,4}p)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ISO_DURATION_PATTERN = Pattern.compile(
            "P(?:(\\d+)D)?T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DIRECT_VIDEO_URL_PATTERN = Pattern.compile(
            "(?:https?:)?//[^\"'\\s<>]+\\.(?:m3u8|mp4)(?:\\?[^\"'\\s<>]*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_FILE_PATTERN = Pattern.compile(
            "[\"']?(?:file|src)[\"']?\\s*:\\s*[\"']([^\"']+\\.(?:m3u8|mp4)(?:\\?[^\"']*)?)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PACKED_SCRIPT_PATTERN = Pattern.compile(
            "eval\\s*\\(\\s*function\\s*\\(\\s*p\\s*,\\s*a\\s*,\\s*c\\s*,\\s*k\\s*,\\s*e\\s*,\\s*d\\s*\\)"
                    + ".*?\\}\\s*\\(\\s*'((?:\\\\'|[^'])*)'\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*"
                    + "'((?:\\\\'|[^'])*)'\\s*\\.\\s*split\\s*\\(\\s*'\\|'\\s*\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATOB_PATTERN = Pattern.compile(
            "atob\\s*\\(\\s*[\"']([^\"']{20,})[\"']\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final int DOCUMENT_CACHE_SIZE = 20;
    private static final long DOCUMENT_CACHE_TTL_MS = 120_000L;
    private static final String BASE62_ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int STREAM_SIGNATURE_XOR_KEY = 0x7b;
    private static final Map<String, CachedDocument> DOCUMENT_CACHE =
            new LinkedHashMap<String, CachedDocument>(DOCUMENT_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        final Map.Entry<String, CachedDocument> eldest) {
                    return size() > DOCUMENT_CACHE_SIZE;
                }
            };

    private JavSbParsingHelper() {
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
            if (cached != null && System.currentTimeMillis() - cached.timestamp
                    < DOCUMENT_CACHE_TTL_MS) {
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
        String normalized = unescapeUrl(url.trim());
        if (normalized.startsWith("//")) {
            normalized = "https:" + normalized;
        }
        normalized = normalized.replaceFirst("^https?://(?:www\\.)?jav\\.sb(?::\\d+)?", BASE_URL);
        if (normalized.startsWith("/")) {
            normalized = BASE_URL + normalized;
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
        return BASE_URL + "/ja/jav/" + id + ".html";
    }

    public static List<JavSbSearchResult> listVideos(final String url, final int count)
            throws IOException, ExtractionException {
        return extractVideoCards(fetchDocument(url), count);
    }

    public static List<JavSbSearchResult> search(final String query, final int count)
            throws IOException, ExtractionException {
        return extractVideoCards(fetchDocument(BASE_URL + "/ja/vod/search.html?wd="
                + encodeQuery(query)), count);
    }

    public static List<JavSbSearchResult> extractVideoCards(final Document document,
                                                              final int count) {
        return extractVideoCardsFromLinks(document.select(".thumbnail a[href*=/jav/], "
                + "a[href*='/jav/'][href$='.html']"), count);
    }

    public static List<JavSbSearchResult> extractRelatedVideoCards(final Document document,
                                                                     final int count) {
        final List<JavSbSearchResult> related = extractVideoCardsFromLinks(document.select(
                "#tab-panels ~ div .thumbnail a[href*=/jav/], "
                        + ".thumbnail a[href*=/jav/]"), count);
        if (!related.isEmpty()) {
            return related;
        }
        return extractVideoCards(document, count);
    }

    public static String extractId(final String url) throws ParsingException {
        final String id = extractIdOrEmpty(url);
        if (id.isEmpty()) {
            throw new ParsingException("Could not extract JAVSB id from URL: " + url);
        }
        return id;
    }

    public static String extractTitle(final Document document) {
        String title = attr(document.selectFirst("meta[property=og:title]"), "content");
        if (title.isEmpty()) {
            title = attr(document.selectFirst("meta[itemprop=name]"), "content");
        }
        if (title.isEmpty()) {
            title = text(document.selectFirst("h1.entry-title, h1"));
        }
        return cleanText(title);
    }

    public static String extractDescription(final Document document) {
        String description = attr(document.selectFirst("meta[itemprop=description]"), "content");
        if (description.isEmpty()) {
            description = attr(document.selectFirst("meta[property=og:description]"), "content");
        }
        if (description.isEmpty()) {
            description = attr(document.selectFirst("meta[name=description]"), "content");
        }
        return cleanText(description);
    }

    public static String extractThumbnail(final Document document) {
        String thumbnail = attr(document.selectFirst("meta[itemprop=thumbnailUrl]"), "content");
        if (thumbnail.isEmpty()) {
            thumbnail = attr(document.selectFirst("meta[property=og:image]"), "content");
        }
        return absoluteUrl(thumbnail);
    }

    public static long extractDuration(final Document document) {
        long duration = parseIsoDuration(attr(document.selectFirst("meta[itemprop=duration]"),
                "content"));
        if (duration >= 0) {
            return duration;
        }
        duration = parseDuration(text(document.selectFirst(".duration, .text-secondary")));
        return duration >= 0 ? duration : parseDuration(document.text());
    }

    public static List<String> extractTags(final Document document) {
        final List<String> tags = new ArrayList<>();
        for (final Element tag : document.select("a[href*=/genres/], a[href*=/actresses/], "
                + "a[href*=/maker/], a[href*=/label/]")) {
            final String value = cleanText(tag.hasAttr("title") ? tag.attr("title") : text(tag));
            if (!value.isEmpty() && !tags.contains(value)) {
                tags.add(value);
            }
        }
        return tags;
    }

    public static List<JavSbVideoSource> findVideoSources(final Document pageDocument,
                                                            final String pageUrl)
            throws IOException, ExtractionException {
        final LinkedHashMap<String, JavSbVideoSource> sources = new LinkedHashMap<>();
        putVideoSourcesFromHtml(sources, pageDocument.html());
        if (!sources.isEmpty()) {
            return new ArrayList<>(sources.values());
        }
        final String playerUrl = absoluteUrl(attr(pageDocument.selectFirst(
                "#player iframe[src*='static/player/videojs.html']"), "src"));
        final Matcher codeMatcher = JAVSB_PLAYER_CODE_PATTERN.matcher(playerUrl);
        if (!codeMatcher.find()) {
            return Collections.emptyList();
        }
        final String fileCode = codeMatcher.group(1);
        final Map<String, List<String>> headers = browserHeaders(playerUrl);
        final Response playerResponse = NewPipe.getDownloader().get(playerUrl, headers);
        putVideoSourcesFromHtml(sources, playerResponse.responseBody());
        if (!sources.isEmpty()) {
            return new ArrayList<>(sources.values());
        }
        final Response tokenResponse = NewPipe.getDownloader().get(BASE_URL + "/token.php?file="
                + encodeQuery(fileCode), headers);
        final Matcher tokenMatcher = TOKEN_PATTERN.matcher(tokenResponse.responseBody());
        if (!tokenMatcher.find()) {
            throw new ParsingException("Could not obtain JAVSB stream token");
        }
        final String token = tokenMatcher.group(1);
        final String timestamp = tokenMatcher.group(2);
        final long currentSecond = System.currentTimeMillis() / 1000L;
        for (final long second : new long[] {currentSecond, currentSecond - 1, currentSecond + 1}) {
            final String signature = createStreamSignature(fileCode + "@" + second);
            final String cacheUrl = BASE_URL + "/save_m3u8_cache.php?file="
                    + encodeQuery(fileCode) + "&token=" + encodeQuery(token)
                    + "&ts=" + timestamp + "&sign=" + encodeQuery(signature);
            final Response manifestResponse = NewPipe.getDownloader().get(cacheUrl, headers);
            addCacheResponseSources(sources, manifestResponse.responseBody(), signature);
            if (!sources.isEmpty()) {
                break;
            }
        }
        return new ArrayList<>(sources.values());
    }

    static String createStreamSignature(final String value) {
        BigInteger encoded = BigInteger.ZERO;
        for (final byte valueByte : value.getBytes(StandardCharsets.UTF_8)) {
            encoded = encoded.shiftLeft(8).add(BigInteger.valueOf(
                    (valueByte & 0xff) ^ STREAM_SIGNATURE_XOR_KEY));
        }
        if (encoded.signum() == 0) {
            return String.valueOf(BASE62_ALPHABET.charAt(0));
        }
        final StringBuilder signature = new StringBuilder();
        final BigInteger radix = BigInteger.valueOf(BASE62_ALPHABET.length());
        while (encoded.signum() > 0) {
            final BigInteger[] quotientAndRemainder = encoded.divideAndRemainder(radix);
            signature.append(BASE62_ALPHABET.charAt(quotientAndRemainder[1].intValue()));
            encoded = quotientAndRemainder[0];
        }
        return signature.reverse().toString();
    }

    private static void addCacheResponseSources(
            final LinkedHashMap<String, JavSbVideoSource> sources,
            final String responseBody,
            final String signature) {
        final String body = unescapeUrl(responseBody);
        final Matcher directSourceMatcher = DIRECT_HLS_SOURCE_PATTERN.matcher(body);
        while (directSourceMatcher.find()) {
            putVideoSource(sources, directSourceMatcher.group(1));
        }
        final Matcher cacheFileMatcher = CACHE_HLS_FILE_PATTERN.matcher(body);
        while (cacheFileMatcher.find()) {
            final String cacheFile = absoluteUrl(cacheFileMatcher.group(1));
            putVideoSource(sources, cacheFile + (cacheFile.contains("?") ? "&" : "?")
                    + "sign=" + encodeQuery(signature));
        }
    }

    private static void putVideoSourcesFromHtml(
            final LinkedHashMap<String, JavSbVideoSource> sources,
            final String html) {
        if (html == null || html.isEmpty()) {
            return;
        }
        final String unescaped = unescapeUrl(html);
        collectUrlMatches(sources, PLAYER_FILE_PATTERN.matcher(unescaped));
        collectUrlMatches(sources, DIRECT_VIDEO_URL_PATTERN.matcher(unescaped));
        for (final String unpacked : unpackPackedScripts(unescaped)) {
            collectUrlMatches(sources, PLAYER_FILE_PATTERN.matcher(unpacked));
            collectUrlMatches(sources, DIRECT_VIDEO_URL_PATTERN.matcher(unpacked));
        }
        final Matcher atobMatcher = ATOB_PATTERN.matcher(unescaped);
        while (atobMatcher.find()) {
            try {
                putVideoSourcesFromHtml(sources, new String(java.util.Base64.getDecoder()
                        .decode(atobMatcher.group(1)), StandardCharsets.UTF_8));
            } catch (final IllegalArgumentException ignored) {
                // Continue scanning other player fragments.
            }
        }
    }

    private static List<String> unpackPackedScripts(final String html) {
        final List<String> unpackedScripts = new ArrayList<>();
        final Matcher matcher = PACKED_SCRIPT_PATTERN.matcher(html);
        while (matcher.find()) {
            final String payload = unescapeJsString(matcher.group(1));
            final int radix = parsePositiveInt(matcher.group(2));
            final int count = parsePositiveInt(matcher.group(3));
            final String[] words = unescapeJsString(matcher.group(4)).split("\\|", -1);
            if (payload.isEmpty() || radix < 2 || radix > 36 || count <= 0) {
                continue;
            }
            String unpacked = payload;
            for (int index = count - 1; index >= 0; index--) {
                if (index >= words.length || words[index] == null || words[index].isEmpty()) {
                    continue;
                }
                final String token = Integer.toString(index, radix);
                unpacked = unpacked.replaceAll("\\b" + Pattern.quote(token) + "\\b",
                        Matcher.quoteReplacement(words[index]));
            }
            unpackedScripts.add(unescapeUrl(unpacked));
        }
        return unpackedScripts;
    }

    private static int parsePositiveInt(final String value) {
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    private static String unescapeJsString(final String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        final StringBuilder builder = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (!escaped) {
                if (ch == '\\') {
                    escaped = true;
                } else {
                    builder.append(ch);
                }
                continue;
            }
            switch (ch) {
                case 'n':
                    builder.append('\n');
                    break;
                case 'r':
                    builder.append('\r');
                    break;
                case 't':
                    builder.append('\t');
                    break;
                case 'b':
                    builder.append('\b');
                    break;
                case 'f':
                    builder.append('\f');
                    break;
                case 'u':
                    if (i + 4 < value.length()) {
                        final String hex = value.substring(i + 1, i + 5);
                        try {
                            builder.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                            break;
                        } catch (final NumberFormatException ignored) {
                            builder.append("\\u");
                            break;
                        }
                    }
                    builder.append("\\u");
                    break;
                case 'x':
                    if (i + 2 < value.length()) {
                        final String hex = value.substring(i + 1, i + 3);
                        try {
                            builder.append((char) Integer.parseInt(hex, 16));
                            i += 2;
                            break;
                        } catch (final NumberFormatException ignored) {
                            builder.append("\\x");
                            break;
                        }
                    }
                    builder.append("\\x");
                    break;
                default:
                    builder.append(ch);
                    break;
            }
            escaped = false;
        }
        if (escaped) {
            builder.append('\\');
        }
        return builder.toString();
    }

    private static void collectUrlMatches(
            final LinkedHashMap<String, JavSbVideoSource> sources,
            final Matcher matcher) {
        while (matcher.find()) {
            final String url = matcher.groupCount() > 0 && matcher.group(1) != null
                    ? matcher.group(1) : matcher.group();
            if (isPlayableVideoUrl(url)) {
                putVideoSource(sources, url);
            }
        }
    }

    private static void putVideoSource(final LinkedHashMap<String, JavSbVideoSource> sources,
                                       final String rawUrl) {
        final String url = appendMarker(absoluteStreamUrl(rawUrl));
        final String key = url.replace(MARKER, "");
        if (sources.containsKey(key)) {
            return;
        }
        final boolean hls = key.toLowerCase(Locale.ROOT).contains(".m3u8");
        final String resolution = guessResolution(key, hls ? "HLS" : "MP4");
        sources.put(key, new JavSbVideoSource(
                (hls ? "hls-" : "mp4-") + (sources.size() + 1),
                url,
                resolution,
                hls ? DeliveryMethod.HLS : DeliveryMethod.PROGRESSIVE_HTTP));
    }

    private static List<JavSbSearchResult> extractVideoCardsFromLinks(
            final Iterable<Element> links,
            final int count) {
        final LinkedHashMap<String, JavSbSearchResult> results = new LinkedHashMap<>();
        for (final Element link : links) {
            final String href = normalizeUrl(link.absUrl("href")).split("[?#]", 2)[0];
            final String id = extractIdOrEmpty(href);
            if (id.isEmpty() || results.containsKey(id) || isArchiveIndexUrl(href)) {
                continue;
            }
            final Element card = findVideoCard(link);
            results.put(id, new JavSbSearchResult(id, href,
                    extractCardTitle(link, card),
                    extractCardThumbnail(card),
                    extractCardDuration(card)));
            if (results.size() >= count) {
                break;
            }
        }
        return new ArrayList<>(results.values());
    }

    private static Element findVideoCard(final Element link) {
        Element current = link;
        for (int depth = 0; depth < 7 && current.parent() != null; depth++) {
            if (current.selectFirst("img, [data-main-thumb]") != null
                    && current.selectFirst("a[href*=/jav/]") != null) {
                return current;
            }
            current = current.parent();
        }
        return link;
    }

    private static String extractCardTitle(final Element link, final Element card) {
        if (link.hasAttr("title") && !link.attr("title").trim().isEmpty()) {
            return cleanText(link.attr("title"));
        }
        final Element image = card.selectFirst("img[alt]");
        if (image != null && !image.attr("alt").trim().isEmpty()) {
            return cleanText(image.attr("alt"));
        }
        final String title = text(card.selectFirst(".my-2 a, .entry-header span, .title, h1, h2, h3"));
        return cleanText(title.isEmpty() ? text(link) : title);
    }

    private static String extractCardThumbnail(final Element card) {
        final String dataMainThumb = card.absUrl("data-main-thumb");
        if (!dataMainThumb.isEmpty()) {
            return absoluteUrl(dataMainThumb);
        }
        final Element image = card.selectFirst("img[data-src], img[data-original], img[src]");
        if (image == null) {
            return "";
        }
        for (final String attr : new String[] {"data-src", "data-original", "src"}) {
            final String url = image.absUrl(attr);
            if (!url.isEmpty()) {
                return absoluteUrl(url);
            }
        }
        return "";
    }

    private static long extractCardDuration(final Element card) {
        return parseDuration(text(card.selectFirst(".duration, .bottom-1.right-1")));
    }

    private static long parseDuration(final String value) {
        if (value == null || value.isEmpty()) {
            return -1;
        }
        final Matcher matcher = Pattern.compile("(\\d{1,2}:\\d{2}(?::\\d{2})?)").matcher(value);
        if (!matcher.find()) {
            return -1;
        }
        try {
            long seconds = 0;
            for (final String part : matcher.group(1).split(":")) {
                seconds = seconds * 60 + Long.parseLong(part.trim());
            }
            return seconds;
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    private static long parseIsoDuration(final String value) {
        final Matcher matcher = ISO_DURATION_PATTERN.matcher(value == null ? "" : value);
        if (!matcher.matches()) {
            return -1;
        }
        return parseDurationPart(matcher.group(1)) * 86_400L
                + parseDurationPart(matcher.group(2)) * 3_600L
                + parseDurationPart(matcher.group(3)) * 60L
                + parseDurationPart(matcher.group(4));
    }

    private static long parseDurationPart(final String value) {
        return value == null || value.isEmpty() ? 0 : Long.parseLong(value);
    }

    private static String extractIdOrEmpty(final String url) {
        final Matcher matcher = Pattern.compile("/(?:[a-z]{2}/)?jav/([^/?#]+)\\.html",
                Pattern.CASE_INSENSITIVE).matcher(normalizeUrl(url));
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).replaceAll("/+$", "");
    }

    private static boolean isArchiveIndexUrl(final String url) {
        return !url.contains("/jav/");
    }

    private static boolean isPlayableVideoUrl(final String url) {
        final String lower = absoluteStreamUrl(url).toLowerCase(Locale.ROOT);
        return lower.startsWith("http") && (lower.contains(".m3u8") || lower.contains(".mp4"));
    }

    private static String absoluteStreamUrl(final String url) {
        final String unescaped = unescapeUrl(url == null ? "" : url);
        if (unescaped.startsWith("//")) {
            return "https:" + unescaped;
        }
        return unescaped;
    }

    private static String appendMarker(final String url) {
        return url.contains(MARKER) ? url : url + MARKER;
    }

    private static String guessResolution(final String value, final String fallback) {
        final Matcher matcher = QUALITY_LABEL_PATTERN.matcher(value == null ? "" : value);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase(Locale.ROOT);
        }
        return fallback;
    }


    public static String text(final Element element) {
        return element == null ? "" : cleanText(element.text());
    }

    public static String encodeQuery(final String query) {
        try {
            return URLEncoder.encode(query == null ? "" : query.trim(), StandardCharsets.UTF_8.name());
        } catch (final java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 must be available", e);
        }
    }

    private static String cleanText(final String value) {
        return decodeHtml(value == null ? "" : value)
                .replace('\u00a0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String decodeHtml(final String value) {
        return Jsoup.parse(value).text();
    }

    private static String unescapeUrl(final String url) {
        return url.replace("\\/", "/")
                .replace("\\u002F", "/")
                .replace("\\u0026", "&")
                .replace("&amp;", "&");
    }

    private static String attr(final Element element, final String attr) {
        return element == null ? "" : element.attr(attr).trim();
    }

    static String decodePathForDisplay(final String id) {
        try {
            return URLDecoder.decode(id, StandardCharsets.UTF_8.name());
        } catch (final IllegalArgumentException | java.io.UnsupportedEncodingException ignored) {
            return id;
        }
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

