package org.schabi.newpipe.extractor.services.pornhub;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.NewPipe;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PornhubParsingHelper {
    public static final String BASE_URL = "https://jp.pornhub.com";
    public static final String MARKER = "#pornhub=1";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36";
    private static final Pattern VIEWKEY_PATTERN =
            Pattern.compile("[?&]viewkey=([^&#/]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FLASHVARS_PATTERN =
            Pattern.compile("var\\s+flashvars_\\d+\\s*=\\s*(\\{.*?\\});\\s*\\n",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern MEDIA_DEFINITIONS_PATTERN =
            Pattern.compile("\"mediaDefinitions\"\\s*:\\s*\\[(.*?)]\\s*,\\s*\"isVertical\"",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern MEDIA_OBJECT_PATTERN =
            Pattern.compile("\\{([^{}]*\"videoUrl\"[^{}]*)\\}",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern RELATED_VIDEOS_PATTERN =
            Pattern.compile("relatedVideos\\s*=\\s*(\\[.*?]);", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern NEXT_VIDEO_PATTERN =
            Pattern.compile("\"nextVideo\"\\s*:\\s*\\{(.*?)\\},\\s*\"playbackTracking\"",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern JSON_STRING_PATTERN_TEMPLATE = Pattern.compile("");
    private static final Pattern JSON_NUMBER_PATTERN_TEMPLATE = Pattern.compile("");
    private static final Pattern ISO_DURATION_PATTERN = Pattern.compile(
            "PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION_PATTERN =
            Pattern.compile("(\\d{1,2}:\\d{2}(?::\\d{2})?)");
    private static final int DOCUMENT_CACHE_SIZE = 16;
    private static final long DOCUMENT_CACHE_TTL_MS = 90_000L;
    private static final Map<String, CachedDocument> DOCUMENT_CACHE =
            new LinkedHashMap<String, CachedDocument>(DOCUMENT_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        final Map.Entry<String, CachedDocument> eldest) {
                    return size() > DOCUMENT_CACHE_SIZE;
                }
            };

    private PornhubParsingHelper() {
    }

    public static Map<String, List<String>> browserHeaders(final String referer) {
        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("User-Agent", Collections.singletonList(USER_AGENT));
        headers.put("Accept", Collections.singletonList(
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"));
        headers.put("Accept-Language", Collections.singletonList("ja,en-US;q=0.8,en;q=0.6"));
        headers.put("Referer", Collections.singletonList(
                referer == null || referer.isEmpty() ? BASE_URL + "/" : normalizeUrl(referer)));
        headers.put("Cookie", Collections.singletonList(
                "age_verified=1; platform=pc; accessAgeDisclaimerPH=1; cookieConsent=3"));
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

    public static String fetchText(final String url, final String referer)
            throws IOException, ExtractionException {
        return NewPipe.getDownloader().get(normalizeUrl(url),
                browserHeaders(referer == null || referer.isEmpty() ? BASE_URL + "/" : referer))
                .responseBody();
    }

    public static String normalizeUrl(final String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        String normalized = decodeUrl(unescapeUrl(url.trim()));
        if (normalized.startsWith("//")) {
            normalized = "https:" + normalized;
        }
        if (normalized.startsWith("/")) {
            normalized = BASE_URL + normalized;
        }
        normalized = normalized.replaceFirst("^https?://(?:www\\.)?pornhub\\.com", BASE_URL);
        normalized = normalized.replaceFirst("^https?://(?:[a-z]{2}\\.)pornhub\\.com", BASE_URL);
        normalized = normalized.replaceFirst("^http://jp\\.pornhub\\.com", BASE_URL);
        return normalized;
    }

    public static String absoluteUrl(final String url) {
        return url == null || url.isEmpty() ? "" : normalizeUrl(url);
    }

    public static String videoUrlFromId(final String id) {
        return BASE_URL + "/view_video.php?viewkey=" + id;
    }

    public static String searchUrl(final String query) {
        return searchUrl(query, 1);
    }

    public static String searchUrl(final String query, final int page) {
        final String encodedQuery = encodeQuery(query);
        if (page <= 1) {
            return BASE_URL + "/video/search?search=" + encodedQuery;
        }
        return BASE_URL + "/video/search?search=" + encodedQuery + "&page=" + page;
    }

    public static List<PornhubSearchResult> search(final String query, final int count)
            throws IOException, ExtractionException {
        final List<PornhubSearchResult> results = PornhubSearchEngine.search(query, 1).results;
        return results.subList(0, Math.min(results.size(), count));
    }

    public static List<PornhubSearchResult> listVideos(final String url, final int count)
            throws IOException, ExtractionException {
        return extractVideoCards(fetchDocument(url), count);
    }

    public static List<PornhubSearchResult> extractVideoCards(final Document document,
                                                              final int count) {
        return extractVideoCardsFromLinks(document.select("a[href*=view_video.php][href*=viewkey]"),
                count);
    }

    private static List<PornhubSearchResult> extractVideoCardsFromLinks(
            final Iterable<Element> links,
            final int count) {
        final LinkedHashMap<String, PornhubSearchResult> results = new LinkedHashMap<>();
        for (final Element link : links) {
            final String href = normalizeUrl(link.absUrl("href"));
            final String id = extractIdOrEmpty(href);
            if (id.isEmpty() || results.containsKey(id)) {
                continue;
            }
            final Element card = findVideoCard(link);
            final String title = extractCardTitle(link, card);
            if (isBadTitle(title)) {
                continue;
            }
            final List<String> tags = extractCardMetadata(card,
                    "a[href*=/video/search?search=], a[href*=/tags/]");
            final List<String> categories = extractCardMetadata(card,
                    "a[href*=/categories/], a[href*=/channels/], a[href*=/pornstar/]");
            final String uploaderName = extractCardUploaderName(card);
            results.put(id, new PornhubSearchResult(id, videoUrlFromId(id), title,
                    extractCardThumbnail(card), extractCardDuration(card),
                    uploaderName, extractCardUploaderUrl(card),
                    tags, categories,
                    id + " " + title + " " + uploaderName + " " + String.join(" ", tags)
                            + " " + String.join(" ", categories) + " " + text(card),
                    0));
            if (results.size() >= count) {
                break;
            }
        }
        return new ArrayList<>(results.values());
    }

    public static String extractId(final String url) throws ParsingException {
        final String id = extractIdOrEmpty(url);
        if (id.isEmpty()) {
            throw new ParsingException("Could not extract Pornhub viewkey from URL: " + url);
        }
        return id;
    }

    public static String extractTitle(final Document document) {
        String title = attr(document.selectFirst("meta[property=og:title]"), "content");
        if (isBadTitle(title)) {
            title = extractFlashValue(document.html(), "video_title");
        }
        if (isBadTitle(title)) {
            title = extractJsonString(attr(document.selectFirst("script[type=application/ld+json]"),
                    "html"), "name");
        }
        if (isBadTitle(title)) {
            title = text(document.selectFirst("h1.title, h1"));
        }
        if (isBadTitle(title)) {
            title = document.title().replace(" - Pornhub.com", "").trim();
        }
        return isBadTitle(title) ? "" : title;
    }

    public static String extractThumbnail(final Document document) {
        String thumbnail = absoluteUrl(attr(document.selectFirst("meta[property=og:image]"), "content"));
        if (thumbnail.isEmpty()) {
            thumbnail = absoluteUrl(extractFlashValue(document.html(), "image_url"));
        }
        return thumbnail;
    }

    public static String extractDescription(final Document document) {
        String description = attr(document.selectFirst("meta[property=og:description]"), "content");
        if (description.isEmpty()) {
            description = attr(document.selectFirst("meta[name=description]"), "content");
        }
        if (description.isEmpty()) {
            description = extractJsonString(attr(document.selectFirst("script[type=application/ld+json]"),
                    "html"), "description");
        }
        return description;
    }

    public static long extractDuration(final Document document) {
        final String seconds = attr(document.selectFirst("meta[property=video:duration]"), "content");
        try {
            if (!seconds.isEmpty()) {
                return Long.parseLong(seconds);
            }
        } catch (final NumberFormatException ignored) {
            // Continue with flashvars/JSON-LD duration.
        }
        final String flashDuration = extractFlashValue(document.html(), "video_duration");
        try {
            if (!flashDuration.isEmpty()) {
                return Long.parseLong(flashDuration);
            }
        } catch (final NumberFormatException ignored) {
            // Continue with ISO duration.
        }
        final Matcher matcher = ISO_DURATION_PATTERN.matcher(extractJsonString(
                attr(document.selectFirst("script[type=application/ld+json]"), "html"),
                "duration"));
        if (!matcher.matches()) {
            return -1;
        }
        return parseDurationPart(matcher.group(1)) * 3600
                + parseDurationPart(matcher.group(2)) * 60
                + parseDurationPart(matcher.group(3));
    }

    public static List<String> extractTags(final Document document) {
        final List<String> tags = new ArrayList<>();
        for (final Element element : document.select(
                "meta[property=video:tag], a[href*=/video/search?search=], a[href*=/categories/], a[href*=/pornstar/]")) {
            final String value = element.hasAttr("content") ? element.attr("content").trim() : text(element);
            if (!value.isEmpty() && !tags.contains(value)) {
                tags.add(value);
            }
        }
        return tags;
    }

    public static String extractUploaderName(final Document document) {
        String uploader = text(document.selectFirst(
                ".usernameWrap a, .userInfo a[href*=/model/], .userInfo a[href*=/users/], "
                        + ".userInfo a[href*=/channels/], .userInfo a[href*=/pornstar/], "
                        + "a[href*=/model/], a[href*=/users/], a[href*=/channels/], "
                        + "a[href*=/pornstar/]"));
        if (uploader.isEmpty()) {
            uploader = extractJsonString(document.html(), "author");
        }
        return uploader.isEmpty() ? "Pornhub" : uploader;
    }

    public static String extractUploaderUrl(final Document document) {
        final Element uploader = document.selectFirst(
                ".usernameWrap a[href], .userInfo a[href*=/model/], .userInfo a[href*=/users/], "
                        + ".userInfo a[href*=/channels/], .userInfo a[href*=/pornstar/], "
                        + "a[href*=/model/], a[href*=/users/], a[href*=/channels/], "
                        + "a[href*=/pornstar/]");
        return uploader == null ? BASE_URL + "/" : normalizeUrl(uploader.absUrl("href"));
    }

    public static String extractChannelName(final Document document, final String fallbackId) {
        String name = text(document.selectFirst(
                ".usernameWrap, .profileUserName, .nameSubscribe h1, .channelTitle h1, "
                        + ".userInfo h1, h1"));
        if (isBadTitle(name)) {
            name = attr(document.selectFirst("meta[property=og:title]"), "content")
                    .replace(" - Pornhub.com", "").trim();
        }
        if (isBadTitle(name)) {
            name = document.title().replace(" - Pornhub.com", "").trim();
        }
        if (isBadTitle(name) && fallbackId != null) {
            final int slash = fallbackId.lastIndexOf('/');
            name = slash >= 0 ? fallbackId.substring(slash + 1) : fallbackId;
            name = name.replace('-', ' ').trim();
        }
        return isBadTitle(name) || name.isEmpty() ? "Pornhub" : name;
    }

    public static String extractChannelAvatarUrl(final Document document) {
        final Element image = document.selectFirst(
                ".profilePic img, .userAvatar img, .avatar img, img.avatar, "
                        + ".userInfo img[src], .channelAvatar img");
        if (image != null) {
            for (final String attr : new String[] {"data-src", "data-thumb_url", "src"}) {
                final String url = image.hasAttr(attr) ? image.absUrl(attr) : "";
                if (!url.isEmpty() && !url.startsWith("data:")) {
                    return absoluteUrl(url);
                }
            }
        }
        return absoluteUrl(attr(document.selectFirst("meta[property=og:image]"), "content"));
    }

    public static String extractChannelBannerUrl(final Document document) {
        final Element image = document.selectFirst(
                ".coverImage img, .profileCover img, .channelCover img, .banner img");
        if (image != null) {
            for (final String attr : new String[] {"data-src", "src"}) {
                final String url = image.hasAttr(attr) ? image.absUrl(attr) : "";
                if (!url.isEmpty() && !url.startsWith("data:")) {
                    return absoluteUrl(url);
                }
            }
        }
        return "";
    }

    public static String extractChannelDescription(final Document document) {
        String description = text(document.selectFirst(
                ".aboutMeSection, .aboutChannel, .profileAbout, .description"));
        if (description.isEmpty()) {
            description = attr(document.selectFirst("meta[property=og:description]"), "content");
        }
        if (description.isEmpty()) {
            description = attr(document.selectFirst("meta[name=description]"), "content");
        }
        return description;
    }

    public static List<PornhubVideoSource> extractVideoSources(final Document document,
                                                               final String referer)
            throws IOException, ExtractionException {
        final LinkedHashMap<String, PornhubVideoSource> sources = new LinkedHashMap<>();
        putVideoSourcesFromHtml(sources, document.html());
        final List<String> remoteUrls = new ArrayList<>();
        for (final PornhubVideoSource source : sources.values()) {
            final String clean = source.url.replace(MARKER, "");
            if (clean.contains("/video/get_media")) {
                remoteUrls.add(clean);
            }
        }
        for (final String remoteUrl : remoteUrls) {
            try {
                putVideoSourcesFromHtml(sources, fetchText(remoteUrl, referer));
            } catch (final IOException | ExtractionException ignored) {
                // HLS definitions from the watch page are enough for normal playback.
            }
        }
        return new ArrayList<>(sources.values());
    }

    public static List<PornhubSearchResult> extractRelatedVideoCards(
            final Document document,
            final String id,
            final int count,
            final String referer) throws IOException, ExtractionException {
        final LinkedHashMap<String, PornhubSearchResult> results = new LinkedHashMap<>();
        final boolean hasFocusedRelated =
                appendHtmlRelatedFromFocusedSections(results, document, count, id) > 0;
        appendRelatedJsonVideos(results, document.html(), count, id);
        appendNextVideo(results, document.html(), id);
        final String relatedUrl = extractFlashValue(document.html(), "related_url");
        if (!relatedUrl.isEmpty()) {
            try {
                final String body = fetchText(relatedUrl, referer);
                appendRelatedJsonVideos(results, body, count, id);
                appendHtmlRelated(results, Jsoup.parse(body, normalizeUrl(relatedUrl)), count, id);
            } catch (final IOException | ExtractionException ignored) {
                // Fall through to DOM and search-derived related items.
            }
        }
        if (!hasFocusedRelated && results.isEmpty()) {
            appendHtmlRelated(results, document, count, id);
        }
        return new ArrayList<>(results.values()).subList(0, Math.min(results.size(), count));
    }

    private static int appendHtmlRelatedFromFocusedSections(
            final LinkedHashMap<String, PornhubSearchResult> results,
            final Document document,
            final int count,
            final String currentId) {
        final int initialSize = results.size();
        final String selector = "#relatedVideosListing, .js-relatedVideos, "
                + "[data-gaBlockName*=Related], [data-label=related_video_container], "
                + ".relatedVideos, .js-relatedRecommended";
        for (final Element section : document.select(selector)) {
            for (final PornhubSearchResult result : extractVideoCardsFromLinks(
                    section.select("a[href*=view_video.php][href*=viewkey]"), count * 3)) {
                if (!result.id.equals(currentId)) {
                    results.putIfAbsent(result.id, result);
                }
                if (results.size() >= count) {
                    return results.size() - initialSize;
                }
            }
        }
        return results.size() - initialSize;
    }

    private static void appendHtmlRelated(final LinkedHashMap<String, PornhubSearchResult> results,
                                          final Document document,
                                          final int count,
                                          final String currentId) {
        for (final PornhubSearchResult result : extractVideoCards(document, count * 2)) {
            if (!result.id.equals(currentId)) {
                results.putIfAbsent(result.id, result);
            }
            if (results.size() >= count) {
                return;
            }
        }
    }

    private static void appendRelatedJsonVideos(
            final LinkedHashMap<String, PornhubSearchResult> results,
            final String html,
            final int count,
            final String currentId) {
        final Matcher matcher = RELATED_VIDEOS_PATTERN.matcher(html);
        while (matcher.find()) {
            appendRelatedObjects(results, matcher.group(1), count, currentId);
        }
        appendRelatedObjects(results, html, count, currentId);
    }

    private static void appendRelatedObjects(final LinkedHashMap<String, PornhubSearchResult> results,
                                             final String json,
                                             final int count,
                                             final String currentId) {
        final Matcher matcher = Pattern.compile("\\{[^{}]*\"vkey\"\\s*:\\s*\"([^\"]+)\"[^{}]*\\}",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(json);
        while (matcher.find()) {
            final String object = matcher.group();
            final String id = unescapeJson(matcher.group(1));
            if (id.isEmpty() || id.equals(currentId) || results.containsKey(id)) {
                continue;
            }
            final String title = firstNonEmpty(
                    extractJsonString(object, "title"),
                    extractJsonString(object, "video_title"));
            final String thumbnail = firstNonEmpty(
                    extractJsonString(object, "thumb"),
                    extractJsonString(object, "thumbnail"),
                    extractJsonString(object, "image_url"));
            final long duration = parseLong(firstNonEmpty(
                    extractJsonNumber(object, "duration"),
                    extractJsonNumber(object, "video_duration")), -1);
            results.put(id, new PornhubSearchResult(id, videoUrlFromId(id), title,
                    absoluteUrl(thumbnail), duration, "Pornhub", BASE_URL + "/"));
            if (results.size() >= count) {
                return;
            }
        }
    }

    private static void appendNextVideo(final LinkedHashMap<String, PornhubSearchResult> results,
                                        final String html,
                                        final String currentId) {
        final Matcher matcher = NEXT_VIDEO_PATTERN.matcher(html);
        if (!matcher.find()) {
            return;
        }
        final String object = matcher.group(1);
        final String id = firstNonEmpty(extractJsonString(object, "vkey"),
                extractIdOrEmpty(extractJsonString(object, "nextUrl")));
        if (id.isEmpty() || id.equals(currentId) || results.containsKey(id)) {
            return;
        }
        results.put(id, new PornhubSearchResult(id, videoUrlFromId(id),
                extractJsonString(object, "title"),
                absoluteUrl(extractJsonString(object, "thumb")),
                parseLong(extractJsonNumber(object, "duration"), -1),
                stripHtml(extractJsonString(object, "uploaderLink")), BASE_URL + "/"));
    }

    private static void putVideoSourcesFromHtml(final LinkedHashMap<String, PornhubVideoSource> sources,
                                                final String html) {
        final String block = extractMediaDefinitionsBlock(html);
        final Matcher matcher = MEDIA_OBJECT_PATTERN.matcher(block.isEmpty() ? html : block);
        while (matcher.find()) {
            final String object = matcher.group(1);
            final String url = unescapeUrl(extractJsonString(object, "videoUrl"));
            if (!isPlayableUrl(url)) {
                continue;
            }
            final String format = extractJsonString(object, "format").toLowerCase(Locale.ROOT);
            final String quality = normalizeQuality(firstNonEmpty(
                    extractJsonString(object, "quality"),
                    extractJsonNumber(object, "height"),
                    guessQuality(url)));
            final DeliveryMethod deliveryMethod = format.contains("hls")
                    || url.toLowerCase(Locale.ROOT).contains(".m3u8")
                    ? DeliveryMethod.HLS : DeliveryMethod.PROGRESSIVE_HTTP;
            final String markedUrl = appendMarker(url);
            final String key = markedUrl.replace(MARKER, "");
            sources.putIfAbsent(key, new PornhubVideoSource(
                    (deliveryMethod == DeliveryMethod.HLS ? "hls-" : "mp4-") + quality,
                    markedUrl, quality, deliveryMethod));
        }
        final Matcher urlMatcher = Pattern.compile(
                "https?:\\\\?/\\\\?/[^\"'<>\\s]+(?:\\.m3u8|\\.mp4)[^\"'<>\\s]*",
                Pattern.CASE_INSENSITIVE).matcher(html);
        while (urlMatcher.find()) {
            final String url = unescapeUrl(urlMatcher.group());
            if (!isPlayableUrl(url)) {
                continue;
            }
            final DeliveryMethod method = url.toLowerCase(Locale.ROOT).contains(".m3u8")
                    ? DeliveryMethod.HLS : DeliveryMethod.PROGRESSIVE_HTTP;
            final String quality = normalizeQuality(guessQuality(url));
            sources.putIfAbsent(url, new PornhubVideoSource(
                    (method == DeliveryMethod.HLS ? "hls-" : "mp4-") + quality,
                    appendMarker(url), quality, method));
        }
    }

    private static String extractMediaDefinitionsBlock(final String html) {
        final Matcher flashvarsMatcher = FLASHVARS_PATTERN.matcher(html);
        if (flashvarsMatcher.find()) {
            final Matcher mediaMatcher = MEDIA_DEFINITIONS_PATTERN.matcher(flashvarsMatcher.group(1));
            if (mediaMatcher.find()) {
                return mediaMatcher.group(1);
            }
        }
        final Matcher mediaMatcher = MEDIA_DEFINITIONS_PATTERN.matcher(html);
        return mediaMatcher.find() ? mediaMatcher.group(1) : "";
    }

    private static Element findVideoCard(final Element link) {
        Element current = link;
        for (int depth = 0; depth < 7 && current.parent() != null; depth++) {
            if (current.selectFirst("img") != null
                    && current.selectFirst("a[href*=view_video.php][href*=viewkey]") != null) {
                return current;
            }
            current = current.parent();
        }
        return link;
    }

    private static String extractCardTitle(final Element link, final Element card) {
        for (final String attr : new String[] {"title", "aria-label"}) {
            if (link.hasAttr(attr) && !link.attr(attr).trim().isEmpty()) {
                return link.attr(attr).trim();
            }
        }
        final Element titleElement = card.selectFirst(".title a, span.title, .videoTitle, img[title], img[alt]");
        if (titleElement == null) {
            return text(link);
        }
        final String value = firstNonEmpty(titleElement.attr("title"), titleElement.attr("alt"),
                text(titleElement));
        return value.trim();
    }

    private static String extractCardThumbnail(final Element card) {
        for (final Element image : card.select(
                "img[data-src], img[data-thumb_url], img[data-mediumthumb], img[data-image], "
                        + "img[data-lazy], img[src]")) {
            for (final String attr : new String[] {
                    "data-src", "data-thumb_url", "data-mediumthumb", "data-image",
                    "data-lazy", "src"
            }) {
                final String rawUrl = image.hasAttr(attr) ? firstNonEmpty(
                        image.absUrl(attr), image.attr(attr)) : "";
                if (!rawUrl.isEmpty() && !rawUrl.startsWith("data:")) {
                    final String url = absoluteUrl(rawUrl);
                    if (!url.isEmpty()) {
                        return url;
                    }
                }
            }
        }
        return "";
    }

    private static long extractCardDuration(final Element card) {
        final Matcher matcher = DURATION_PATTERN.matcher(text(card));
        if (!matcher.find()) {
            return -1;
        }
        return parseDuration(matcher.group(1));
    }

    private static String extractCardUploaderName(final Element card) {
        final String uploader = text(card.selectFirst(
                "a[href*=/model/], a[href*=/users/], a[href*=/channels/], "
                        + "a[href*=/pornstar/], .username"));
        return uploader.isEmpty() ? "Pornhub" : uploader;
    }

    private static String extractCardUploaderUrl(final Element card) {
        final Element uploader = card.selectFirst(
                "a[href*=/model/], a[href*=/users/], a[href*=/channels/], a[href*=/pornstar/]");
        return uploader == null ? BASE_URL + "/" : normalizeUrl(uploader.absUrl("href"));
    }

    private static List<String> extractCardMetadata(final Element card, final String selector) {
        final List<String> values = new ArrayList<>();
        for (final Element element : card.select(selector)) {
            final String value = text(element);
            if (!value.isEmpty() && !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private static String extractFlashValue(final String html, final String key) {
        final Matcher matcher = Pattern.compile("\"" + Pattern.quote(key)
                        + "\"\\s*:\\s*(?:\"((?:\\\\.|[^\"])*)\"|([^,}\\]]+))",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        if (!matcher.find()) {
            return "";
        }
        return unescapeJson(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
    }

    private static String extractJsonString(final String json, final String key) {
        if (json == null || json.isEmpty()) {
            return "";
        }
        final Matcher matcher = Pattern.compile("\"" + Pattern.quote(key)
                + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"", Pattern.CASE_INSENSITIVE).matcher(json);
        return matcher.find() ? unescapeJson(matcher.group(1)) : "";
    }

    private static String extractJsonNumber(final String json, final String key) {
        if (json == null || json.isEmpty()) {
            return "";
        }
        final Matcher matcher = Pattern.compile("\"" + Pattern.quote(key)
                + "\"\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String extractIdOrEmpty(final String url) {
        final Matcher matcher = VIEWKEY_PATTERN.matcher(normalizeUrl(url));
        return matcher.find() ? matcher.group(1) : "";
    }

    private static boolean isPlayableUrl(final String url) {
        final String lower = url == null ? "" : url.toLowerCase(Locale.ROOT);
        return lower.startsWith("http") && (lower.contains(".m3u8") || lower.contains(".mp4")
                || lower.contains("/video/get_media"));
    }

    private static String appendMarker(final String url) {
        return url.contains(MARKER) ? url : url + MARKER;
    }

    private static String normalizeQuality(final String quality) {
        if (quality == null || quality.trim().isEmpty() || quality.trim().equals("[]")) {
            return "auto";
        }
        final Matcher matcher = Pattern.compile("(\\d{3,4})").matcher(quality);
        return matcher.find() ? matcher.group(1) + "p" : quality.trim();
    }

    private static String guessQuality(final String value) {
        final Matcher matcher = Pattern.compile("(\\d{3,4})P", Pattern.CASE_INSENSITIVE)
                .matcher(value == null ? "" : value);
        return matcher.find() ? matcher.group(1) : "auto";
    }

    private static boolean isBadTitle(final String title) {
        if (title == null || title.trim().isEmpty()) {
            return true;
        }
        final String lower = title.trim().toLowerCase(Locale.ROOT);
        return lower.contains("attention required") || lower.contains("cloudflare")
                || lower.contains("sorry, you have been blocked") || lower.equals("pornhub.com")
                || lower.equals("not found") || lower.contains("404");
    }

    private static long parseDuration(final String value) {
        try {
            long seconds = 0;
            for (final String part : value.split(":")) {
                seconds = seconds * 60 + Long.parseLong(part.trim());
            }
            return seconds;
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    private static long parseLong(final String value, final long fallback) {
        try {
            return value == null || value.isEmpty() ? fallback : Long.parseLong(value);
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseDurationPart(final String value) {
        return value == null || value.isEmpty() ? 0 : Integer.parseInt(value);
    }

    public static String encodeQuery(final String query) {
        return URLEncoder.encode(query == null ? "" : query.trim(), StandardCharsets.UTF_8);
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

    private static String unescapeUrl(final String value) {
        return unescapeJson(value).replace("&amp;", "&");
    }

    private static String unescapeJson(final String value) {
        if (value == null) {
            return "";
        }
        final String unescaped = value.replace("\\/", "/")
                .replace("\\u002F", "/")
                .replace("\\u0026", "&")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\t", "\t");
        final Matcher matcher = Pattern.compile("\\\\u([0-9a-fA-F]{4})").matcher(unescaped);
        final StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer,
                    Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(matcher.group(1), 16))));
        }
        matcher.appendTail(buffer);
        return buffer.toString().trim();
    }

    private static String stripHtml(final String html) {
        return html == null || html.isEmpty() ? "" : Jsoup.parse(html).text().trim();
    }

    private static String text(final Element element) {
        return element == null ? "" : element.text().trim();
    }

    private static String attr(final Element element, final String attr) {
        return element == null ? "" : element.attr(attr).trim();
    }

    private static String firstNonEmpty(final String... values) {
        for (final String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
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
