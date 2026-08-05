package org.schabi.newpipe.extractor.services.spankbang;

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
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
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
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor;
import org.schabi.newpipe.extractor.comments.CommentsExtractor;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsExtractor;
import org.schabi.newpipe.extractor.localization.DateWrapper;

import java.io.IOException;
import java.net.URI;
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** SpankBang video service. Only first-party HTML and media CDN URLs are requested. */
public final class SpankBangService extends StreamingService {
    public SpankBangService(final int id) {
        super(id, "SpankBang", Collections.singletonList(ServiceInfo.MediaCapability.VIDEO));
    }

    @Override public String getBaseUrl() { return SpankBangParser.BASE_URL; }
    @Override public LinkHandlerFactory getStreamLHFactory() { return SpankBangStreamLinkHandlerFactory.INSTANCE; }
    @Override public ListLinkHandlerFactory getChannelLHFactory() { return null; }
    @Override public ListLinkHandlerFactory getChannelTabLHFactory() { return null; }
    @Override public ListLinkHandlerFactory getPlaylistLHFactory() { return null; }
    @Override public SearchQueryHandlerFactory getSearchQHFactory() { return SpankBangSearchQueryHandlerFactory.INSTANCE; }
    @Override public ListLinkHandlerFactory getCommentsLHFactory() { return null; }
    @Override public SearchExtractor getSearchExtractor(final SearchQueryHandler handler) {
        return new SpankBangSearchExtractor(this, handler);
    }
    @Override public SuggestionExtractor getSuggestionExtractor() { return null; }
    @Override public SubscriptionExtractor getSubscriptionExtractor() { return null; }
    @Override public KioskList getKioskList() { return new KioskList(this); }
    @Override public ChannelExtractor getChannelExtractor(final ListLinkHandler handler) { return null; }
    @Override public ChannelTabExtractor getChannelTabExtractor(final ListLinkHandler handler)
            throws ExtractionException { throw new ExtractionException("SpankBang has no channel extractor"); }
    @Override public PlaylistExtractor getPlaylistExtractor(final ListLinkHandler handler)
            throws ExtractionException { throw new ExtractionException("SpankBang has no playlist extractor"); }
    @Override public StreamExtractor getStreamExtractor(final LinkHandler handler) {
        return new SpankBangStreamExtractor(this, handler);
    }
    @Override public CommentsExtractor getCommentsExtractor(final ListLinkHandler handler) { return null; }
    @Override public BulletCommentsExtractor getBulletCommentsExtractor(final ListLinkHandler handler) { return null; }
}

final class SpankBangStreamLinkHandlerFactory extends LinkHandlerFactory {
    static final SpankBangStreamLinkHandlerFactory INSTANCE = new SpankBangStreamLinkHandlerFactory();
    @Override public String getId(final String url) throws ParsingException { return SpankBangParser.id(url); }
    @Override public String getUrl(final String id) { return SpankBangParser.BASE_URL + "/" + id + "/video"; }
    @Override public boolean onAcceptUrl(final String url) { return SpankBangParser.isVideoUrl(url); }
}

final class SpankBangSearchQueryHandlerFactory extends SearchQueryHandlerFactory {
    static final SpankBangSearchQueryHandlerFactory INSTANCE = new SpankBangSearchQueryHandlerFactory();
    @Override public String getUrl(final String query, final List<FilterItem> content,
                                   final List<FilterItem> sort) {
        return SpankBangParser.BASE_URL + "/s/" + SpankBangParser.encode(query) + "/";
    }
}

final class SpankBangSearchExtractor extends SearchExtractor {
    SpankBangSearchExtractor(final StreamingService service, final SearchQueryHandler handler) {
        super(service, handler);
    }
    @Override public void onFetchPage(@Nonnull final Downloader downloader) { }
    @Override protected InfoItemsPage<InfoItem> getInitialPageInternal()
            throws IOException, ExtractionException {
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        final Document page = SpankBangParser.fetch(SpankBangParser.BASE_URL + "/s/"
                + SpankBangParser.encode(getSearchString()) + "/");
        for (final SpankBangItem item : SpankBangParser.searchCards(page, 40)) {
            collector.commit(new SpankBangInfoItemExtractor(item));
        }
        return new ListExtractor.InfoItemsPage<>(collector, null);
    }
    @Override protected InfoItemsPage<InfoItem> getPageInternal(final Page page) {
        return InfoItemsPage.emptyPage();
    }
}

final class SpankBangStreamExtractor extends StreamExtractor {
    private static final int MAX_RELATED = 30;
    private Document document;

    SpankBangStreamExtractor(final StreamingService service, final LinkHandler handler) {
        super(service, handler);
    }
    @Override public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        document = SpankBangParser.fetch(getUrl());
    }
    @Nonnull @Override public String getName() throws ParsingException {
        page();
        return SpankBangParser.title(document, getId());
    }
    @Nonnull @Override public String getThumbnailUrl() throws ParsingException {
        page();
        return SpankBangParser.meta(document, "meta[property=og:image]");
    }
    @Nonnull @Override public Description getDescription() throws ParsingException {
        page();
        final String value = SpankBangParser.meta(document, "meta[name=description], meta[property=og:description]");
        return value.isEmpty() ? Description.EMPTY_DESCRIPTION : new Description(value, Description.PLAIN_TEXT);
    }
    @Override public long getLength() throws ParsingException { page(); return SpankBangParser.duration(document); }
    @Nonnull @Override public String getUploaderName() { return "SpankBang"; }
    @Nonnull @Override public String getUploaderUrl() { return SpankBangParser.BASE_URL + "/"; }
    @Nonnull @Override public List<String> getTags() { return Collections.emptyList(); }
    @Override public String getTextualUploadDate() { return ""; }
    @Override public List<AudioStream> getAudioStreams() { return Collections.emptyList(); }
    @Override public List<VideoStream> getVideoStreams() throws IOException, ExtractionException {
        page();
        final List<VideoStream> streams = new ArrayList<>();
        for (final SpankBangSource source : SpankBangParser.sources(document, getUrl())) {
            final VideoStream.Builder builder = new VideoStream.Builder()
                    .setId(source.id).setContent(source.url, true).setResolution(source.resolution)
                    .setIsVideoOnly(false).setDeliveryMethod(source.method).setMediaFormat(MediaFormat.MPEG_4);
            if (source.method == DeliveryMethod.HLS) { builder.setManifestUrl(source.cleanUrl); }
            streams.add(builder.build());
        }
        if (streams.isEmpty()) { throw new ParsingException("Could not find SpankBang video URL"); }
        return streams;
    }
    @Override public List<VideoStream> getVideoOnlyStreams() { return Collections.emptyList(); }
    @Override public StreamType getStreamType() { return StreamType.VIDEO_STREAM; }
    @Override public InfoItemsCollector<? extends InfoItem, ? extends InfoItemExtractor> getRelatedItems()
            throws IOException, ExtractionException {
        page();
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        final Set<String> ids = new HashSet<>();
        for (final SpankBangItem item : SpankBangParser.relatedCards(document, MAX_RELATED)) {
            if (!item.id.equals(getId()) && ids.add(item.id)) { collector.commit(new SpankBangInfoItemExtractor(item)); }
        }
        return collector;
    }
    @Nonnull @Override public List<MetaInfo> getMetaInfo() { return Collections.emptyList(); }
    private void page() throws ParsingException {
        if (document == null) { throw new ParsingException("SpankBang page was not fetched"); }
    }
}

final class SpankBangInfoItemExtractor implements StreamInfoItemExtractor {
    private final SpankBangItem item;
    SpankBangInfoItemExtractor(final SpankBangItem item) { this.item = item; }
    @Override public String getName() { return item.title; }
    @Override public String getUrl() { return item.url; }
    @Override public String getThumbnailUrl() { return item.thumbnail; }
    @Override public StreamType getStreamType() { return StreamType.VIDEO_STREAM; }
    @Override public long getDuration() { return item.duration; }
    @Override public long getViewCount() { return -1; }
    @Override public String getUploaderName() { return "SpankBang"; }
    @Override public String getUploaderUrl() { return SpankBangParser.BASE_URL + "/"; }
    @Nullable @Override public String getTextualUploadDate() { return null; }
    @Nullable @Override public DateWrapper getUploadDate() { return null; }
}

final class SpankBangItem {
    final String id; final String url; final String title; final String thumbnail; final long duration;
    SpankBangItem(final String id, final String url, final String title, final String thumbnail, final long duration) {
        this.id = id; this.url = url; this.title = title; this.thumbnail = thumbnail; this.duration = duration;
    }
}

final class SpankBangSource {
    final String id; final String url; final String cleanUrl; final String resolution; final DeliveryMethod method;
    SpankBangSource(final String id, final String url, final String cleanUrl, final String resolution,
                    final DeliveryMethod method) {
        this.id = id; this.url = url; this.cleanUrl = cleanUrl; this.resolution = resolution; this.method = method;
    }
}

final class SpankBangParser {
    static final String BASE_URL = "https://www.spankbang.com";
    private static final String MARKER = "#spankbang=1&ref=";
    private static final Pattern ID = Pattern.compile("^/([a-z0-9]+)/video(?:/|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern STREAM = Pattern.compile(
            "'(?:\\d{3,4}p|4k|m3u8(?:_\\d{3,4}p|_4k)?)'\\s*:\\s*\\[\\s*'([^']+)'",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME = Pattern.compile("(\\d+)h(?:\\s*(\\d+)m)?|(\\d+)m(?:\\s*(\\d+)s)?|(\\d+)s");
    private SpankBangParser() { }

    static Document fetch(final String url) throws IOException, ExtractionException {
        final String normalized = normalize(url);
        final Response response = org.schabi.newpipe.extractor.NewPipe.getDownloader().get(normalized, headers(normalized));
        return Jsoup.parse(response.responseBody(), normalized);
    }
    static Map<String, List<String>> headers(final String referer) {
        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("User-Agent", Collections.singletonList(
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"));
        headers.put("Accept", Collections.singletonList(
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"));
        headers.put("Accept-Language", Collections.singletonList("en-US,en;q=0.9"));
        headers.put("Cache-Control", Collections.singletonList("no-cache"));
        headers.put("Pragma", Collections.singletonList("no-cache"));
        headers.put("Referer", Collections.singletonList(referer == null ? BASE_URL + "/" : referer));
        headers.put("Cookie", Collections.singletonList(
                "age_pass=1; pg_interstitial_v5=1; pg_pop_v5=1; player_quality=1080; "
                        + "preroll_skip=1; backend_version=main; videos_layout=four-col"));
        return headers;
    }
    static String normalize(final String value) {
        if (value == null || value.trim().isEmpty()) { return ""; }
        String url = value.trim().replace("&amp;", "&");
        if (url.startsWith("//")) { url = "https:" + url; }
        if (url.startsWith("/")) { return BASE_URL + url; }
        return url.replaceFirst("^https?://(?:[a-z]{2}\\.)?(?:www\\.)?spankbang\\.com", BASE_URL);
    }
    static boolean isVideoUrl(final String value) {
        if (value == null) { return false; }
        try {
            final URI uri = URI.create(normalize(value));
            return uri.getHost() != null && uri.getHost().endsWith("spankbang.com")
                    && ID.matcher(uri.getPath()).find();
        } catch (final IllegalArgumentException ignored) { return false; }
    }
    static String id(final String url) throws ParsingException {
        try {
            final Matcher matcher = ID.matcher(URI.create(normalize(url)).getPath());
            if (matcher.find()) { return matcher.group(1); }
        } catch (final IllegalArgumentException ignored) { }
        throw new ParsingException("Could not extract SpankBang id from URL: " + url);
    }
    static String encode(final String value) {
        try { return URLEncoder.encode(value == null ? "" : value.trim(), StandardCharsets.UTF_8.name()); }
        catch (final java.io.UnsupportedEncodingException e) { throw new IllegalStateException("UTF-8 unavailable", e); }
    }
    static String title(final Document document, final String fallback) {
        String value = meta(document, "meta[property=og:title], h1");
        value = value.replaceFirst("\\s*(?:エロ動画\\s*-\\s*)?SpankBang\\s*$", "").trim();
        return value.isEmpty() ? fallback : value;
    }
    static String meta(final Document document, final String selector) {
        final Element element = document.selectFirst(selector);
        return element == null ? "" : clean(element.hasAttr("content") ? element.attr("content") : element.text());
    }
    static long duration(final Document document) {
        try { return Long.parseLong(meta(document, "meta[property='og:video:duration']")); }
        catch (final NumberFormatException ignored) { return -1; }
    }
    static List<SpankBangItem> cards(final Element scope, final int maximum) {
        if (scope == null) { return Collections.emptyList(); }
        final LinkedHashMap<String, SpankBangItem> values = new LinkedHashMap<>();
        final List<Element> candidates = new ArrayList<>();
        if (scope.is("[data-testid=video-item][data-id]")) { candidates.add(scope); }
        candidates.addAll(scope.select("[data-testid=video-item][data-id]"));
        if (candidates.isEmpty()) {
            for (final Element link : scope.select("a[href*='/video/']")) {
                Element card = link;
                while (card.parent() != null && card.selectFirst("img") == null) {
                    card = card.parent();
                }
                candidates.add(card);
            }
        }
        for (final Element card : candidates) {
            final Element link = card.selectFirst("a[href*='/video']");
            if (link == null) { continue; }
            final String url = normalize(link.absUrl("href"));
            final String id;
            try { id = id(url); } catch (final ParsingException ignored) { continue; }
            final Element image = card.selectFirst("img[src], img[data-src], img[data-original]");
            final String thumbnail = image == null ? "" : first(normalize(image.absUrl("data-src")),
                    normalize(image.absUrl("data-original")), normalize(image.absUrl("src")));
            final String name = first(clean(card.selectFirst("[data-testid=video-item-title]") == null ? "" :
                    card.selectFirst("[data-testid=video-item-title]").text()),
                    image == null ? "" : clean(image.attr("alt")), clean(link.attr("title")));
            if (name.isEmpty()) { continue; }
            values.put(id, new SpankBangItem(id, url, name, thumbnail,
                    time(card.selectFirst("[data-testid=video-item-length]") == null ? "" :
                            card.selectFirst("[data-testid=video-item-length]").text())));
            if (values.size() >= maximum) { break; }
        }
        return new ArrayList<>(values.values());
    }
    static List<SpankBangItem> searchCards(final Document document, final int maximum) {
        final List<Element> lists = document.select("main.main-container div[x-data=videoList]");
        if (lists.isEmpty()) {
            return Collections.emptyList();
        }
        // The first list belongs to the surrounding layout; the search results use the second.
        return cards(lists.get(lists.size() > 1 ? 1 : 0), maximum);
    }
    static List<SpankBangItem> relatedCards(final Document document, final int maximum) {
        final LinkedHashMap<String, SpankBangItem> values = new LinkedHashMap<>();
        for (final Element link : document.select("a[data-testid=related_videos_bottom][href*='/video']")) {
            Element card = link;
            while (card != null && !card.hasAttr("data-id")) { card = card.parent(); }
            if (card == null) { continue; }
            for (final SpankBangItem item : cards(card, 1)) {
                values.put(item.id, item);
            }
            if (values.size() >= maximum) { break; }
        }
        return new ArrayList<>(values.values());
    }
    static List<SpankBangSource> sources(final Document document, final String pageUrl) {
        final LinkedHashMap<String, SpankBangSource> values = new LinkedHashMap<>();
        final Element mainContainer = document.selectFirst("main.main-container");
        final String pageSource = mainContainer == null ? document.html() : mainContainer.html();
        final Matcher matcher = STREAM.matcher(pageSource);
        while (matcher.find()) {
            final String source = matcher.group(1).replace("\\/", "/").replace("&amp;", "&");
            if (!source.startsWith("https://") || source.contains("ads")) { continue; }
            final DeliveryMethod method = source.contains(".m3u8") ? DeliveryMethod.HLS
                    : DeliveryMethod.PROGRESSIVE_HTTP;
            final String resolution = method == DeliveryMethod.HLS ? "HLS" : resolution(source);
            final String marked = source + MARKER + encode(pageUrl);
            values.put(source, new SpankBangSource(resolution, marked, source, resolution, method));
        }
        for (final Element sourceElement : document.select("video source[src], source[src*='.mp4'], source[src*='.m3u8']")) {
            final String source = sourceElement.absUrl("src").replace("&amp;", "&");
            if (!source.startsWith("https://") || source.contains("ads")) { continue; }
            final DeliveryMethod method = source.contains(".m3u8") ? DeliveryMethod.HLS
                    : DeliveryMethod.PROGRESSIVE_HTTP;
            final String resolution = method == DeliveryMethod.HLS ? "HLS" : resolution(source);
            values.put(source, new SpankBangSource(resolution, source + MARKER + encode(pageUrl),
                    source, resolution, method));
        }
        return new ArrayList<>(values.values());
    }
    private static String resolution(final String source) {
        final Matcher matcher = Pattern.compile("[-_,](\\d{3,4}p|4k)(?:[.?_-]|$)",
                Pattern.CASE_INSENSITIVE).matcher(source);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : "MP4";
    }
    private static long time(final String value) {
        final Matcher matcher = TIME.matcher(value == null ? "" : value);
        if (!matcher.find()) { return -1; }
        long result = 0;
        if (matcher.group(1) != null) { result += Long.parseLong(matcher.group(1)) * 3600L; }
        if (matcher.group(2) != null) { result += Long.parseLong(matcher.group(2)) * 60L; }
        if (matcher.group(3) != null) { result += Long.parseLong(matcher.group(3)) * 60L; }
        if (matcher.group(4) != null) { result += Long.parseLong(matcher.group(4)); }
        if (matcher.group(5) != null) { result += Long.parseLong(matcher.group(5)); }
        return result;
    }
    private static String first(final String... values) {
        for (final String value : values) { if (value != null && !value.trim().isEmpty()) { return value.trim(); } }
        return "";
    }
    private static String clean(final String value) { return Jsoup.parse(value == null ? "" : value).text().replaceAll("\\s+", " ").trim(); }
}
