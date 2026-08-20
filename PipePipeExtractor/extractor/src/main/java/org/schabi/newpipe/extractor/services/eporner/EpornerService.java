package org.schabi.newpipe.extractor.services.eporner;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.InfoItemExtractor;
import org.schabi.newpipe.extractor.InfoItemsCollector;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.MetaInfo;
import org.schabi.newpipe.extractor.MultiInfoItemsCollector;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsExtractor;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor;
import org.schabi.newpipe.extractor.comments.CommentsExtractor;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** EPORNER implementation using its public video API and first-party video pages only. */
public final class EpornerService extends StreamingService {
    public EpornerService(final int id) {
        super(id, "EPORNER", Collections.singletonList(ServiceInfo.MediaCapability.VIDEO));
    }

    @Override public String getBaseUrl() { return EpornerParser.BASE; }
    @Override public LinkHandlerFactory getStreamLHFactory() { return EpornerStreamLinkHandlerFactory.INSTANCE; }
    @Override public ListLinkHandlerFactory getChannelLHFactory() { return null; }
    @Override public ListLinkHandlerFactory getChannelTabLHFactory() { return null; }
    @Override public ListLinkHandlerFactory getPlaylistLHFactory() { return null; }
    @Override public SearchQueryHandlerFactory getSearchQHFactory() { return EpornerSearchQueryHandlerFactory.INSTANCE; }
    @Override public ListLinkHandlerFactory getCommentsLHFactory() { return null; }
    @Override public SearchExtractor getSearchExtractor(final SearchQueryHandler handler) {
        return new EpornerSearchExtractor(this, handler);
    }
    @Override public SuggestionExtractor getSuggestionExtractor() { return null; }
    @Override public SubscriptionExtractor getSubscriptionExtractor() { return null; }
    @Override public KioskList getKioskList() throws ExtractionException {
        final KioskList kiosks = new KioskList(this);
        try {
            kiosks.addKioskEntry((service, url, kioskId) -> new EpornerKioskExtractor(
                            service, EpornerKioskLinkHandlerFactory.INSTANCE.fromId(kioskId), kioskId),
                    EpornerKioskLinkHandlerFactory.INSTANCE, "latest");
            kiosks.setDefaultKiosk("latest");
            return kiosks;
        } catch (final Exception e) {
            throw new ExtractionException("Could not initialize Eporner kiosks", e);
        }
    }
    @Override public ChannelExtractor getChannelExtractor(final ListLinkHandler handler) { return null; }
    @Override public ChannelTabExtractor getChannelTabExtractor(final ListLinkHandler handler)
            throws ExtractionException { throw new ExtractionException("EPORNER channel tabs unavailable"); }
    @Override public PlaylistExtractor getPlaylistExtractor(final ListLinkHandler handler)
            throws ExtractionException { throw new ExtractionException("EPORNER playlists unavailable"); }
    @Override public StreamExtractor getStreamExtractor(final LinkHandler handler) {
        return new EpornerStreamExtractor(this, handler);
    }
    @Override public CommentsExtractor getCommentsExtractor(final ListLinkHandler handler) { return null; }
    @Override public BulletCommentsExtractor getBulletCommentsExtractor(final ListLinkHandler handler) { return null; }
}

final class EpornerStreamLinkHandlerFactory extends LinkHandlerFactory {
    static final EpornerStreamLinkHandlerFactory INSTANCE = new EpornerStreamLinkHandlerFactory();
    @Override public String getId(final String url) throws ParsingException { return EpornerParser.id(url); }
    @Override public String getUrl(final String id) { return EpornerParser.BASE + "/video-" + id + "/"; }
    @Override public boolean onAcceptUrl(final String url) { return EpornerParser.isVideoUrl(url); }
}

final class EpornerSearchQueryHandlerFactory extends SearchQueryHandlerFactory {
    static final EpornerSearchQueryHandlerFactory INSTANCE = new EpornerSearchQueryHandlerFactory();
    @Override public String getUrl(final String query, final List<FilterItem> content,
                                   final List<FilterItem> sort) {
        return EpornerParser.searchUrl(query, 1);
    }
}

final class EpornerSearchExtractor extends SearchExtractor {
    EpornerSearchExtractor(final StreamingService service, final SearchQueryHandler handler) {
        super(service, handler);
    }
    @Override public void onFetchPage(@Nonnull final Downloader downloader) { }
    @Override protected InfoItemsPage<InfoItem> getInitialPageInternal()
            throws IOException, ExtractionException {
        return pageFor(1);
    }
    @Override protected InfoItemsPage<InfoItem> getPageInternal(final Page page)
            throws IOException, ExtractionException {
        return pageFor(EpornerParser.pageNumber(page.getUrl()));
    }
    private InfoItemsPage<InfoItem> pageFor(final int page) throws IOException, ExtractionException {
        final EpornerSearchPage result = EpornerParser.search(getSearchString(), page);
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        for (final EpornerItem item : result.items) collector.commit(new EpornerItemExtractor(item));
        final Page next = result.hasNext ? new Page(EpornerParser.searchUrl(getSearchString(), page + 1)) : null;
        return new ListExtractor.InfoItemsPage<>(collector, next);
    }
}

final class EpornerKioskLinkHandlerFactory extends ListLinkHandlerFactory {
    static final EpornerKioskLinkHandlerFactory INSTANCE = new EpornerKioskLinkHandlerFactory();
    @Override public String getId(final String url) { return "latest"; }
    @Override public String getUrl(final String id, final List<FilterItem> content,
                                   final List<FilterItem> sort) { return EpornerParser.kioskUrl(); }
    @Override public boolean onAcceptUrl(final String url) {
        return url != null && url.startsWith(EpornerParser.BASE);
    }
}

final class EpornerKioskExtractor extends KioskExtractor<org.schabi.newpipe.extractor.stream.StreamInfoItem> {
    private Document document;
    EpornerKioskExtractor(final StreamingService service, final ListLinkHandler handler,
                          final String kioskId) { super(service, handler, kioskId); }
    @Override public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        document = EpornerParser.fetch(getUrl());
        if (EpornerParser.cards(document, 1).isEmpty()) {
            document = EpornerParser.fetch(EpornerParser.homeUrl());
        }
    }
    @Nonnull @Override public String getName() { return "Latest"; }
    @Nonnull @Override public InfoItemsPage<org.schabi.newpipe.extractor.stream.StreamInfoItem> getInitialPage()
            throws ExtractionException {
        if (document == null) throw new ParsingException("Eporner kiosk page was not fetched");
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        for (final EpornerItem item : EpornerParser.cards(document, 40)) {
            collector.commit(new EpornerItemExtractor(item));
        }
        return new InfoItemsPage<>(collector, null);
    }
    @Override public InfoItemsPage<org.schabi.newpipe.extractor.stream.StreamInfoItem> getPage(final Page page) {
        return InfoItemsPage.emptyPage();
    }
}

final class EpornerStreamExtractor extends StreamExtractor {
    private Document document;
    private String sessionCookies = "";
    private List<EpornerStream> signedStreams = Collections.emptyList();
    EpornerStreamExtractor(final StreamingService service, final LinkHandler handler) { super(service, handler); }
    @Override public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        final Response response = downloader.get(getUrl(), EpornerParser.headers());
        document = Jsoup.parse(response.responseBody(), response.latestUrl());
        sessionCookies = EpornerParser.sessionCookies(response);
        signedStreams = EpornerParser.fetchSignedStreams(downloader, document, getId(), sessionCookies);
    }
    @Nonnull @Override public String getName() throws ParsingException {
        page(); return EpornerParser.cleanTitle(EpornerParser.meta(document, "meta[property=og:title], title"), getId());
    }
    @Nonnull @Override public String getThumbnailUrl() throws ParsingException {
        page(); return EpornerParser.meta(document, "meta[property=og:image], meta[name=twitter:image]");
    }
    @Nonnull @Override public Description getDescription() throws ParsingException {
        page(); final String value = EpornerParser.meta(document, "meta[name=description], meta[property=og:description]");
        return value.isEmpty() ? Description.EMPTY_DESCRIPTION : new Description(value, Description.PLAIN_TEXT);
    }
    @Override public long getLength() throws ParsingException {
        page(); return EpornerParser.longValue(EpornerParser.meta(document, "meta[property=og:duration]"));
    }
    @Nonnull @Override public String getUploaderName() { return "EPORNER"; }
    @Nonnull @Override public String getUploaderUrl() { return EpornerParser.BASE + "/"; }
    @Nonnull @Override public List<String> getTags() throws ParsingException {
        page(); final String keywords = EpornerParser.meta(document, "meta[name=keywords]");
        if (keywords.isEmpty()) return Collections.emptyList();
        final List<String> tags = new ArrayList<>();
        for (final String value : keywords.split(",")) if (!value.trim().isEmpty()) tags.add(value.trim());
        return tags;
    }
    @Override public String getTextualUploadDate() { return ""; }
    @Override public List<AudioStream> getAudioStreams() { return Collections.emptyList(); }
    @Override public List<VideoStream> getVideoStreams() throws IOException, ExtractionException {
        page();
        final LinkedHashMap<String, VideoStream> streams = new LinkedHashMap<>();
        for (final EpornerStream stream : signedStreams) {
            streams.putIfAbsent(stream.url, new VideoStream.Builder().setId(stream.resolution)
                    .setContent(EpornerParser.markStream(stream.url, getUrl(), sessionCookies), true)
                    .setResolution(stream.resolution).setMediaFormat(MediaFormat.MPEG_4)
                    .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP).setIsVideoOnly(false).build());
        }
        if (!streams.isEmpty()) return new ArrayList<>(streams.values());
        final String source = EpornerParser.contentUrl(document);
        if (!source.isEmpty()) {
            streams.put(source, new VideoStream.Builder().setId("Auto")
                    .setContent(EpornerParser.markStream(source, getUrl(), sessionCookies), true)
                    .setResolution("MP4").setMediaFormat(MediaFormat.MPEG_4)
                    .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP).setIsVideoOnly(false).build());
        }
        // Download endpoints are rate-limited separately from the video CDN. Only use them when
        // the page does not publish its direct playback URL.
        if (streams.isEmpty()) for (final Element link : document.select("span.download-h264 a[href]")) {
            final String url = link.absUrl("href").replace("&amp;", "&");
            final String resolution = EpornerParser.resolution(link.text(), url);
            final String streamUrl = EpornerParser.markStream(url, getUrl(), sessionCookies);
            streams.putIfAbsent(url, new VideoStream.Builder().setId(resolution).setContent(streamUrl, true)
                    .setResolution(resolution).setMediaFormat(MediaFormat.MPEG_4)
                    .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP).setIsVideoOnly(false).build());
        }
        if (streams.isEmpty()) throw new ParsingException("Could not find EPORNER video URL");
        return new ArrayList<>(streams.values());
    }
    @Override public List<VideoStream> getVideoOnlyStreams() { return Collections.emptyList(); }
    @Override public StreamType getStreamType() { return StreamType.VIDEO_STREAM; }
    @Override public InfoItemsCollector<? extends InfoItem, ? extends InfoItemExtractor> getRelatedItems()
            throws IOException, ExtractionException {
        page(); final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        for (final EpornerItem item : EpornerParser.cards(document.selectFirst("#relateddiv"), 40)) {
            if (!getId().equals(item.id)) collector.commit(new EpornerItemExtractor(item));
        }
        return collector;
    }
    @Nonnull @Override public List<MetaInfo> getMetaInfo() { return Collections.emptyList(); }
    private void page() throws ParsingException { if (document == null) throw new ParsingException("EPORNER page was not fetched"); }
}

final class EpornerItem {
    final String id, url, title, thumbnail; final long duration; final long views;
    EpornerItem(final String id, final String url, final String title, final String thumbnail,
                final long duration, final long views) {
        this.id = id; this.url = url; this.title = title; this.thumbnail = thumbnail;
        this.duration = duration; this.views = views;
    }
}

final class EpornerItemExtractor implements StreamInfoItemExtractor {
    private final EpornerItem item;
    EpornerItemExtractor(final EpornerItem item) { this.item = item; }
    @Override public String getName() { return item.title; }
    @Override public String getUrl() { return item.url; }
    @Override public String getThumbnailUrl() { return item.thumbnail; }
    @Override public StreamType getStreamType() { return StreamType.VIDEO_STREAM; }
    @Override public long getDuration() { return item.duration; }
    @Override public long getViewCount() { return item.views; }
    @Override public String getUploaderName() { return "EPORNER"; }
    @Override public String getUploaderUrl() { return EpornerParser.BASE + "/"; }
    @Nullable @Override public String getTextualUploadDate() { return null; }
    @Nullable @Override public DateWrapper getUploadDate() { return null; }
}

final class EpornerSearchPage {
    final List<EpornerItem> items; final boolean hasNext;
    EpornerSearchPage(final List<EpornerItem> items, final boolean hasNext) { this.items = items; this.hasNext = hasNext; }
}

final class EpornerStream {
    final String url;
    final String resolution;

    EpornerStream(final String url, final String resolution) {
        this.url = url;
        this.resolution = resolution;
    }
}

final class EpornerParser {
    static final String BASE = "https://www.eporner.com";
    private static final Pattern ID = Pattern.compile("/(?:video-|hd-porn/)([^/?#]+)/?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAGE = Pattern.compile("[?&]page=(\\d+)");
    private static final Pattern RESOLUTION = Pattern.compile("(\\d{3,4}p|4k)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_HASH = Pattern.compile(
            "EP\\.video\\.player\\.hash\\s*=\\s*['\\\"]([0-9a-f]{32})['\\\"]",
            Pattern.CASE_INSENSITIVE);
    private EpornerParser() { }
    static String kioskUrl() { return BASE + "/cat/all/"; }
    static String homeUrl() { return BASE + "/"; }
    static Document fetch(final String url) throws IOException, ExtractionException {
        final Response response = NewPipe.getDownloader().get(normalize(url), headers());
        return Jsoup.parse(response.responseBody(), normalize(url));
    }
    static EpornerSearchPage search(final String query, final int page) throws IOException, ExtractionException {
        try {
            final Response response = NewPipe.getDownloader().get(searchUrl(query, page), headers());
            if (response.responseCode() != 200) {
                return searchHtml(query, page);
            }
            final JsonObject root = JsonParser.object().from(response.responseBody());
            final Object value = root.get("videos");
            final List<EpornerItem> items = value instanceof JsonArray ? items((JsonArray) value, 40) : Collections.emptyList();
            if (items.isEmpty()) {
                return searchHtml(query, page);
            }
            final long total = longValue(root.get("total_count"));
            final long reportedPerPage = longValue(root.get("per_page"));
            final long perPage = reportedPerPage > 0 ? reportedPerPage : 40L;
            final long availablePages = longValue(root.get("available_pages"));
            final boolean hasNextPage = availablePages > page
                    || total > (long) page * perPage;

            return new EpornerSearchPage(items, hasNextPage);
        } catch (final Exception exception) {
            if (exception instanceof ExtractionException) throw (ExtractionException) exception;
            return searchHtml(query, page);
        }
    }
    private static EpornerSearchPage searchHtml(final String query, final int page)
            throws IOException, ExtractionException {
        final String form = "search=" + encode(query) + "&searchtype=video";
        final Response response = NewPipe.getDownloader().post(searchHtmlUrl(page), formHeaders(),
                form.getBytes(StandardCharsets.UTF_8));
        if (response.responseCode() != 200) {
            throw new ParsingException("EPORNER search returned HTTP " + response.responseCode());
        }
        final Document document = Jsoup.parse(response.responseBody(), response.latestUrl());
        final List<EpornerItem> values = cards(document, 40);
        final boolean hasNextPage = document.selectFirst("a[rel=next], .pagination .next a, .pagination a.next")
                != null;
        return new EpornerSearchPage(values, !values.isEmpty() && hasNextPage);
    }
    static String searchUrl(final String query, final int page) {
        return BASE + "/api/v2/video/search/?query=" + encode(query) + "&per_page=40&page="
                + Math.max(1, page) + "&thumbsize=medium&order=latest&gay=0&lq=0&format=json";
    }
    static List<EpornerStream> fetchSignedStreams(final Downloader downloader,
                                                   final Document document,
                                                   final String videoId,
                                                   final String sessionCookies) {
        final String hash = playerHash(document);
        if (hash.isEmpty()) return Collections.emptyList();
        try {
            final Response response = downloader.get(playerUrl(videoId, hash), headers(sessionCookies));
            if (response.responseCode() != 200) return Collections.emptyList();
            final JsonObject sources = JsonParser.object().from(response.responseBody())
                    .getObject("sources");
            final JsonObject mp4 = sources == null ? null : sources.getObject("mp4");
            if (mp4 == null) return Collections.emptyList();

            final List<EpornerStream> streams = new ArrayList<>();
            for (final Map.Entry<String, Object> entry : mp4.entrySet()) {
                if (!(entry.getValue() instanceof JsonObject)) continue;
                final JsonObject value = (JsonObject) entry.getValue();
                final String url = normalize(value.getString("src", ""));
                if (url.isEmpty()) continue;
                streams.add(new EpornerStream(url, first(value.getString("labelShort", ""),
                        entry.getKey(), resolution("", url))));
            }
            return streams;
        } catch (final Exception ignored) {
            return Collections.emptyList();
        }
    }
    private static String playerUrl(final String videoId, final String hash) {
        return BASE + "/xhr/video/" + encode(videoId)
                + "?hash=" + encode(hash)
                + "&domain=www.eporner.com&pixelRatio=1&playerWidth=0&playerHeight=0"
                + "&fallback=false&embed=false&supportedFormats=mp4";
    }
    private static String playerHash(final Document document) {
        final Matcher matcher = PLAYER_HASH.matcher(document.html());
        if (!matcher.find()) return "";
        final String value = matcher.group(1);
        final StringBuilder result = new StringBuilder(28);
        try {
            for (int offset = 0; offset < value.length(); offset += 8) {
                result.append(Long.toString(Long.parseLong(value.substring(offset, offset + 8), 16), 36));
            }
            return result.toString();
        } catch (final NumberFormatException ignored) {
            return "";
        }
    }
    private static String searchHtmlUrl(final int page) {
        return page <= 1 ? BASE + "/search/" : BASE + "/search/" + page + "/";
    }
    static int pageNumber(final String url) { final Matcher m = PAGE.matcher(url); return m.find() ? Integer.parseInt(m.group(1)) : 1; }
    static List<EpornerItem> cards(final Element scope, final int maximum) {
        if (scope == null) return Collections.emptyList();
        final LinkedHashMap<String, EpornerItem> values = new LinkedHashMap<>();
        for (final Element card : scope.select("div.mb[data-id]")) {
            final Element link = card.selectFirst("p.mbtit a[href], .mbimg a[href]");
            if (link == null) continue;
            final String id = first(card.attr("data-id"), idOrEmpty(link.absUrl("href")));
            final Element image = card.selectFirst("div.mbimg img");
            final String title = first(link.text(), link.attr("title"), image == null ? "" : image.attr("alt"));
            if (id.isEmpty() || title.isEmpty()) continue;
            final String thumbnail = image == null ? "" : first(image.absUrl("data-src"), image.absUrl("src"));
            final Element duration = card.selectFirst("span.mbtim");
            final Element views = card.selectFirst("span.mbvie");
            values.putIfAbsent(id, new EpornerItem(id, normalize(link.absUrl("href")), clean(title), thumbnail,
                    duration(duration == null ? "" : duration.text()), longValue(views == null ? "" : views.text())));
            if (values.size() >= maximum) break;
        }
        return new ArrayList<>(values.values());
    }
    static Map<String, List<String>> headers() {
        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("Referer", Collections.singletonList(BASE + "/"));
        headers.put("User-Agent", Collections.singletonList("Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"));
        headers.put("Accept-Language", Collections.singletonList("ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7"));
        return headers;
    }
    private static Map<String, List<String>> headers(final String sessionCookies) {
        final Map<String, List<String>> headers = headers();
        if (sessionCookies != null && !sessionCookies.isEmpty()) {
            headers.put("Cookie", Collections.singletonList(sessionCookies));
        }
        headers.put("Accept", Collections.singletonList("*/*"));
        return headers;
    }
    private static Map<String, List<String>> formHeaders() {
        final Map<String, List<String>> headers = headers();
        headers.put("Content-Type", Collections.singletonList("application/x-www-form-urlencoded; charset=UTF-8"));
        headers.put("Accept", Collections.singletonList("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"));
        return headers;
    }
    static boolean isVideoUrl(final String url) { try { return url != null && URI.create(normalize(url)).getHost().endsWith("eporner.com") && ID.matcher(URI.create(normalize(url)).getPath()).find(); } catch (final Exception ignored) { return false; } }
    static String id(final String url) throws ParsingException { final Matcher m = ID.matcher(normalize(url)); if (m.find()) return m.group(1); throw new ParsingException("Could not extract EPORNER id: " + url); }
    static String normalize(final String url) { if (url == null) return ""; final String value = url.trim().replace("&amp;", "&"); return value.startsWith("/") ? BASE + value : value.startsWith("//") ? "https:" + value : value; }
    static String markStream(final String url, final String pageUrl, final String sessionCookies) {
        final String cookiePart = sessionCookies.isEmpty() ? "" : "&cookie=" + encode(sessionCookies);
        return normalize(url) + "#eporner=1&ref=" + encode(pageUrl) + cookiePart;
    }
    static String sessionCookies(final Response response) {
        final List<String> values = new ArrayList<>();
        for (final Map.Entry<String, List<String>> entry : response.responseHeaders().entrySet()) {
            if (entry.getKey() == null || !"Set-Cookie".equalsIgnoreCase(entry.getKey())) continue;
            for (final String header : entry.getValue()) {
                if (header == null) continue;
                final String value = header.split(";", 2)[0].trim();
                if (value.startsWith("EPRNS=") || value.startsWith("PHPSESSID=")) values.add(value);
            }
        }
        return String.join("; ", values);
    }
    static String meta(final Document doc, final String selector) { final Element e = doc.selectFirst(selector); return e == null ? "" : clean(e.hasAttr("content") ? e.attr("content") : e.text()); }
    static String cleanTitle(final String title, final String fallback) { final String clean = title.replaceFirst("\\s*-\\s*EPORNER(?:\\.COM)?\\s*$", "").trim(); return clean.isEmpty() ? fallback : clean; }
    static String contentUrl(final Document doc) {
        for (final Element script : doc.select("script[type=application/ld+json]")) {
            try { final JsonObject json = JsonParser.object().from(script.data()); final String value = string(json.get("contentUrl")); if (!value.isEmpty()) return value; } catch (final Exception ignored) { }
        }
        return "";
    }
    static String resolution(final String label, final String url) { final Matcher m = RESOLUTION.matcher(label + " " + url); return m.find() ? m.group(1).toUpperCase(java.util.Locale.ROOT) : "MP4"; }
    static long duration(final String text) { final String[] p = text.trim().split(":"); try { if (p.length == 2) return Long.parseLong(p[0]) * 60L + Long.parseLong(p[1]); if (p.length == 3) return Long.parseLong(p[0]) * 3600L + Long.parseLong(p[1]) * 60L + Long.parseLong(p[2]); } catch (final NumberFormatException ignored) { } return -1; }
    static long longValue(final Object value) { try { return Long.parseLong(string(value).replaceAll("[^0-9]", "")); } catch (final NumberFormatException ignored) { return -1; } }
    private static List<EpornerItem> items(final JsonArray array, final int maximum) {
        final List<EpornerItem> result = new ArrayList<>();
        for (final Object entry : array) { if (!(entry instanceof JsonObject)) continue; final JsonObject item = (JsonObject) entry;
            final String id = string(item.get("video_id")); final String url = normalize(string(item.get("url"))); final String title = clean(string(item.get("title"))); if (id.isEmpty() || url.isEmpty() || title.isEmpty()) continue;
            String thumbnail = ""; final Object thumb = item.get("default_thumb"); if (thumb instanceof JsonObject) thumbnail = string(((JsonObject) thumb).get("src"));
            result.add(new EpornerItem(id, url, title, thumbnail, longValue(item.get("length_sec")), longValue(item.get("views")))); if (result.size() >= maximum) break;
        } return result;
    }
    private static String encode(final String value) { try { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name()); } catch (final Exception e) { throw new IllegalStateException("UTF-8 unavailable", e); } }
    private static String idOrEmpty(final String url) { try { return id(url); } catch (final ParsingException ignored) { return ""; } }
    private static String first(final String... values) { for (final String value : values) if (value != null && !value.trim().isEmpty()) return value.trim(); return ""; }
    private static String string(final Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static String clean(final String value) { return Jsoup.parse(value == null ? "" : value).text().replaceAll("\\s+", " ").trim(); }
}
