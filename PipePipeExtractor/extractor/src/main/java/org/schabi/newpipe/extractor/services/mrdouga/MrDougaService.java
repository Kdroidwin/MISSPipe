package org.schabi.newpipe.extractor.services.mrdouga;

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
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Extractor for first-party MRDOUGA pages. Advertising endpoints are never requested. */
public final class MrDougaService extends StreamingService {
    public MrDougaService(final int id) {
        super(id, "MRDOUGA", Collections.singletonList(ServiceInfo.MediaCapability.VIDEO));
    }

    @Override public String getBaseUrl() { return MrDougaParser.BASE; }
    @Override public LinkHandlerFactory getStreamLHFactory() { return MrDougaStreamLinkHandlerFactory.INSTANCE; }
    @Override public ListLinkHandlerFactory getChannelLHFactory() { return null; }
    @Override public ListLinkHandlerFactory getChannelTabLHFactory() { return null; }
    @Override public ListLinkHandlerFactory getPlaylistLHFactory() { return null; }
    @Override public SearchQueryHandlerFactory getSearchQHFactory() { return MrDougaSearchQueryHandlerFactory.INSTANCE; }
    @Override public ListLinkHandlerFactory getCommentsLHFactory() { return null; }
    @Override public SearchExtractor getSearchExtractor(final SearchQueryHandler handler) { return new MrDougaSearchExtractor(this, handler); }
    @Override public SuggestionExtractor getSuggestionExtractor() { return null; }
    @Override public SubscriptionExtractor getSubscriptionExtractor() { return null; }
    @Override public KioskList getKioskList() throws ExtractionException {
        final KioskList kiosks = new KioskList(this);
        try {
            kiosks.addKioskEntry((service, url, kioskId) -> new MrDougaKioskExtractor(
                            service, MrDougaKioskLinkHandlerFactory.INSTANCE.fromId(kioskId), kioskId),
                    MrDougaKioskLinkHandlerFactory.INSTANCE, "latest");
            kiosks.addKioskEntry((service, url, kioskId) -> new MrDougaKioskExtractor(
                            service, MrDougaKioskLinkHandlerFactory.INSTANCE.fromId(kioskId), kioskId),
                    MrDougaKioskLinkHandlerFactory.INSTANCE, "popular");
            kiosks.setDefaultKiosk("latest");
            return kiosks;
        } catch (final Exception e) {
            throw new ExtractionException("Could not initialize MRDOUGA kiosks", e);
        }
    }
    @Override public ChannelExtractor getChannelExtractor(final ListLinkHandler handler) { return null; }
    @Override public ChannelTabExtractor getChannelTabExtractor(final ListLinkHandler handler) throws ExtractionException { throw new ExtractionException("MRDOUGA channel tabs unavailable"); }
    @Override public PlaylistExtractor getPlaylistExtractor(final ListLinkHandler handler) throws ExtractionException { throw new ExtractionException("MRDOUGA playlists unavailable"); }
    @Override public StreamExtractor getStreamExtractor(final LinkHandler handler) { return new MrDougaStreamExtractor(this, handler); }
    @Override public CommentsExtractor getCommentsExtractor(final ListLinkHandler handler) { return null; }
    @Override public BulletCommentsExtractor getBulletCommentsExtractor(final ListLinkHandler handler) { return null; }
}

final class MrDougaStreamLinkHandlerFactory extends LinkHandlerFactory {
    static final MrDougaStreamLinkHandlerFactory INSTANCE = new MrDougaStreamLinkHandlerFactory();
    @Override public String getId(final String url) throws ParsingException { return MrDougaParser.id(url); }
    @Override public String getUrl(final String id) { return MrDougaParser.BASE + "/video/" + id + "/"; }
    @Override public boolean onAcceptUrl(final String url) { return MrDougaParser.isVideoUrl(url); }
    @Override public LinkHandler fromUrl(final String url, final String baseUrl)
            throws ParsingException {
        if (!acceptUrl(url)) throw new ParsingException("URL not accepted: " + url);
        // MRDOUGA requires the human-readable slug after the numeric id. Do not replace a
        // supplied video URL with the otherwise valid-looking but server-side 404 id-only URL.
        return new LinkHandler(url, MrDougaParser.normalize(url), getId(url));
    }
}

final class MrDougaSearchQueryHandlerFactory extends SearchQueryHandlerFactory {
    static final MrDougaSearchQueryHandlerFactory INSTANCE = new MrDougaSearchQueryHandlerFactory();
    @Override public String getUrl(final String query, final List<FilterItem> content, final List<FilterItem> sort) { return MrDougaParser.searchUrl(query, 1); }
}

final class MrDougaSearchExtractor extends SearchExtractor {
    MrDougaSearchExtractor(final StreamingService service, final SearchQueryHandler handler) { super(service, handler); }
    @Override public void onFetchPage(@Nonnull final Downloader downloader) { }
    @Override protected InfoItemsPage<InfoItem> getInitialPageInternal() throws IOException, ExtractionException { return page(1); }
    @Override protected InfoItemsPage<InfoItem> getPageInternal(final Page page) throws IOException, ExtractionException { return page(MrDougaParser.pageNumber(page.getUrl())); }
    private InfoItemsPage<InfoItem> page(final int number) throws IOException, ExtractionException {
        final MrDougaSearchPage result = MrDougaParser.search(getSearchString(), number);
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        for (final MrDougaItem item : result.items) collector.commit(new MrDougaItemExtractor(item));
        final Page next = result.hasNext ? new Page(MrDougaParser.searchUrl(getSearchString(), number + 1)) : null;
        return new ListExtractor.InfoItemsPage<>(collector, next);
    }
}

final class MrDougaKioskLinkHandlerFactory extends ListLinkHandlerFactory {
    static final MrDougaKioskLinkHandlerFactory INSTANCE = new MrDougaKioskLinkHandlerFactory();
    @Override public String getId(final String url) { return url != null && url.contains("most-popular") ? "popular" : "latest"; }
    @Override public String getUrl(final String id, final List<FilterItem> content,
                                   final List<FilterItem> sort) {
        return "popular".equals(id) ? MrDougaParser.BASE + "/most-popular/?sort_by=video_viewed"
                : MrDougaParser.BASE + "/latest-updates/";
    }
    @Override public boolean onAcceptUrl(final String url) { return url != null && MrDougaParser.normalize(url).contains("mrdouga.com/"); }
}

final class MrDougaKioskExtractor extends KioskExtractor<StreamInfoItem> {
    private Document document;
    MrDougaKioskExtractor(final StreamingService service, final ListLinkHandler handler, final String kioskId) { super(service, handler, kioskId); }
    @Override public void onFetchPage(@Nonnull final Downloader downloader) throws IOException, ExtractionException { document = MrDougaParser.fetch(getUrl()); }
    @Nonnull @Override public String getName() { return "popular".equals(getId()) ? "Popular" : "Latest"; }
    @Nonnull @Override public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {
        if (document == null) throw new ParsingException("MRDOUGA kiosk page was not fetched");
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        for (final MrDougaItem item : MrDougaParser.cards(document, 40)) collector.commit(new MrDougaItemExtractor(item));
        return new InfoItemsPage<>(collector, null);
    }
    @Override public InfoItemsPage<StreamInfoItem> getPage(final Page page) { return InfoItemsPage.emptyPage(); }
}

final class MrDougaStreamExtractor extends StreamExtractor {
    private Document document;
    MrDougaStreamExtractor(final StreamingService service, final LinkHandler handler) { super(service, handler); }
    @Override public void onFetchPage(@Nonnull final Downloader downloader) throws IOException, ExtractionException { document = MrDougaParser.fetch(getUrl()); }
    @Nonnull @Override public String getName() throws ParsingException { page(); return MrDougaParser.title(document, getId()); }
    @Nonnull @Override public String getThumbnailUrl() throws ParsingException { page(); return MrDougaParser.thumbnail(document); }
    @Nonnull @Override public Description getDescription() throws ParsingException { page(); final String value = MrDougaParser.description(document); return value.isEmpty() ? Description.EMPTY_DESCRIPTION : new Description(value, Description.PLAIN_TEXT); }
    @Override public long getLength() throws ParsingException { page(); return MrDougaParser.duration(document); }
    @Nonnull @Override public String getUploaderName() { return "MRDOUGA"; }
    @Nonnull @Override public String getUploaderUrl() { return MrDougaParser.BASE + "/"; }
    @Nonnull @Override public List<String> getTags() throws ParsingException { page(); return MrDougaParser.tags(document); }
    @Override public String getTextualUploadDate() { return ""; }
    @Override public List<AudioStream> getAudioStreams() { return Collections.emptyList(); }
    @Override public List<VideoStream> getVideoStreams() throws IOException, ExtractionException {
        page();
        final List<MrDougaStream> values = MrDougaParser.streams(document);
        if (values.isEmpty()) throw new ParsingException("Could not find MRDOUGA video URL");
        final List<VideoStream> streams = new ArrayList<>();
        for (final MrDougaStream value : values) streams.add(new VideoStream.Builder().setId(value.id)
                .setContent(MrDougaParser.markStream(value.url, getUrl()), true).setResolution(value.resolution)
                .setMediaFormat(MediaFormat.MPEG_4).setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                .setIsVideoOnly(false).build());
        return streams;
    }
    @Override public List<VideoStream> getVideoOnlyStreams() { return Collections.emptyList(); }
    @Override public StreamType getStreamType() { return StreamType.VIDEO_STREAM; }
    @Override public InfoItemsCollector<? extends InfoItem, ? extends InfoItemExtractor> getRelatedItems() throws IOException, ExtractionException {
        page(); final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        for (final MrDougaItem item : MrDougaParser.cards(document.selectFirst("#list_videos_related_videos_items"), 40)) {
            if (!getId().equals(item.id)) collector.commit(new MrDougaItemExtractor(item));
        }
        return collector;
    }
    @Nonnull @Override public List<MetaInfo> getMetaInfo() { return Collections.emptyList(); }
    private void page() throws ParsingException { if (document == null) throw new ParsingException("MRDOUGA page was not fetched"); }
}

final class MrDougaItem {
    final String id, url, title, thumbnail; final long duration;
    MrDougaItem(final String id, final String url, final String title, final String thumbnail, final long duration) { this.id = id; this.url = url; this.title = title; this.thumbnail = thumbnail; this.duration = duration; }
}

final class MrDougaItemExtractor implements StreamInfoItemExtractor {
    private final MrDougaItem item;
    MrDougaItemExtractor(final MrDougaItem item) { this.item = item; }
    @Override public String getName() { return item.title; }
    @Override public String getUrl() { return item.url; }
    @Override public String getThumbnailUrl() { return item.thumbnail; }
    @Override public StreamType getStreamType() { return StreamType.VIDEO_STREAM; }
    @Override public long getDuration() { return item.duration; }
    @Override public long getViewCount() { return -1; }
    @Override public String getUploaderName() { return "MRDOUGA"; }
    @Override public String getUploaderUrl() { return MrDougaParser.BASE + "/"; }
    @Nullable @Override public String getTextualUploadDate() { return null; }
    @Nullable @Override public DateWrapper getUploadDate() { return null; }
}

final class MrDougaSearchPage { final List<MrDougaItem> items; final boolean hasNext; MrDougaSearchPage(final List<MrDougaItem> items, final boolean hasNext) { this.items = items; this.hasNext = hasNext; } }
final class MrDougaStream { final String id, url, resolution; MrDougaStream(final String id, final String url, final String resolution) { this.id = id; this.url = url; this.resolution = resolution; } }

final class MrDougaParser {
    static final String BASE = "https://mrdouga.com";
    private static final Pattern ID = Pattern.compile("/video/(\\d+)(?:/|$)");
    private static final Pattern VIDEO_URL = Pattern.compile(
            "^https?://(?:www\\.)?mrdouga\\.com/video/\\d+(?:/|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PAGE = Pattern.compile("[?&]page=(\\d+)");
    private static final Pattern PLAYER_VALUE = Pattern.compile("(?:video_url(?:_hd)?\\s*:\\s*|flashvars\\[['\\\"]video_url(?:_hd)?['\\\"]]\\s*=\\s*)['\\\"]([^'\\\"]+)['\\\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_PROPERTY = Pattern.compile(
            "(?:video_url(?:_hd)?\\s*:\\s*)['\\\"]([^'\\\"]+)['\\\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DIRECT_GET_FILE = Pattern.compile(
            "https?://[^'\\\"\\s<>]+/get_file/[^'\\\"\\s<>]+",
            Pattern.CASE_INSENSITIVE);
    private MrDougaParser() { }
    static Document fetch(final String url) throws IOException, ExtractionException {
        final String requestUrl = requestUrl(normalize(url));
        final Response response = NewPipe.getDownloader().get(requestUrl, headers(BASE + "/"));
        if (response.responseCode() == 200) {
            return Jsoup.parse(response.responseBody(), response.latestUrl());
        }
        if (response.responseCode() == 404) {
            final Document recovered = recoverVideoPage(url);
            if (recovered != null) return recovered;
        }
        throw new ParsingException("MRDOUGA page request returned HTTP "
                + response.responseCode());
    }
    @Nullable
    private static Document recoverVideoPage(final String url) throws IOException, ExtractionException {
        final String videoId;
        try {
            videoId = id(url);
        } catch (final ParsingException ignored) {
            return null;
        }
        final Response embedResponse = NewPipe.getDownloader().get(
                BASE + "/embed/" + videoId + "/", headers(BASE + "/"));
        if (embedResponse.responseCode() != 200) return null;
        final Document embedDocument = Jsoup.parse(embedResponse.responseBody(),
                embedResponse.latestUrl());
        final Element canonical = embedDocument.selectFirst("link[rel=canonical][href]");
        if (canonical == null) return embedDocument;
        final String canonicalUrl = canonical.absUrl("href");
        if (canonicalUrl.isEmpty()) return embedDocument;
        final Response pageResponse = NewPipe.getDownloader().get(requestUrl(canonicalUrl),
                headers(BASE + "/"));
        return pageResponse.responseCode() == 200
                ? Jsoup.parse(pageResponse.responseBody(), pageResponse.latestUrl())
                : embedDocument;
    }
    static MrDougaSearchPage search(final String query, final int page) throws IOException, ExtractionException {
        final Document document = fetch(searchUrl(query, page)); final List<MrDougaItem> items = cards(document, 40);
        final boolean next = document.selectFirst("a[rel=next], .pagination .next a, .pagination a.next") != null;
        return new MrDougaSearchPage(items, !items.isEmpty() && next);
    }
    static String searchUrl(final String query, final int page) { final String base = BASE + "/search/" + encode(query) + "/"; return page <= 1 ? base : base + "?page=" + page; }
    static int pageNumber(final String url) { final Matcher matcher = PAGE.matcher(url); return matcher.find() ? Integer.parseInt(matcher.group(1)) : 1; }
    static List<MrDougaItem> cards(final Element scope, final int maximum) {
        if (scope == null) return Collections.emptyList(); final LinkedHashMap<String, MrDougaItem> result = new LinkedHashMap<>();
        for (final Element link : scope.select(".thumb a[href*=/video/], a[href*=/video/]") ) {
            final String url = normalize(link.absUrl("href")); final String id = idOrEmpty(url); if (id.isEmpty() || result.containsKey(id)) continue;
            final Element card = card(link); final Element image = card.selectFirst("img[data-original], img[data-webp], img[data-src], img[src]");
            final String title = first(link.attr("title"), text(card.selectFirst(".title")), image == null ? "" : image.attr("alt"));
            if (title.isEmpty()) continue;
            final String thumbnail = image == null ? "" : first(image.absUrl("data-original"), image.absUrl("data-webp"), image.absUrl("data-src"), image.absUrl("src"));
            result.put(id, new MrDougaItem(id, url, title, normalize(thumbnail), duration(text(card.selectFirst(".time, .duration")))));
            if (result.size() >= maximum) break;
        }
        return new ArrayList<>(result.values());
    }
    static List<MrDougaStream> streams(final Document document) {
        final LinkedHashMap<String, MrDougaStream> result = new LinkedHashMap<>();
        for (final Element script : document.select("script")) {
            collectStreamCandidates(result, script.data());
        }
        // Keep a document fallback for pages whose player data is outside a script tag.
        collectStreamCandidates(result, document.outerHtml());
        return new ArrayList<>(result.values());
    }
    static String title(final Document document, final String fallback) { final Matcher m = Pattern.compile("video_title\\s*:\\s*'([^']+)'", Pattern.CASE_INSENSITIVE).matcher(document.html()); return m.find() ? clean(m.group(1)) : first(meta(document, "meta[property=og:title]"), clean(document.title()), fallback); }
    static String thumbnail(final Document document) { final Matcher m = Pattern.compile("preview_url\\s*:\\s*'([^']+)'", Pattern.CASE_INSENSITIVE).matcher(document.html()); return m.find() ? normalize(m.group(1)) : meta(document, "meta[property=og:image]"); }
    static String description(final Document document) { return first(meta(document, "meta[property=og:description]"), meta(document, "meta[name=description]")); }
    static long duration(final Document document) { return duration(meta(document, "meta[property=video:duration]")); }
    static List<String> tags(final Document document) { final List<String> values = new ArrayList<>(); for (final Element tag : document.select("meta[property=video:tag], a[href*=/categories/]")) { final String value = clean(tag.hasAttr("content") ? tag.attr("content") : tag.text()); if (!value.isEmpty() && !values.contains(value)) values.add(value); } return values; }
    static String markStream(final String stream, final String page) { return stream + "#mrdouga=1&ref=" + encode(page); }
    static boolean isVideoUrl(final String value) {
        return value != null && VIDEO_URL.matcher(normalize(value)).find();
    }
    private static boolean isPlayableStreamUrl(final String value) {
        final String normalized = normalize(value);
        return (normalized.startsWith("https://") || normalized.startsWith("http://"))
                && normalized.contains("/get_file/");
    }
    static String id(final String url) throws ParsingException { final Matcher matcher = ID.matcher(normalize(url)); if (matcher.find()) return matcher.group(1); throw new ParsingException("Could not extract MRDOUGA id: " + url); }
    static String normalize(final String value) { if (value == null) return ""; final String result = value.trim().replace("\\/", "/").replace("&amp;", "&"); return result.startsWith("//") ? "https:" + result : result.startsWith("/") ? BASE + result : result; }
    private static String requestUrl(final String value) throws ParsingException {
        try {
            return URI.create(value).toASCIIString();
        } catch (final IllegalArgumentException exception) {
            throw new ParsingException("Invalid MRDOUGA page URL", exception);
        }
    }
    private static Map<String, List<String>> headers(final String referer) { final Map<String, List<String>> headers = new HashMap<>(); headers.put("User-Agent", Collections.singletonList("Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")); headers.put("Accept-Language", Collections.singletonList("ja-JP,ja;q=0.9,en-US;q=0.8")); headers.put("Referer", Collections.singletonList(referer == null || referer.isEmpty() ? BASE + "/" : referer)); return headers; }
    private static void collectPlayerStreams(final LinkedHashMap<String, MrDougaStream> result,
                                             final Matcher matcher) {
        while (matcher.find()) {
            final String decoded = decode(matcher.group(1));
            if (!isPlayableStreamUrl(decoded)) continue;
            final String label = matcher.group().toLowerCase(java.util.Locale.ROOT)
                    .contains("_hd") ? "HD MP4" : "MP4";
            addStream(result, decoded, label);
        }
    }
    private static void collectStreamCandidates(final LinkedHashMap<String, MrDougaStream> result,
                                                final String source) {
        if (source == null || source.isEmpty()) return;
        collectPlayerStreams(result, PLAYER_VALUE.matcher(source));
        collectPlayerStreams(result, PLAYER_PROPERTY.matcher(source));
        final Matcher directMatcher = DIRECT_GET_FILE.matcher(source.replace("\\/", "/"));
        while (directMatcher.find()) addStream(result, normalize(directMatcher.group()), "MP4");
    }
    private static void addStream(final LinkedHashMap<String, MrDougaStream> result,
                                  final String url, final String label) {
        if (!isPlayableStreamUrl(url) || result.containsKey(url)) return;
        result.put(url, new MrDougaStream(label + "-" + (result.size() + 1), url, label));
    }
    private static String decode(final String value) {
        final String normalized = normalize(value);
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) return normalized;
        try { return normalize(new String(Base64.getDecoder().decode(value.trim()), StandardCharsets.UTF_8));
        } catch (final IllegalArgumentException ignored) {
            try { return normalize(new String(Base64.getUrlDecoder().decode(value.trim()), StandardCharsets.UTF_8));
            } catch (final IllegalArgumentException ignoredAgain) { return ""; }
        }
    }
    private static Element card(final Element link) { Element current = link; for (int i = 0; i < 5 && current.parent() != null; i++) { if (current.hasClass("thumb")) return current; current = current.parent(); } return link; }
    private static String meta(final Document document, final String selector) { final Element value = document.selectFirst(selector); return value == null ? "" : clean(value.attr("content")); }
    private static long duration(final String value) { if (value == null || value.trim().isEmpty()) return -1; try { long result = 0; for (final String part : value.trim().split(":")) result = result * 60 + Long.parseLong(part.trim()); return result; } catch (final NumberFormatException ignored) { return -1; } }
    private static String idOrEmpty(final String url) { try { return id(url); } catch (final ParsingException ignored) { return ""; } }
    private static String encode(final String value) { try { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name()); } catch (final Exception e) { throw new IllegalStateException("UTF-8 unavailable", e); } }
    private static String first(final String... values) { for (final String value : values) if (value != null && !value.trim().isEmpty()) return clean(value); return ""; }
    private static String text(final Element value) { return value == null ? "" : value.text(); }
    private static String clean(final String value) { return Jsoup.parse(value == null ? "" : value).text().replaceAll("\\s+", " ").trim(); }
}
