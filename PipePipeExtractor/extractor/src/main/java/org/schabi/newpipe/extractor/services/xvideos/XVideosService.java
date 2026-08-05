package org.schabi.newpipe.extractor.services.xvideos;

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
import org.schabi.newpipe.extractor.MetaInfo;
import org.schabi.newpipe.extractor.MediaFormat;
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
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Extracts publicly embedded XVideos metadata without requesting advertising or tracking endpoints. */
public final class XVideosService extends StreamingService {
    public XVideosService(final int id) {
        super(id, "XVideos", Collections.singletonList(ServiceInfo.MediaCapability.VIDEO));
    }

    @Override public String getBaseUrl() { return XVideosParser.BASE; }
    @Override public LinkHandlerFactory getStreamLHFactory() { return XVideosLinks.INSTANCE; }
    @Override public ListLinkHandlerFactory getChannelLHFactory() {
        return XVideosChannelLinkHandlerFactory.getInstance();
    }
    @Override public ListLinkHandlerFactory getChannelTabLHFactory() { return null; }
    @Override public ListLinkHandlerFactory getPlaylistLHFactory() { return null; }
    @Override public SearchQueryHandlerFactory getSearchQHFactory() { return XVideosSearchFactory.INSTANCE; }
    @Override public ListLinkHandlerFactory getCommentsLHFactory() { return null; }
    @Override public SearchExtractor getSearchExtractor(final SearchQueryHandler handler) {
        return new XVideosSearchExtractor(this, handler);
    }
    @Override public SuggestionExtractor getSuggestionExtractor() { return null; }
    @Override public SubscriptionExtractor getSubscriptionExtractor() { return null; }
    @Override public KioskList getKioskList() { return new KioskList(this); }
    @Override public ChannelExtractor getChannelExtractor(final ListLinkHandler handler) {
        return new XVideosChannelExtractor(this, handler);
    }
    @Override public ChannelTabExtractor getChannelTabExtractor(final ListLinkHandler handler)
            throws ExtractionException { throw new ExtractionException("XVideos channel tabs unavailable"); }
    @Override public PlaylistExtractor getPlaylistExtractor(final ListLinkHandler handler)
            throws ExtractionException { throw new ExtractionException("XVideos playlists unavailable"); }
    @Override public StreamExtractor getStreamExtractor(final LinkHandler handler) {
        return new XVideosStreamExtractor(this, handler);
    }
    @Override public CommentsExtractor getCommentsExtractor(final ListLinkHandler handler) { return null; }
    @Override public BulletCommentsExtractor getBulletCommentsExtractor(final ListLinkHandler handler) { return null; }
}

final class XVideosLinks extends LinkHandlerFactory {
    static final XVideosLinks INSTANCE = new XVideosLinks();

    @Override public String getId(final String url) throws ParsingException { return XVideosParser.id(url); }
    @Override public String getUrl(final String id) { return XVideosParser.BASE + "/video." + id + "/_"; }
    @Override public boolean onAcceptUrl(final String url) {
        return url != null && url.matches("https?://(?:www\\.)?xvideos\\.com/video\\.[^/?#]+(?:[/?#].*)?");
    }
}

final class XVideosSearchFactory extends SearchQueryHandlerFactory {
    static final XVideosSearchFactory INSTANCE = new XVideosSearchFactory();

    @Override public String getUrl(final String query, final List<FilterItem> contentFilters,
                                   final List<FilterItem> sortFilter) {
        return XVideosParser.searchUrl(query, 0);
    }
}

final class XVideosSearchExtractor extends SearchExtractor {
    XVideosSearchExtractor(final StreamingService service, final SearchQueryHandler handler) {
        super(service, handler);
    }

    @Override public void onFetchPage(@Nonnull final Downloader downloader) { }

    @Override protected InfoItemsPage<InfoItem> getInitialPageInternal()
            throws IOException, ExtractionException {
        return pageForUrl(XVideosParser.searchUrl(getSearchString(), 0), 0);
    }

    @Override protected InfoItemsPage<InfoItem> getPageInternal(final Page page)
            throws IOException, ExtractionException {
        return pageForUrl(page.getUrl(), XVideosParser.pageNumber(page.getUrl()));
    }

    private InfoItemsPage<InfoItem> pageForUrl(final String url, final int page)
            throws IOException, ExtractionException {
        final Document document = XVideosParser.fetch(url);
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        for (final XVideosItem item : XVideosParser.cards(document, 40)) {
            collector.commit(new XVideosItemExtractor(item));
        }
        final Page nextPage = XVideosParser.hasNextPage(document)
                ? new Page(XVideosParser.searchUrl(getSearchString(), page + 1)) : null;
        return new ListExtractor.InfoItemsPage<>(collector, nextPage);
    }
}

final class XVideosStreamExtractor extends StreamExtractor {
    private Document page;

    XVideosStreamExtractor(final StreamingService service, final LinkHandler handler) {
        super(service, handler);
    }

    @Override public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        page = XVideosParser.fetch(getUrl());
    }

    private void requirePage() throws ParsingException {
        if (page == null) throw new ParsingException("XVideos page was not fetched");
    }

    @Nonnull @Override public String getName() throws ParsingException {
        requirePage();
        return XVideosParser.firstNonEmpty(XVideosParser.scriptValue(page, XVideosParser.TITLE),
                XVideosParser.value(page, "meta[property=og:title], h1", getId()));
    }

    @Nonnull @Override public String getThumbnailUrl() throws ParsingException {
        requirePage();
        return XVideosParser.firstNonEmpty(XVideosParser.scriptValue(page, XVideosParser.THUMBNAIL),
                XVideosParser.value(page, "meta[property=og:image]", ""));
    }

    @Nonnull @Override public Description getDescription() throws ParsingException {
        requirePage();
        final String description = XVideosParser.value(page,
                "meta[name=description], meta[property=og:description]", "");
        return description.isEmpty() ? Description.EMPTY_DESCRIPTION
                : new Description(description, Description.PLAIN_TEXT);
    }

    @Override public long getLength() throws ParsingException {
        requirePage();
        return XVideosParser.duration(XVideosParser.value(page,
                "meta[itemprop=duration], .duration", ""));
    }

    @Nonnull @Override public String getUploaderName() throws ParsingException {
        requirePage();
        return XVideosParser.firstNonEmpty(XVideosParser.value(page,
                ".main-uploader a, [class*=uploader] a", ""),
                XVideosParser.scriptValue(page, XVideosParser.UPLOADER), "XVideos");
    }

    @Nonnull @Override public String getUploaderUrl() throws ParsingException {
        requirePage();
        final Element link = page.selectFirst(".main-uploader a[href], [class*=uploader] a[href]");
        if (link != null) {
            return link.absUrl("href");
        }
        final String uploader = XVideosParser.scriptValue(page, XVideosParser.UPLOADER);
        if (!uploader.matches("[A-Za-z0-9_-]+")) {
            return XVideosParser.BASE;
        }
        return XVideosParser.isChannel(page) ? XVideosParser.BASE + "/" + uploader
                : XVideosParser.BASE + "/profiles/" + uploader;
    }

    @Nonnull @Override public List<String> getTags() throws ParsingException {
        requirePage();
        final LinkedHashMap<String, String> tags = new LinkedHashMap<>();
        for (final Element tag : page.select("a.is-keyword, a[href*=/tags/], a[href*=/k/].keyword")) {
            final String value = tag.text().trim();
            if (!value.isEmpty()) tags.put(value, value);
        }
        return new ArrayList<>(tags.values());
    }

    @Override public String getTextualUploadDate() { return ""; }
    @Override public List<AudioStream> getAudioStreams() { return Collections.emptyList(); }

    @Override public List<VideoStream> getVideoStreams() throws IOException, ExtractionException {
        requirePage();
        final List<VideoStream> streams = new ArrayList<>();
        final String hls = XVideosParser.scriptValue(page, XVideosParser.HLS);
        if (!hls.isEmpty()) {
            streams.add(new VideoStream.Builder().setId("hls").setContent(hls, true)
                    .setManifestUrl(hls).setResolution("HLS").setDeliveryMethod(DeliveryMethod.HLS)
                    .setMediaFormat(MediaFormat.MPEG_4).setIsVideoOnly(false).build());
        }
        final String high = XVideosParser.scriptValue(page, XVideosParser.HIGH);
        final String low = XVideosParser.scriptValue(page, XVideosParser.LOW);
        XVideosParser.addProgressiveStream(streams, "high", high);
        if (!low.equals(high)) XVideosParser.addProgressiveStream(streams, "low", low);
        if (streams.isEmpty()) throw new ParsingException("Could not find XVideos video URL");
        return streams;
    }

    @Override public List<VideoStream> getVideoOnlyStreams() { return Collections.emptyList(); }
    @Override public StreamType getStreamType() { return StreamType.VIDEO_STREAM; }

    @Override public InfoItemsCollector<? extends InfoItem, ? extends InfoItemExtractor> getRelatedItems()
            throws IOException, ExtractionException {
        requirePage();
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        for (final XVideosItem item : XVideosParser.related(page, 40)) {
            if (!getId().equals(item.id)) collector.commit(new XVideosItemExtractor(item));
        }
        return collector;
    }

    @Nonnull @Override public List<MetaInfo> getMetaInfo() { return Collections.emptyList(); }
}

final class XVideosItem {
    final String id;
    final String url;
    final String title;
    final String thumbnail;
    final long duration;
    final String uploaderName;
    final String uploaderUrl;

    XVideosItem(final String id, final String url, final String title, final String thumbnail,
                final long duration, final String uploaderName, final String uploaderUrl) {
        this.id = id;
        this.url = url;
        this.title = title;
        this.thumbnail = thumbnail;
        this.duration = duration;
        this.uploaderName = uploaderName;
        this.uploaderUrl = uploaderUrl;
    }
}

final class XVideosItemExtractor implements StreamInfoItemExtractor {
    private final XVideosItem item;
    XVideosItemExtractor(final XVideosItem item) { this.item = item; }
    @Override public String getName() { return item.title; }
    @Override public String getUrl() { return item.url; }
    @Override public String getThumbnailUrl() { return item.thumbnail; }
    @Override public StreamType getStreamType() { return StreamType.VIDEO_STREAM; }
    @Override public long getDuration() { return item.duration; }
    @Override public long getViewCount() { return -1; }
    @Override public String getUploaderName() {
        return item.uploaderName.isEmpty() ? "XVideos" : item.uploaderName;
    }
    @Override public String getUploaderUrl() {
        return item.uploaderUrl.isEmpty() ? XVideosParser.BASE : item.uploaderUrl;
    }
    @Nullable @Override public String getTextualUploadDate() { return null; }
    @Nullable @Override public DateWrapper getUploadDate() { return null; }
}

final class XVideosParser {
    static final String BASE = "https://www.xvideos.com";
    static final Pattern TITLE = Pattern.compile("html5player\\.setVideoTitle\\('((?:\\\\.|[^'])*)'\\)");
    static final Pattern THUMBNAIL = Pattern.compile("html5player\\.setThumbUrl\\('((?:\\\\.|[^'])*)'\\)");
    static final Pattern HLS = Pattern.compile("html5player\\.setVideoHLS\\('((?:\\\\.|[^'])*)'\\)");
    static final Pattern HIGH = Pattern.compile("html5player\\.setVideoUrlHigh\\('((?:\\\\.|[^'])*)'\\)");
    static final Pattern LOW = Pattern.compile("html5player\\.setVideoUrlLow\\('((?:\\\\.|[^'])*)'\\)");
    static final Pattern UPLOADER = Pattern.compile("html5player\\.setUploaderName\\('((?:\\\\.|[^'])*)'\\)");
    private static final Pattern IS_CHANNEL = Pattern.compile("\\\"is_channel\\\"\\s*:\\s*1");
    private static final Pattern VIDEO_ID = Pattern.compile("/video\\.([^/?#]+)");
    private static final Pattern RELATED = Pattern.compile(
            "(?:var\\s+|window\\.)video_related\\s*=\\s*(\\[.*?\\])(?=;|\\s*window\\.|\\s*</script>)",
            Pattern.DOTALL);
    private static final Pattern PAGE = Pattern.compile("[?&]p=(\\d+)");
    private static final Pattern CLOCK_DURATION = Pattern.compile("(\\d+):(\\d{2})(?::(\\d{2}))?");
    private static final Pattern WORD_DURATION = Pattern.compile("(?:(\\d+)\\s*(?:h|hour|時間))?\\s*(?:(\\d+)\\s*(?:min|minute|分))?");

    private XVideosParser() { }

    static Document fetch(final String url) throws IOException, ExtractionException {
        final Response response = NewPipe.getDownloader().get(url, headers());
        return Jsoup.parse(response.responseBody(), url);
    }

    static Map<String, List<String>> headers() {
        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("Referer", Collections.singletonList(BASE + "/"));
        headers.put("User-Agent", Collections.singletonList(
                "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"));
        headers.put("Accept-Language", Collections.singletonList("ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7"));
        return headers;
    }

    static String id(final String url) throws ParsingException {
        try {
            final Matcher matcher = VIDEO_ID.matcher(URI.create(url).getPath());
            if (matcher.find()) return matcher.group(1);
        } catch (final IllegalArgumentException ignored) { }
        throw new ParsingException("Could not extract XVideos id: " + url);
    }

    static String searchUrl(final String query, final int page) {
        return BASE + "/?k=" + encode(query) + "&sort=relevance" + (page > 0 ? "&p=" + page : "");
    }

    static int pageNumber(final String url) {
        final Matcher matcher = PAGE.matcher(url);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    static String encode(final String query) {
        try {
            return URLEncoder.encode(query == null ? "" : query, StandardCharsets.UTF_8.name());
        } catch (final Exception exception) {
            throw new IllegalStateException("UTF-8 is unavailable", exception);
        }
    }

    static String value(final Document document, final String selector, final String fallback) {
        final Element element = document.selectFirst(selector);
        if (element == null) return fallback;
        final String raw = element.hasAttr("content") ? element.attr("content") : element.text();
        final String value = Jsoup.parse(raw).text().trim();
        return value.isEmpty() ? fallback : value;
    }

    static String scriptValue(final Document document, final Pattern pattern) {
        final Matcher matcher = pattern.matcher(document.html());
        return matcher.find() ? unescapeJavascript(matcher.group(1)) : "";
    }

    static boolean isChannel(final Document document) {
        return IS_CHANNEL.matcher(document.html()).find();
    }

    static List<XVideosItem> cards(final Element root, final int maximum) {
        final LinkedHashMap<String, XVideosItem> items = new LinkedHashMap<>();
        for (final Element card : root.select("div.frame-block")) {
            final Element link = card.selectFirst("a[href*=/video.]");
            if (link == null) continue;
            final String url = link.absUrl("href");
            final String id = idOrEmpty(url);
            final Element image = card.selectFirst("img[data-src], img[data-original], img[data-lazy-src], img");
            final Element metadata = card.selectFirst("div.thumb-under");
            final Element titleLink = metadata == null ? null : metadata.selectFirst("a[href*=/video.]");
            final String title = firstNonEmpty(titleLink == null ? "" : titleLink.text(),
                    titleLink == null ? "" : titleLink.attr("title"), image == null ? "" : image.attr("alt"));
            if (id.isEmpty() || title.isEmpty()) continue;
            final String thumbnail = image == null ? "" : firstNonEmpty(
                    image.absUrl("data-src"), image.absUrl("data-original"),
                    image.absUrl("data-lazy-src"), firstSrc(image.attr("data-srcset")),
                    firstSrc(image.attr("srcset")), image.absUrl("src"));
            final Element duration = metadata == null ? null : metadata.selectFirst("span.duration");
            final Element uploader = card.selectFirst("a[href^=/profiles/], a[href^=/channels/], "
                    + "a[href^=/model/], a[href^=/pornstar/]");
            items.putIfAbsent(id, new XVideosItem(id, url, title, thumbnail,
                    duration(duration == null ? "" : duration.text()),
                    uploader == null ? "" : uploader.text(),
                    uploader == null ? "" : uploader.absUrl("href")));
            if (items.size() >= maximum) break;
        }
        return new ArrayList<>(items.values());
    }

    static List<XVideosItem> related(final Document document, final int maximum) {
        final Matcher matcher = RELATED.matcher(document.html());
        if (!matcher.find()) return Collections.emptyList();
        try {
            final JsonArray array = JsonParser.array().from(matcher.group(1));
            return itemsFromJson(array, maximum);
        } catch (final Exception ignored) {
            return Collections.emptyList();
        }
    }

    static ChannelVideos channelVideos(final String channelId, final int page)
            throws IOException, ExtractionException {
        final String url = channelVideosUrl(channelId, page);
        final Map<String, List<String>> requestHeaders = headers();
        requestHeaders.put("Accept", Collections.singletonList("application/json, text/javascript, */*; q=0.01"));
        requestHeaders.put("Content-Type", Collections.singletonList(
                "application/x-www-form-urlencoded; charset=UTF-8"));
        requestHeaders.put("X-Requested-With", Collections.singletonList("XMLHttpRequest"));
        requestHeaders.put("Origin", Collections.singletonList(BASE));
        requestHeaders.put("Referer", Collections.singletonList(BASE + "/" + channelSlug(channelId)));
        final byte[] form = "main_cats%5B%5D=straight&main_cats%5B%5D=shemale&main_cats%5B%5D=gay"
                .getBytes(StandardCharsets.UTF_8);
        try {
            final JsonObject response = JsonParser.object().from(
                    NewPipe.getDownloader().post(url, requestHeaders, form).responseBody());
            final Object videosValue = response.get("videos");
            if (!(videosValue instanceof JsonArray)) {
                throw new ParsingException("XVideos channel response does not contain videos");
            }
            final List<XVideosItem> videos = itemsFromJson((JsonArray) videosValue, 40);
            final long total = longValue(response.get("nb_videos"));
            final long perPage = Math.max(1, longValue(response.get("nb_per_page")));
            return new ChannelVideos(videos, total > ((long) page + 1L) * perPage);
        } catch (final ParsingException exception) {
            throw exception;
        } catch (final Exception exception) {
            throw new ParsingException("Could not parse XVideos channel videos", exception);
        }
    }

    static String channelVideosUrl(final String channelId, final int page) {
        return BASE + "/channels/" + channelSlug(channelId) + "/videos/best/straight/" + Math.max(0, page);
    }

    static int channelPageNumber(final String url) {
        final Matcher matcher = Pattern.compile("/videos/best/[^/]+/(\\d+)(?:[?#].*)?$").matcher(url);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private static List<XVideosItem> itemsFromJson(final JsonArray array, final int maximum) {
        final LinkedHashMap<String, XVideosItem> items = new LinkedHashMap<>();
        for (final Object candidate : array) {
            if (!(candidate instanceof JsonObject)) continue;
            final JsonObject item = (JsonObject) candidate;
            final String id = firstNonEmpty(string(item.get("eid")), idOrEmpty(string(item.get("u"))));
            final String title = firstNonEmpty(string(item.get("tf")), string(item.get("t")));
            if (id.isEmpty() || title.isEmpty()) continue;
            final String url = BASE + "/video." + id + "/_";
            items.putIfAbsent(id, new XVideosItem(id, url, title, absolute(string(item.get("i"))),
                    duration(string(item.get("d"))), string(item.get("pn")),
                    absolute(string(item.get("pu")))));
            if (items.size() >= maximum) break;
        }
        return new ArrayList<>(items.values());
    }

    private static String channelSlug(final String channelId) {
        final String normalized = channelId == null ? "" : channelId.replaceAll("^/+|/+$", "");
        final int separator = normalized.lastIndexOf('/');
        return separator >= 0 ? normalized.substring(separator + 1) : normalized;
    }

    private static long longValue(final Object value) {
        try {
            return Long.parseLong(string(value));
        } catch (final NumberFormatException ignored) {
            return 0;
        }
    }

    static final class ChannelVideos {
        final List<XVideosItem> items;
        final boolean hasNextPage;

        ChannelVideos(final List<XVideosItem> items, final boolean hasNextPage) {
            this.items = items;
            this.hasNextPage = hasNextPage;
        }
    }

    static boolean hasNextPage(final Document document) {
        return document.selectFirst("a[rel=next], .pagination a.next, .pagination a[title*=Next]") != null;
    }

    static void addProgressiveStream(final List<VideoStream> streams, final String id, final String url) {
        if (url.isEmpty()) return;
        streams.add(new VideoStream.Builder().setId(id).setContent(url, true).setResolution("MP4")
                .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP).setMediaFormat(MediaFormat.MPEG_4)
                .setIsVideoOnly(false).build());
    }

    static long duration(final String text) {
        final Matcher clock = CLOCK_DURATION.matcher(text == null ? "" : text);
        if (clock.find()) {
            final long first = Long.parseLong(clock.group(1));
            final long second = Long.parseLong(clock.group(2));
            return clock.group(3) == null ? first * 60 + second
                    : first * 3600 + second * 60 + Long.parseLong(clock.group(3));
        }
        final Matcher words = WORD_DURATION.matcher(text == null ? "" : text);
        if (words.find() && (words.group(1) != null || words.group(2) != null)) {
            return (words.group(1) == null ? 0 : Long.parseLong(words.group(1)) * 3600)
                    + (words.group(2) == null ? 0 : Long.parseLong(words.group(2)) * 60);
        }
        return -1;
    }

    static String firstNonEmpty(final String... values) {
        for (final String value : values) if (value != null && !value.trim().isEmpty()) return value.trim();
        return "";
    }

    private static String idOrEmpty(final String url) {
        try { return id(url); } catch (final ParsingException ignored) { return ""; }
    }

    private static String absolute(final String url) {
        if (url == null || url.isEmpty()) return "";
        if (url.startsWith("//")) return "https:" + url;
        return url.startsWith("/") ? BASE + url : url;
    }

    private static String firstSrc(final String srcset) {
        if (srcset == null || srcset.trim().isEmpty()) return "";
        final String first = srcset.split(",", 2)[0].trim();
        final int space = first.indexOf(' ');
        return space < 0 ? first : first.substring(0, space);
    }

    private static String string(final Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    private static String unescapeJavascript(final String value) {
        return value.replace("\\\\/", "/").replace("\\\\'", "'")
                .replace("\\\\\"", "\"").replace("\\\\\\\\", "\\");
    }
}
