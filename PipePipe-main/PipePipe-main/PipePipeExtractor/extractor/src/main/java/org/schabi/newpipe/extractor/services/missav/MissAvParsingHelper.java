package org.schabi.newpipe.extractor.services.missav;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class MissAvParsingHelper {
    public static final String BASE_URL = "https://missav.ws";
    public static final String DEFAULT_LANGUAGE = "ja";

    private static final String RECOMBEE_HOST = "client-rapi-missav.recombee.com";
    private static final String DATABASE_ID = "missav-default";
    private static final String PUBLIC_TOKEN =
            "Ikkg568nlM51RHvldlPvc2GzZPE9R4XGzaH9Qj4zK9npbbbTly1gj9K4mgRn0QlV";

    private static final Pattern M3U8_PACKED_PATTERN = Pattern.compile("'m3u8(.*?)video");
    private static final Pattern M3U8_URL_PATTERN = Pattern.compile(
            "https?:(?:\\\\/|/)(?:\\\\/|/)[^\"'\\s<>]+?\\.m3u8[^\"'\\s<>]*");
    private static final Pattern OG_IMAGE_PATTERN = Pattern.compile(
            "<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+cover-n\\.jpg)");
    private static final int DOCUMENT_CACHE_SIZE = 24;
    private static final long DOCUMENT_CACHE_TTL_MS = 120_000L;
    private static final Map<String, CachedDocument> DOCUMENT_CACHE =
            new LinkedHashMap<String, CachedDocument>(DOCUMENT_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        final Map.Entry<String, CachedDocument> eldest) {
                    return size() > DOCUMENT_CACHE_SIZE;
                }
            };

    private MissAvParsingHelper() {
    }

    public static Map<String, List<String>> browserHeaders() {
        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("User-Agent", Collections.singletonList(
                "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/142.0.0.0 Mobile Safari/537.36"));
        headers.put("Accept", Collections.singletonList(
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"));
        headers.put("Accept-Language", Collections.singletonList("ja,en-US;q=0.8,en;q=0.6"));
        return headers;
    }

    public static Map<String, List<String>> hlsHeaders(final String referer) {
        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("User-Agent", Collections.singletonList(
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"));
        headers.put("Referer", Collections.singletonList(
                referer == null || referer.isEmpty() ? BASE_URL + "/" : referer));
        headers.put("Origin", Collections.singletonList(BASE_URL));
        headers.put("Accept", Collections.singletonList("*/*"));
        headers.put("Accept-Language", Collections.singletonList("ja,en-US;q=0.8,en;q=0.6"));
        return headers;
    }

    public static Document fetchDocument(final String url) throws IOException, ExtractionException {
        final String localizedUrl = localizeUrl(url);
        synchronized (DOCUMENT_CACHE) {
            final CachedDocument cached = DOCUMENT_CACHE.get(localizedUrl);
            if (cached != null && System.currentTimeMillis() - cached.timestamp < DOCUMENT_CACHE_TTL_MS) {
                return cached.document;
            }
        }

        final Response response = NewPipe.getDownloader().get(localizedUrl, browserHeaders());
        final Document document = Jsoup.parse(response.responseBody(), localizedUrl);
        synchronized (DOCUMENT_CACHE) {
            DOCUMENT_CACHE.put(localizedUrl, new CachedDocument(document));
        }
        return document;
    }

    public static List<MissAvSearchResult> search(final String query, final int count)
            throws IOException, ExtractionException {
        final LinkedHashMap<String, MissAvSearchResult> mergedResults = new LinkedHashMap<>();
        final List<String> searchQueries = buildSearchQueries(query);
        for (final String code : extractSearchCodes(query)) {
            if (isVideoId(code)) {
                mergedResults.put(code, new MissAvSearchResult(code, toVideoUrl(code),
                        new JsonObject()));
            }
        }

        for (final String searchQuery : searchQueries) {
            try {
                for (final MissAvSearchResult result : searchRecombee(searchQuery, count)) {
                    mergedResults.putIfAbsent(result.id, result);
                    if (mergedResults.size() >= count) {
                        return new ArrayList<>(mergedResults.values());
                    }
                }
            } catch (final IOException | ExtractionException ignored) {
                // Fall through to the regular site search; title-only searches are often better there.
            }
        }
        try {
            for (final String searchQuery : searchQueries) {
                for (final MissAvSearchResult result : searchHtml(searchQuery, count)) {
                    mergedResults.putIfAbsent(result.id, result);
                    if (mergedResults.size() >= count) {
                        return new ArrayList<>(mergedResults.values());
                    }
                }
            }
        } catch (final IOException | ExtractionException e) {
            if (mergedResults.isEmpty()) {
                throw e;
            }
        }
        return new ArrayList<>(mergedResults.values());
    }

    private static List<MissAvSearchResult> searchRecombee(final String query, final int count)
            throws IOException, ExtractionException {
        final String path = "/search/users/anonymous/items/";
        final String signedPath = signPath(path);
        final String url = "https://" + RECOMBEE_HOST + signedPath;
        final String body = "{\"searchQuery\":\"" + escapeJson(query) + "\","
                + "\"count\":" + count + ","
                + "\"cascadeCreate\":true,"
                + "\"returnProperties\":true}";

        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("Accept", Collections.singletonList("application/json"));
        headers.put("Content-Type", Collections.singletonList("application/json"));

        final Response response = NewPipe.getDownloader().post(
                url, headers, body.getBytes(StandardCharsets.UTF_8));

        try {
            final JsonObject json = JsonParser.object().from(response.responseBody());
            final JsonArray recomms = json.getArray("recomms", new JsonArray());
            final List<MissAvSearchResult> results = new ArrayList<>();
            for (final Object item : recomms) {
                if (item instanceof JsonObject) {
                    final JsonObject object = (JsonObject) item;
                    final String id = object.getString("id");
                    if (id != null && !id.isEmpty()) {
                        results.add(new MissAvSearchResult(id, toVideoUrl(id),
                                getResultValues(object)));
                    }
                }
            }
            return results;
        } catch (final JsonParserException e) {
            throw new ParsingException("Could not parse MissAV search response", e);
        }
    }

    private static List<MissAvSearchResult> searchHtml(final String query, final int count)
            throws IOException, ExtractionException {
        final Document document = fetchDocument(BASE_URL + "/" + DEFAULT_LANGUAGE
                + "/search/" + encodeQuery(query));
        final LinkedHashMap<String, MissAvSearchResult> results = new LinkedHashMap<>();
        for (final Element link : document.select("a[href]")) {
            final String href = localizeUrl(link.absUrl("href"));
            final String id;
            try {
                id = extractId(href).toLowerCase(Locale.ROOT);
            } catch (final ParsingException ignored) {
                continue;
            }
            if (!isVideoId(id) || results.containsKey(id)) {
                continue;
            }
            final JsonObject properties = new JsonObject();
            putIfNotEmpty(properties, "title_ja", extractCardTitle(link));
            putIfNotEmpty(properties, "thumbnail", extractCardThumbnail(link));
            results.put(id, new MissAvSearchResult(id, toVideoUrl(id), properties));
            if (results.size() >= count) {
                break;
            }
        }
        return new ArrayList<>(results.values());
    }

    private static String extractCardTitle(final Element link) {
        final Element card = link.parent() == null ? link : link.parent();
        final Element image = link.selectFirst("img[alt]");
        if (image != null && !image.attr("alt").trim().isEmpty()) {
            return image.attr("alt").trim();
        }
        if (link.hasAttr("title") && !link.attr("title").trim().isEmpty()) {
            return link.attr("title").trim();
        }
        final Element title = card.selectFirst("h1, h2, h3, .my-2, .text-secondary, a[title]");
        if (title != null && title.hasAttr("title") && !title.attr("title").trim().isEmpty()) {
            return title.attr("title").trim();
        }
        final String titleText = text(title);
        return titleText.isEmpty() ? text(link) : titleText;
    }

    private static String extractCardThumbnail(final Element link) {
        final Element card = link.parent() == null ? link : link.parent();
        final Element image = card.selectFirst("img[data-src], img[src], source[srcset]");
        if (image == null) {
            return "";
        }
        final String dataSrc = image.absUrl("data-src");
        if (!dataSrc.isEmpty()) {
            return dataSrc;
        }
        final String src = image.absUrl("src");
        if (!src.isEmpty()) {
            return src;
        }
        final String srcset = image.attr("abs:srcset");
        final int separator = srcset.indexOf(' ');
        return separator > 0 ? srcset.substring(0, separator) : srcset;
    }

    private static void putIfNotEmpty(final JsonObject properties,
                                      final String key,
                                      final String value) {
        if (value != null && !value.trim().isEmpty()) {
            properties.put(key, value.trim());
        }
    }

    static List<String> buildSearchQueries(final String rawQuery) {
        final LinkedHashSet<String> queries = new LinkedHashSet<>();
        final String query = normalizeSearchText(rawQuery);
        addSearchQuery(queries, query);
        addSearchQuery(queries, query.toLowerCase(Locale.ROOT));

        for (final String code : extractSearchCodes(query)) {
            final String rest = removeCodeFromQuery(query, code);
            addSearchQuery(queries, code);
            addSearchQuery(queries, code + " " + rest);
            addSearchQuery(queries, code.replace("-", " ") + " " + rest);

            final String[] parts = code.split("-");
            if (parts.length >= 2) {
                addSearchQuery(queries, parts[0] + " " + rest);
                addSearchQuery(queries, parts[parts.length - 1] + " " + rest);
            }
            if (code.startsWith("fc2-ppv-")) {
                addSearchQuery(queries, "fc2 " + rest);
                addSearchQuery(queries, code.substring("fc2-ppv-".length()) + " " + rest);
            }
        }
        return new ArrayList<>(queries);
    }

    private static List<String> extractSearchCodes(final String rawQuery) {
        final LinkedHashSet<String> codes = new LinkedHashSet<>();
        final String query = normalizeSearchText(rawQuery).toLowerCase(Locale.ROOT);

        final Matcher fc2Matcher = Pattern.compile(
                "\\bfc2[\\s_-]*(?:ppv[\\s_-]*)?(\\d{4,})\\b",
                Pattern.CASE_INSENSITIVE).matcher(query);
        while (fc2Matcher.find()) {
            codes.add("fc2-ppv-" + fc2Matcher.group(1));
        }

        final Matcher codeMatcher = Pattern.compile(
                "\\b([a-z]{2,10})[\\s_-]+(\\d{2,8})\\b",
                Pattern.CASE_INSENSITIVE).matcher(query);
        while (codeMatcher.find()) {
            final String prefix = codeMatcher.group(1).toLowerCase(Locale.ROOT);
            if ("fc2".equals(prefix)) {
                codes.add("fc2-ppv-" + codeMatcher.group(2));
            } else if (!"ppv".equals(prefix)) {
                codes.add(prefix + "-" + codeMatcher.group(2));
            }
        }

        final Matcher numberMatcher = Pattern.compile("\\b(\\d{6,})\\b").matcher(query);
        while (numberMatcher.find()) {
            codes.add("fc2-ppv-" + numberMatcher.group(1));
        }
        return new ArrayList<>(codes);
    }

    private static String normalizeSearchText(final String query) {
        return query == null ? "" : query.replace('\u3000', ' ')
                .replace('+', ' ')
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static void addSearchQuery(final LinkedHashSet<String> queries, final String query) {
        final String normalized = normalizeSearchText(query);
        if (!normalized.isEmpty()) {
            queries.add(normalized);
        }
    }

    private static String removeCodeFromQuery(final String query, final String code) {
        String result = normalizeSearchText(query);
        result = result.replaceAll("(?i)\\bfc2[\\s_-]*(?:ppv[\\s_-]*)?"
                + Pattern.quote(code.replace("fc2-ppv-", "")) + "\\b", " ");
        result = result.replaceAll("(?i)\\b" + Pattern.quote(code)
                .replace("\\-", "[\\\\s_-]+") + "\\b", " ");
        final String[] parts = code.split("-");
        if (parts.length >= 2) {
            result = result.replaceAll("(?i)\\b" + Pattern.quote(parts[0])
                    + "[\\s_-]+" + Pattern.quote(parts[parts.length - 1]) + "\\b", " ");
        }
        return normalizeSearchText(result);
    }

    public static List<MissAvSearchResult> recommend(final String scenario, final int count)
            throws IOException, ExtractionException {
        final String path = "/recommend/users/anonymous/items/";
        final String signedPath = signPath(path);
        final String url = "https://" + RECOMBEE_HOST + signedPath;
        String body = "{\"count\":" + count + ","
                + "\"cascadeCreate\":true,"
                + "\"returnProperties\":true";
        if (scenario != null && !scenario.isEmpty()) {
            body += ",\"scenario\":\"" + escapeJson(scenario) + "\"";
        }
        body += "}";

        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("Accept", Collections.singletonList("application/json"));
        headers.put("Content-Type", Collections.singletonList("application/json"));

        final Response response = NewPipe.getDownloader().post(
                url, headers, body.getBytes(StandardCharsets.UTF_8));

        try {
            final JsonObject json = JsonParser.object().from(response.responseBody());
            final JsonArray recomms = json.getArray("recomms", new JsonArray());
            final List<MissAvSearchResult> results = new ArrayList<>();
            for (final Object item : recomms) {
                if (item instanceof JsonObject) {
                    final JsonObject object = (JsonObject) item;
                    final String id = object.getString("id");
                    if (id != null && !id.isEmpty()) {
                        results.add(new MissAvSearchResult(id, toVideoUrl(id),
                                getResultValues(object)));
                    }
                }
            }
            return results;
        } catch (final JsonParserException e) {
            throw new ParsingException("Could not parse MissAV recommendations response", e);
        }
    }

    public static List<MissAvSearchResult> recommendRelated(final String itemId, final int count)
            throws IOException, ExtractionException {
        if (!isVideoId(itemId)) {
            return Collections.emptyList();
        }
        final String normalizedItemId = extractPlainId(itemId).toLowerCase();
        final String path = "/recomms/items/" + normalizedItemId + "/items/";
        final String signedPath = signPath(path);
        final String url = "https://" + RECOMBEE_HOST + signedPath;
        final String body = "{\"count\":" + count + ","
                + "\"cascadeCreate\":true,"
                + "\"returnProperties\":true}";

        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("Accept", Collections.singletonList("application/json"));
        headers.put("Content-Type", Collections.singletonList("application/json"));

        final Response response = NewPipe.getDownloader().post(
                url, headers, body.getBytes(StandardCharsets.UTF_8));

        try {
            final JsonObject json = JsonParser.object().from(response.responseBody());
            final JsonArray recomms = json.getArray("recomms", new JsonArray());
            final List<MissAvSearchResult> results = new ArrayList<>();
            for (final Object item : recomms) {
                if (item instanceof JsonObject) {
                    final JsonObject object = (JsonObject) item;
                    final String id = object.getString("id");
                    if (isVideoId(id)) {
                        results.add(new MissAvSearchResult(id, toVideoUrl(id),
                                getResultValues(object)));
                    }
                }
            }
            return results;
        } catch (final JsonParserException e) {
            throw new ParsingException("Could not parse MissAV related response", e);
        }
    }

    public static String toVideoUrl(final String id) {
        if (id.startsWith("http://") || id.startsWith("https://")) {
            return localizeUrl(id);
        }
        return BASE_URL + "/" + DEFAULT_LANGUAGE + "/" + id;
    }

    public static String toThumbnailUrl(final String id) {
        if (id == null || id.isEmpty()) {
            return "";
        }
        final String normalizedId = extractPlainId(id).toLowerCase();
        return "https://fourhoi.com/" + normalizedId + "/cover-n.jpg";
    }

    public static boolean isVideoId(final String id) {
        if (id == null) {
            return false;
        }
        final String plainId = extractPlainId(id).toLowerCase();
        return plainId.length() >= 4
                && plainId.contains("-")
                && plainId.matches(".*\\d.*")
                && plainId.matches("[a-z0-9][a-z0-9_-]*[a-z0-9]");
    }

    public static String localizeUrl(final String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        final String normalizedHost = url.replaceFirst(
                "^https?://(?:www\\.)?missav\\.(?:ai|wa)", BASE_URL);
        return normalizedHost.replaceFirst("/(?:en|zh|tw|ko|th|vi|id|ms|de|fr|es|pt)/",
                "/" + DEFAULT_LANGUAGE + "/");
    }

    public static String extractId(final String url) throws ParsingException {
        final String cleanUrl = url.split("[?#]", 2)[0];
        final int slash = cleanUrl.lastIndexOf('/');
        if (slash < 0 || slash == cleanUrl.length() - 1) {
            throw new ParsingException("Could not extract MissAV id from URL: " + url);
        }
        return cleanUrl.substring(slash + 1);
    }

    private static String extractPlainId(final String idOrUrl) {
        final String cleanValue = idOrUrl.split("[?#]", 2)[0];
        final int slash = cleanValue.lastIndexOf('/');
        return slash >= 0 ? cleanValue.substring(slash + 1) : cleanValue;
    }

    public static String text(final Element element) {
        return element == null ? "" : element.text().trim();
    }

    public static String firstMetaValue(final Document doc, final int index) {
        final List<Element> values = doc.select("div.space-y-2 div.text-secondary");
        if (index >= values.size()) {
            return "";
        }
        final Element value = values.get(index).selectFirst(".font-medium");
        return text(value);
    }

    public static List<String> linkedMetaValues(final Document doc, final int index) {
        final List<Element> values = doc.select("div.space-y-2 div.text-secondary");
        if (index >= values.size()) {
            return Collections.emptyList();
        }
        final List<String> result = new ArrayList<>();
        for (final Element a : values.get(index).select("a")) {
            final String text = text(a);
            if (!text.isEmpty()) {
                result.add(text);
            }
        }
        return result;
    }

    public static List<String> actressNames(final Document doc) {
        final List<String> names = linkedMetaValuesByLabels(doc,
                "\u5973\u512a", "Actress", "\u5973\u4f18", "\u6f14\u54e1", "\u6f14\u5458");
        return names.isEmpty() ? linkedMetaValues(doc, 7) : names;
    }

    public static List<String> actressUrls(final Document doc) {
        return linkedMetaUrlsByLabels(doc,
                "\u5973\u512a", "Actress", "\u5973\u4f18", "\u6f14\u54e1", "\u6f14\u5458");
    }

    public static List<String> linkedMetaValuesByLabels(final Document doc,
                                                        final String... labels) {
        final LinkedHashSet<String> result = new LinkedHashSet<>();
        for (final Element row : metaRows(doc)) {
            if (!hasAnyLabel(row, labels)) {
                continue;
            }
            for (final Element a : row.select("a")) {
                final String value = text(a);
                if (!value.isEmpty()) {
                    result.add(value);
                }
            }
        }
        return new ArrayList<>(result);
    }

    public static List<String> linkedMetaUrlsByLabels(final Document doc,
                                                      final String... labels) {
        final LinkedHashSet<String> result = new LinkedHashSet<>();
        for (final Element row : metaRows(doc)) {
            if (!hasAnyLabel(row, labels)) {
                continue;
            }
            for (final Element a : row.select("a[href]")) {
                final String href = localizeUrl(a.absUrl("href"));
                if (href != null && !href.isEmpty()) {
                    result.add(href);
                }
            }
        }
        return new ArrayList<>(result);
    }

    private static List<Element> metaRows(final Document doc) {
        final LinkedHashSet<Element> rows = new LinkedHashSet<>();
        rows.addAll(doc.select("div.space-y-2 div.text-secondary"));
        rows.addAll(doc.select("div.text-secondary:has(span):has(a)"));
        return new ArrayList<>(rows);
    }

    private static boolean hasAnyLabel(final Element row, final String... labels) {
        final Element labelElement = row.selectFirst("span");
        final String labelText = text(labelElement == null ? row : labelElement)
                .replace("\uff1a", ":")
                .replace(":", "")
                .trim();
        for (final String label : labels) {
            if (labelText.equalsIgnoreCase(label) || labelText.contains(label)) {
                return true;
            }
        }
        return false;
    }

    public static String extractThumbnail(final Document doc) {
        final Element image = doc.selectFirst("meta[property=og:image]");
        if (image != null) {
            return image.attr("content");
        }
        final Matcher matcher = OG_IMAGE_PATTERN.matcher(doc.html());
        return matcher.find() ? matcher.group(1) : "";
    }

    public static String extractPageDescription(final Document doc) {
        for (final Element element : doc.select(
                "div.mb-1.text-secondary.break-all, div[class*=line-clamp].text-secondary")) {
            final String text = text(element);
            if (!text.isEmpty()
                    && !text.contains("\u5973\u512a:")
                    && !text.contains("Actress:")
                    && text.length() > 8) {
                return text;
            }
        }
        return "";
    }

    public static String extractHlsUrl(final Document doc) throws ParsingException {
        final String html = doc.html();
        final Matcher urlMatcher = M3U8_URL_PATTERN.matcher(html);
        if (urlMatcher.find()) {
            final String candidate = unescapeUrl(urlMatcher.group());
            if (isValidHlsUrl(candidate)) {
                return candidate;
            }
        }

        final Matcher matcher = M3U8_PACKED_PATTERN.matcher(doc.html());
        if (!matcher.find()) {
            throw new ParsingException("Could not find MissAV HLS metadata");
        }

        final String[] rawParts = matcher.group(1).split("\\|");
        final List<String> parts = new ArrayList<>();
        for (int i = rawParts.length - 1; i >= 0; i--) {
            parts.add(rawParts[i]);
        }
        for (int i = 0; i + 7 < parts.size(); i++) {
            if ("http".equals(parts.get(i)) || "https".equals(parts.get(i))) {
                final String candidate = parts.get(i) + "://" + parts.get(i + 1) + "."
                        + parts.get(i + 2) + "/" + parts.get(i + 3) + "-" + parts.get(i + 4)
                        + "-" + parts.get(i + 5) + "-" + parts.get(i + 6) + "-"
                        + parts.get(i + 7) + "/playlist.m3u8";
                if (isValidHlsUrl(candidate)) {
                    return candidate;
                }
            }
        }
        throw new ParsingException("Could not construct a valid MissAV HLS URL");
    }

    private static String unescapeUrl(final String url) {
        return url.replace("\\/", "/")
                .replace("\\u002F", "/")
                .replace("\\u0026", "&")
                .replace("&amp;", "&");
    }

    private static boolean isValidHlsUrl(final String url) {
        return (url.startsWith("https://") || url.startsWith("http://"))
                && url.contains(".")
                && url.endsWith("/playlist.m3u8")
                && !url.contains("://.")
                && !url.contains(" ");
    }

    private static JsonObject getResultValues(final JsonObject object) {
        final JsonObject values = object.getObject("values");
        return values == null ? object : values;
    }

    private static String signPath(final String path) throws ParsingException {
        final long timestamp = System.currentTimeMillis() / 1000L;
        String unsigned = "/" + DATABASE_ID + path;
        unsigned += path.contains("?") ? "&frontend_timestamp=" : "?frontend_timestamp=";
        unsigned += timestamp;
        return unsigned + "&frontend_sign=" + hmacSha1(unsigned, PUBLIC_TOKEN);
    }

    private static String hmacSha1(final String value, final String key) throws ParsingException {
        try {
            final Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            final byte[] bytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            final Formatter formatter = new Formatter();
            for (final byte b : bytes) {
                formatter.format("%02x", b);
            }
            return formatter.toString();
        } catch (final Exception e) {
            throw new ParsingException("Could not sign MissAV request", e);
        }
    }

    private static String escapeJson(final String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static String encodeQuery(final String query) {
        return URLEncoder.encode(query, StandardCharsets.UTF_8);
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
