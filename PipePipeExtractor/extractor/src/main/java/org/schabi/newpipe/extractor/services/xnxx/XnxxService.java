package org.schabi.newpipe.extractor.services.xnxx;

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

/** First-party XNXX extractor for search, video metadata, playback and related videos. */
public final class XnxxService extends StreamingService {
    public XnxxService(final int id) {
        super(id, "XNXX", Collections.singletonList(ServiceInfo.MediaCapability.VIDEO));
    }

    @Override public String getBaseUrl() { return XnxxParser.BASE; }
    @Override public LinkHandlerFactory getStreamLHFactory() { return XnxxLinkHandlerFactory.INSTANCE; }
    @Override public ListLinkHandlerFactory getChannelLHFactory() { return null; }
    @Override public ListLinkHandlerFactory getChannelTabLHFactory() { return null; }
    @Override public ListLinkHandlerFactory getPlaylistLHFactory() { return null; }
    @Override public SearchQueryHandlerFactory getSearchQHFactory() { return XnxxSearchFactory.INSTANCE; }
    @Override public ListLinkHandlerFactory getCommentsLHFactory() { return null; }
    @Override public SearchExtractor getSearchExtractor(final SearchQueryHandler handler) { return new XnxxSearchExtractor(this, handler); }
    @Override public SuggestionExtractor getSuggestionExtractor() { return null; }
    @Override public SubscriptionExtractor getSubscriptionExtractor() { return null; }
    @Override public KioskList getKioskList() { return new KioskList(this); }
    @Override public ChannelExtractor getChannelExtractor(final ListLinkHandler handler) { return null; }
    @Override public ChannelTabExtractor getChannelTabExtractor(final ListLinkHandler handler) throws ExtractionException { throw new ExtractionException("XNXX channel tabs unavailable"); }
    @Override public PlaylistExtractor getPlaylistExtractor(final ListLinkHandler handler) throws ExtractionException { throw new ExtractionException("XNXX playlists unavailable"); }
    @Override public StreamExtractor getStreamExtractor(final LinkHandler handler) { return new XnxxStreamExtractor(this, handler); }
    @Override public CommentsExtractor getCommentsExtractor(final ListLinkHandler handler) { return null; }
    @Override public BulletCommentsExtractor getBulletCommentsExtractor(final ListLinkHandler handler) { return null; }
}

final class XnxxLinkHandlerFactory extends LinkHandlerFactory {
    static final XnxxLinkHandlerFactory INSTANCE = new XnxxLinkHandlerFactory();
    @Override public String getId(final String url) throws ParsingException { return XnxxParser.id(url); }
    @Override public String getUrl(final String id) { return XnxxParser.BASE + "/video-" + id + "/"; }
    @Override public boolean onAcceptUrl(final String url) { return XnxxParser.isVideo(url); }
    @Override public LinkHandler fromUrl(final String url, final String baseUrl) throws ParsingException {
        if (!acceptUrl(url)) throw new ParsingException("URL not accepted: " + url);
        final String normalized = XnxxParser.normalize(url);
        return new LinkHandler(normalized, normalized, getId(normalized));
    }
}

final class XnxxSearchFactory extends SearchQueryHandlerFactory {
    static final XnxxSearchFactory INSTANCE = new XnxxSearchFactory();
    @Override public String getUrl(final String query, final List<FilterItem> content, final List<FilterItem> sort) { return XnxxParser.searchUrl(query, 0); }
}

final class XnxxSearchExtractor extends SearchExtractor {
    XnxxSearchExtractor(final StreamingService service, final SearchQueryHandler handler) { super(service, handler); }
    @Override public void onFetchPage(@Nonnull final Downloader downloader) { }
    @Override protected InfoItemsPage<InfoItem> getInitialPageInternal() throws IOException, ExtractionException {
        return page(XnxxParser.searchUrl(getSearchString(), 0), 0);
    }
    @Override protected InfoItemsPage<InfoItem> getPageInternal(final Page page) throws IOException, ExtractionException {
        final int next = Integer.parseInt(page.getId());
        return page(XnxxParser.searchUrl(getSearchString(), next), next);
    }
    private InfoItemsPage<InfoItem> page(final String url, final int number) throws IOException, ExtractionException {
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        for (final XnxxItem item : XnxxParser.cards(XnxxParser.fetch(url), 48)) collector.commit(new XnxxItemExtractor(item));
        return new ListExtractor.InfoItemsPage<>(collector,
                collector.getItems().isEmpty() ? null : new Page(String.valueOf(number + 1)));
    }
}

final class XnxxStreamExtractor extends StreamExtractor {
    private Document document;
    XnxxStreamExtractor(final StreamingService service, final LinkHandler handler) { super(service, handler); }
    @Override public void onFetchPage(@Nonnull final Downloader downloader) throws IOException, ExtractionException { document = XnxxParser.fetch(getUrl()); }
    @Nonnull @Override public String getName() throws ParsingException { page(); return XnxxParser.title(document, getId()); }
    @Nonnull @Override public String getThumbnailUrl() throws ParsingException { page(); return XnxxParser.thumbnail(document); }
    @Nonnull @Override public Description getDescription() throws ParsingException { page(); final String value = XnxxParser.meta(document, "meta[property=og:description],meta[name=description]"); return value.isEmpty() ? Description.EMPTY_DESCRIPTION : new Description(value, Description.PLAIN_TEXT); }
    @Override public long getLength() { return -1; }
    @Nonnull @Override public String getUploaderName() { return "XNXX"; }
    @Nonnull @Override public String getUploaderUrl() { return XnxxParser.BASE + "/"; }
    @Nonnull @Override public List<String> getTags() throws ParsingException { page(); return XnxxParser.tags(document); }
    @Override public String getTextualUploadDate() { return ""; }
    @Override public List<AudioStream> getAudioStreams() { return Collections.emptyList(); }
    @Override public List<VideoStream> getVideoStreams() throws IOException, ExtractionException {
        page();
        final List<VideoStream> streams = new ArrayList<>();
        XnxxParser.addStream(streams, "HD", XnxxParser.scriptValue(document, "setVideoUrlHigh"), DeliveryMethod.PROGRESSIVE_HTTP, MediaFormat.MPEG_4);
        XnxxParser.addStream(streams, "SD", XnxxParser.scriptValue(document, "setVideoUrlLow"), DeliveryMethod.PROGRESSIVE_HTTP, MediaFormat.MPEG_4);
        XnxxParser.addStream(streams, "HLS", XnxxParser.scriptValue(document, "setVideoHLS"), DeliveryMethod.HLS, MediaFormat.MPEG_4);
        if (streams.isEmpty()) throw new ParsingException("Could not find XNXX video URL");
        return streams;
    }
    @Override public List<VideoStream> getVideoOnlyStreams() { return Collections.emptyList(); }
    @Override public StreamType getStreamType() { return StreamType.VIDEO_STREAM; }
    @Override public InfoItemsCollector<? extends InfoItem, ? extends InfoItemExtractor> getRelatedItems() throws IOException, ExtractionException {
        page(); final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        for (final XnxxItem item : XnxxParser.related(document, 48)) if (!item.id.equals(getId())) collector.commit(new XnxxItemExtractor(item));
        return collector;
    }
    @Nonnull @Override public List<MetaInfo> getMetaInfo() { return Collections.emptyList(); }
    private void page() throws ParsingException { if (document == null) throw new ParsingException("XNXX page was not fetched"); }
}

final class XnxxItem {
    final String id, url, title, thumbnail, duration;
    XnxxItem(final String id, final String url, final String title, final String thumbnail, final String duration) { this.id = id; this.url = url; this.title = title; this.thumbnail = thumbnail; this.duration = duration; }
}

final class XnxxItemExtractor implements StreamInfoItemExtractor {
    private final XnxxItem item;
    XnxxItemExtractor(final XnxxItem item) { this.item = item; }
    @Override public String getName() { return item.title; }
    @Override public String getUrl() { return item.url; }
    @Override public String getThumbnailUrl() { return item.thumbnail; }
    @Override public StreamType getStreamType() { return StreamType.VIDEO_STREAM; }
    @Override public long getDuration() { return XnxxParser.duration(item.duration); }
    @Override public long getViewCount() { return -1; }
    @Override public String getUploaderName() { return "XNXX"; }
    @Override public String getUploaderUrl() { return XnxxParser.BASE + "/"; }
    @Nullable @Override public String getTextualUploadDate() { return null; }
    @Nullable @Override public DateWrapper getUploadDate() { return null; }
}

final class XnxxParser {
    static final String BASE = "https://www.xnxx.com";
    private static final Pattern ID = Pattern.compile("/video-([a-z0-9]+)(?:/|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RELATED = Pattern.compile("var\\s+video_related\\s*=\\s*\\[(.*?)]\\s*;", Pattern.DOTALL);
    private static final Pattern OBJECT = Pattern.compile("\\{(.*?)\\}", Pattern.DOTALL);
    private XnxxParser() { }
    static Document fetch(final String url) throws IOException, ExtractionException {
        final Response response = NewPipe.getDownloader().get(normalize(url), headers());
        if (response.responseCode() != 200) throw new ParsingException("XNXX page request returned HTTP " + response.responseCode());
        return Jsoup.parse(response.responseBody(), response.latestUrl());
    }
    static List<XnxxItem> cards(final Element scope, final int maximum) {
        if (scope == null) return Collections.emptyList();
        final LinkedHashMap<String, XnxxItem> items = new LinkedHashMap<>();
        for (final Element card : scope.select("div[id^=video_]")) {
            final Element link = card.selectFirst(".thumb-under p a[href]");
            if (link == null) continue;
            // The downloader response does not always retain a base URI. XNXX cards use relative URLs,
            // so resolving the raw attribute keeps search independent from that response detail.
            final String url = normalize(link.attr("href")); final String id = idOrEmpty(url);
            if (id.isEmpty() || items.containsKey(id)) continue;
            final Element image = card.selectFirst(".thumb img");
            final String title = first(link.attr("title"), link.text(), image == null ? "" : image.attr("alt"));
            if (title.isEmpty()) continue;
            final String thumb = image == null ? "" : normalize(first(image.attr("data-src"), image.attr("src")));
            final Element metadata = card.selectFirst(".metadata");
            items.put(id, new XnxxItem(id, url, title, thumb, metadata == null ? "" : metadata.text()));
            if (items.size() >= maximum) break;
        }
        return new ArrayList<>(items.values());
    }
    static List<XnxxItem> related(final Document document, final int maximum) {
        final Matcher list = RELATED.matcher(document.html()); if (!list.find()) return cards(document, maximum);
        final LinkedHashMap<String, XnxxItem> items = new LinkedHashMap<>(); final Matcher objects = OBJECT.matcher(list.group(1));
        while (objects.find() && items.size() < maximum) {
            final String object = objects.group(1); final String id = field(object, "eid"); final String path = field(object, "u");
            final String title = field(object, "t"); if (id.isEmpty() || path.isEmpty() || title.isEmpty() || items.containsKey(id)) continue;
            items.put(id, new XnxxItem(id, normalize(path), title, field(object, "i"), field(object, "d")));
        }
        return new ArrayList<>(items.values());
    }
    static String title(final Document document, final String fallback) { return first(scriptValue(document, "setVideoTitle"), meta(document, "meta[property=og:title]"), document.title(), fallback); }
    static String thumbnail(final Document document) { return first(scriptValue(document, "setThumbUrl"), meta(document, "meta[property=og:image]")); }
    static List<String> tags(final Document document) {
        final String values = scriptValue(document, "wpn_categories"); final List<String> tags = new ArrayList<>();
        for (final String tag : values.split(",")) { final String normalized = tag.trim(); if (!normalized.isEmpty()) tags.add(normalized); }
        return tags;
    }
    static String scriptValue(final Document document, final String name) {
        final Pattern pattern = Pattern.compile(Pattern.quote(name) + "\\s*\\(\\s*['\\\"]((?:\\\\.|[^'\\\"])*)['\\\"]", Pattern.DOTALL);
        final Matcher matcher = pattern.matcher(document.html());
        if (matcher.find()) return normalize(unescape(matcher.group(1)));
        final Pattern assignment = Pattern.compile("(?:var\\s+)?" + Pattern.quote(name) + "\\s*=\\s*['\\\"]((?:\\\\.|[^'\\\"])*)['\\\"]", Pattern.DOTALL);
        final Matcher assigned = assignment.matcher(document.html()); return assigned.find() ? unescape(assigned.group(1)) : "";
    }
    static void addStream(final List<VideoStream> streams, final String id, final String url, final DeliveryMethod delivery, final MediaFormat format) {
        if (url.isEmpty()) return;
        for (final VideoStream stream : streams) if (stream.getContent().equals(url)) return;
        streams.add(new VideoStream.Builder().setId(id).setContent(url, true).setResolution(id).setMediaFormat(format).setDeliveryMethod(delivery).setIsVideoOnly(false).build());
    }
    static String searchUrl(final String query, final int page) {
        final String path = BASE + "/search/" + encode(query);
        // XNXX serves the initial result set through the explicit top variant.
        // Subsequent pages are loaded from the numbered endpoint.
        return page == 0 ? path + "?top" : path + "/" + page;
    }
    static boolean isVideo(final String url) { return url != null && normalize(url).matches("https?://(?:www\\.)?xnxx\\.com/video-[a-z0-9]+(?:/.*)?"); }
    static String id(final String url) throws ParsingException { final Matcher matcher = ID.matcher(normalize(url)); if (matcher.find()) return matcher.group(1); throw new ParsingException("Could not extract XNXX id: " + url); }
    static String normalize(final String value) { if (value == null) return ""; final String result = value.trim().replace("\\/", "/").replace("&amp;", "&"); return result.startsWith("//") ? "https:" + result : result.startsWith("/") ? BASE + result : result; }
    static String meta(final Document document, final String selector) { final Element element = document.selectFirst(selector); return element == null ? "" : first(element.attr("content")); }
    static long duration(final String value) { final Matcher matcher = Pattern.compile("(\\d+)\\s*min", Pattern.CASE_INSENSITIVE).matcher(value); return matcher.find() ? Long.parseLong(matcher.group(1)) * 60L : -1; }
    private static String field(final String object, final String name) { final Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(object); return matcher.find() ? unescape(matcher.group(1)) : ""; }
    private static String unescape(final String value) {
        final StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            final char current = value.charAt(index);
            if (current != '\\' || index + 1 >= value.length()) {
                result.append(current);
                continue;
            }
            final char escaped = value.charAt(++index);
            if (escaped == 'u' && index + 4 < value.length()) {
                final String hexadecimal = value.substring(index + 1, index + 5);
                try {
                    result.append((char) Integer.parseInt(hexadecimal, 16));
                    index += 4;
                    continue;
                } catch (final NumberFormatException ignored) {
                    result.append('\\').append(escaped);
                    continue;
                }
            }
            switch (escaped) {
                case 'n': result.append('\n'); break;
                case 'r': result.append('\r'); break;
                case 't': result.append('\t'); break;
                default: result.append(escaped); break;
            }
        }
        return result.toString();
    }
    private static String encode(final String value) { try { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name()); } catch (final Exception e) { throw new IllegalStateException("UTF-8 unavailable", e); } }
    private static String idOrEmpty(final String url) { try { return id(url); } catch (final ParsingException ignored) { return ""; } }
    private static String first(final String... values) { for (final String value : values) if (value != null && !value.trim().isEmpty()) return Jsoup.parse(value).text().replaceAll("\\s+", " ").trim(); return ""; }
    private static Map<String, List<String>> headers() { final Map<String, List<String>> values = new HashMap<>(); values.put("User-Agent", Collections.singletonList("Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")); values.put("Referer", Collections.singletonList(BASE + "/")); values.put("Accept-Language", Collections.singletonList("ja-JP,ja;q=0.9,en-US;q=0.8")); return values; }
}
