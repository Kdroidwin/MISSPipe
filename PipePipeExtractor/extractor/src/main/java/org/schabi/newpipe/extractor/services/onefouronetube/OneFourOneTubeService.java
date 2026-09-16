package org.schabi.newpipe.extractor.services.onefouronetube;

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
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
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

/** First-party 141tube extractor for videos, search, related cards and latest uploads. */
public final class OneFourOneTubeService extends StreamingService {
    public OneFourOneTubeService(final int id) {
        super(id, "141tube", Collections.singletonList(ServiceInfo.MediaCapability.VIDEO));
    }
    @Override public String getBaseUrl() { return OneFourOneTubeParser.BASE; }
    @Override public LinkHandlerFactory getStreamLHFactory() { return OneFourOneTubeStreamFactory.INSTANCE; }
    @Override public ListLinkHandlerFactory getChannelLHFactory() { return null; }
    @Override public ListLinkHandlerFactory getChannelTabLHFactory() { return null; }
    @Override public ListLinkHandlerFactory getPlaylistLHFactory() { return null; }
    @Override public SearchQueryHandlerFactory getSearchQHFactory() { return OneFourOneTubeSearchFactory.INSTANCE; }
    @Override public ListLinkHandlerFactory getCommentsLHFactory() { return null; }
    @Override public SearchExtractor getSearchExtractor(final SearchQueryHandler handler) { return new OneFourOneTubeSearchExtractor(this, handler); }
    @Override public SuggestionExtractor getSuggestionExtractor() { return null; }
    @Override public SubscriptionExtractor getSubscriptionExtractor() { return null; }
    @Override public KioskList getKioskList() throws ExtractionException {
        final KioskList kiosks = new KioskList(this);
        try {
            kiosks.addKioskEntry((service, url, kioskId) -> new OneFourOneTubeKioskExtractor(service,
                            OneFourOneTubeKioskFactory.INSTANCE.fromId(kioskId), kioskId),
                    OneFourOneTubeKioskFactory.INSTANCE, "latest");
            kiosks.setDefaultKiosk("latest");
            return kiosks;
        } catch (final Exception e) { throw new ExtractionException("Could not initialize 141tube kiosk", e); }
    }
    @Override public ChannelExtractor getChannelExtractor(final ListLinkHandler handler) { return null; }
    @Override public ChannelTabExtractor getChannelTabExtractor(final ListLinkHandler handler) throws ExtractionException { throw new ExtractionException("141tube channel tabs unavailable"); }
    @Override public PlaylistExtractor getPlaylistExtractor(final ListLinkHandler handler) throws ExtractionException { throw new ExtractionException("141tube playlists unavailable"); }
    @Override public StreamExtractor getStreamExtractor(final LinkHandler handler) { return new OneFourOneTubeStreamExtractor(this, handler); }
    @Override public CommentsExtractor getCommentsExtractor(final ListLinkHandler handler) { return null; }
    @Override public BulletCommentsExtractor getBulletCommentsExtractor(final ListLinkHandler handler) { return null; }
}

final class OneFourOneTubeStreamFactory extends LinkHandlerFactory {
    static final OneFourOneTubeStreamFactory INSTANCE = new OneFourOneTubeStreamFactory();
    @Override public String getId(final String url) throws ParsingException { return OneFourOneTubeParser.id(url); }
    @Override public String getUrl(final String id) { return OneFourOneTubeParser.BASE + "/video/" + id; }
    @Override public boolean onAcceptUrl(final String url) { return OneFourOneTubeParser.isVideo(url); }
}

final class OneFourOneTubeSearchFactory extends SearchQueryHandlerFactory {
    static final OneFourOneTubeSearchFactory INSTANCE = new OneFourOneTubeSearchFactory();
    @Override public String getUrl(final String query, final List<FilterItem> content, final List<FilterItem> sort) { return OneFourOneTubeParser.searchUrl(query, 1); }
}

final class OneFourOneTubeSearchExtractor extends SearchExtractor {
    OneFourOneTubeSearchExtractor(final StreamingService service, final SearchQueryHandler handler) { super(service, handler); }
    @Override public void onFetchPage(@Nonnull final Downloader downloader) { }
    @Override protected InfoItemsPage<InfoItem> getInitialPageInternal() throws IOException, ExtractionException { return page(1); }
    @Override protected InfoItemsPage<InfoItem> getPageInternal(final Page page) throws IOException, ExtractionException { return page(OneFourOneTubeParser.page(page)); }
    private InfoItemsPage<InfoItem> page(final int number) throws IOException, ExtractionException {
        final Document document = OneFourOneTubeParser.fetch(OneFourOneTubeParser.searchUrl(getSearchString(), number));
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        for (final OneFourOneTubeItem item : OneFourOneTubeParser.cards(document, 48)) collector.commit(new OneFourOneTubeItemExtractor(item));
        return new ListExtractor.InfoItemsPage<>(collector, OneFourOneTubeParser.hasNext(document, number) ? new Page(String.valueOf(number + 1)) : null);
    }
}

final class OneFourOneTubeKioskFactory extends ListLinkHandlerFactory {
    static final OneFourOneTubeKioskFactory INSTANCE = new OneFourOneTubeKioskFactory();
    @Override public String getId(final String url) { return "latest"; }
    @Override public String getUrl(final String id, final List<FilterItem> content, final List<FilterItem> sort) { return OneFourOneTubeParser.BASE + "/"; }
    @Override public boolean onAcceptUrl(final String url) { return url != null && OneFourOneTubeParser.normalize(url).startsWith(OneFourOneTubeParser.BASE); }
}

final class OneFourOneTubeKioskExtractor extends KioskExtractor<org.schabi.newpipe.extractor.stream.StreamInfoItem> {
    private Document document;
    OneFourOneTubeKioskExtractor(final StreamingService service, final ListLinkHandler handler, final String kioskId) { super(service, handler, kioskId); }
    @Override public void onFetchPage(@Nonnull final Downloader downloader) throws IOException, ExtractionException { document = OneFourOneTubeParser.fetch(getUrl()); }
    @Nonnull @Override public String getName() { return "Latest"; }
    @Nonnull @Override public InfoItemsPage<org.schabi.newpipe.extractor.stream.StreamInfoItem> getInitialPage() throws ExtractionException {
        if (document == null) throw new ParsingException("141tube kiosk page was not fetched");
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        for (final OneFourOneTubeItem item : OneFourOneTubeParser.cards(document, 48)) collector.commit(new OneFourOneTubeItemExtractor(item));
        return new InfoItemsPage<>(collector, OneFourOneTubeParser.hasNext(document, 1) ? new Page("2") : null);
    }
    @Override public InfoItemsPage<org.schabi.newpipe.extractor.stream.StreamInfoItem> getPage(final Page page) throws IOException, ExtractionException {
        final Document next = OneFourOneTubeParser.fetch(OneFourOneTubeParser.kioskUrl(OneFourOneTubeParser.page(page)));
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        for (final OneFourOneTubeItem item : OneFourOneTubeParser.cards(next, 48)) collector.commit(new OneFourOneTubeItemExtractor(item));
        final int nextNumber = OneFourOneTubeParser.page(page) + 1;
        return new InfoItemsPage<>(collector, OneFourOneTubeParser.hasNext(next, nextNumber - 1) ? new Page(String.valueOf(nextNumber)) : null);
    }
}

final class OneFourOneTubeStreamExtractor extends StreamExtractor {
    private Document document;
    OneFourOneTubeStreamExtractor(final StreamingService service, final LinkHandler handler) { super(service, handler); }
    @Override public void onFetchPage(@Nonnull final Downloader downloader) throws IOException, ExtractionException { document = OneFourOneTubeParser.fetch(getUrl()); }
    @Nonnull @Override public String getName() throws ParsingException { page(); return OneFourOneTubeParser.title(document, getId()); }
    @Nonnull @Override public String getThumbnailUrl() throws ParsingException { page(); return OneFourOneTubeParser.meta(document, "meta[property=og:image]"); }
    @Nonnull @Override public Description getDescription() throws ParsingException { page(); final String value = OneFourOneTubeParser.meta(document, "meta[property=og:description], meta[name=description]"); return value.isEmpty() ? Description.EMPTY_DESCRIPTION : new Description(value, Description.PLAIN_TEXT); }
    @Override public long getLength() throws ParsingException { page(); return OneFourOneTubeParser.duration(document); }
    @Nonnull @Override public String getUploaderName() { return "141tube"; }
    @Nonnull @Override public String getUploaderUrl() { return OneFourOneTubeParser.BASE + "/"; }
    @Nonnull @Override public List<String> getTags() throws ParsingException { page(); final List<String> tags = new ArrayList<>(); for (final Element tag : document.select("meta[property=video\\:tag]")) { final String value = tag.attr("content").trim(); if (!value.isEmpty() && !tags.contains(value)) tags.add(value); } return tags; }
    @Override public String getTextualUploadDate() { return ""; }
    @Override public List<AudioStream> getAudioStreams() { return Collections.emptyList(); }
    @Override public List<VideoStream> getVideoStreams() throws IOException, ExtractionException {
        page(); final LinkedHashMap<String, VideoStream> streams = new LinkedHashMap<>();
        for (final Element source : document.select("video source[src]")) {
            final String url = OneFourOneTubeParser.normalize(source.absUrl("src"));
            if (url.isEmpty()) continue;
            final String resolution = OneFourOneTubeParser.first(source.attr("label"), source.attr("res"), "Auto");
            streams.putIfAbsent(url, new VideoStream.Builder().setId(resolution).setContent(OneFourOneTubeParser.mark(url, getUrl()), true)
                    .setResolution(resolution).setMediaFormat(MediaFormat.MPEG_4)
                    .setDeliveryMethod(url.contains(".m3u8") ? DeliveryMethod.HLS : DeliveryMethod.PROGRESSIVE_HTTP).setIsVideoOnly(false).build());
        }
        if (streams.isEmpty()) throw new ParsingException("Could not find 141tube video URL");
        return new ArrayList<>(streams.values());
    }
    @Override public List<VideoStream> getVideoOnlyStreams() { return Collections.emptyList(); }
    @Override public StreamType getStreamType() { return StreamType.VIDEO_STREAM; }
    @Override public InfoItemsCollector<? extends InfoItem, ? extends InfoItemExtractor> getRelatedItems() throws IOException, ExtractionException {
        page(); final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        Element related = document.selectFirst("#related_videos, .related-videos, .related");
        if (related == null) related = document;
        for (final OneFourOneTubeItem item : OneFourOneTubeParser.cards(related, 48)) if (!getId().equals(item.id)) collector.commit(new OneFourOneTubeItemExtractor(item));
        return collector;
    }
    @Nonnull @Override public List<MetaInfo> getMetaInfo() { return Collections.emptyList(); }
    private void page() throws ParsingException { if (document == null) throw new ParsingException("141tube page was not fetched"); }
}

final class OneFourOneTubeItem { final String id, url, title, thumbnail, duration, views; OneFourOneTubeItem(final String id, final String url, final String title, final String thumbnail, final String duration, final String views) { this.id = id; this.url = url; this.title = title; this.thumbnail = thumbnail; this.duration = duration; this.views = views; } }
final class OneFourOneTubeItemExtractor implements StreamInfoItemExtractor {
    private final OneFourOneTubeItem item; OneFourOneTubeItemExtractor(final OneFourOneTubeItem item) { this.item = item; }
    @Override public String getName() { return item.title; } @Override public String getUrl() { return item.url; } @Override public String getThumbnailUrl() { return item.thumbnail; } @Override public StreamType getStreamType() { return StreamType.VIDEO_STREAM; } @Override public long getDuration() { return OneFourOneTubeParser.duration(item.duration); } @Override public long getViewCount() { return OneFourOneTubeParser.number(item.views); } @Override public String getUploaderName() { return "141tube"; } @Override public String getUploaderUrl() { return OneFourOneTubeParser.BASE + "/"; } @Nullable @Override public String getTextualUploadDate() { return null; } @Nullable @Override public DateWrapper getUploadDate() { return null; }
}

final class OneFourOneTubeParser {
    static final String BASE = "https://141tube.com";
    private static final Pattern ID = Pattern.compile("/video/(\\d+)(?:/|$)");
    private static final Pattern DURATION = Pattern.compile("video_duration\\s*=\\s*[\"']([0-9.]+)");
    private static final Pattern PAGE = Pattern.compile("(?:[?&]page=)(\\d+)");
    private OneFourOneTubeParser() { }
    static Document fetch(final String url) throws IOException, ExtractionException { final Response response = NewPipe.getDownloader().get(normalize(url), headers()); if (response.responseCode() != 200) throw new ParsingException("141tube page request returned HTTP " + response.responseCode()); return Jsoup.parse(response.responseBody(), response.latestUrl()); }
    static List<OneFourOneTubeItem> cards(final Element scope, final int maximum) {
        if (scope == null) return Collections.emptyList(); final LinkedHashMap<String, OneFourOneTubeItem> items = new LinkedHashMap<>();
        for (final Element link : scope.select("a[href]")) {
            if (!link.attr("href").contains("/video/")) continue;
            final String url = normalize(link.attr("href")); final String id = idOrEmpty(url);
            if (id.isEmpty() || items.containsKey(id)) continue;
            final Element card = link.closest(".well"); final Element image = link.selectFirst("img[src]");
            final String title = first(link.selectFirst(".video-title") == null ? "" : link.selectFirst(".video-title").text(),
                    card == null || card.selectFirst(".video-title") == null ? "" : card.selectFirst(".video-title").text(),
                    link.attr("title"), image == null ? "" : image.attr("alt"));
            if (title.isEmpty()) continue;
            final String thumbnail = image == null ? "" : normalize(image.absUrl("src"));
            final Element data = card == null ? link.parent() : card; final Element duration = data.selectFirst(".duration"); final Element views = data.selectFirst(".video-views");
            items.put(id, new OneFourOneTubeItem(id, url, title, thumbnail, duration == null ? "" : duration.text(), views == null ? "" : views.text()));
            if (items.size() >= maximum) break;
        }
        return new ArrayList<>(items.values());
    }
    static String title(final Document document, final String fallback) { return first(meta(document, "meta[property=og:title]"), document.title().replaceFirst("\\s*-\\s*141tube.*$", ""), fallback); }
    static String meta(final Document document, final String selector) { final Element element = document.selectFirst(selector); return element == null ? "" : element.attr("content").trim(); }
    static long duration(final Document document) { final Matcher matcher = DURATION.matcher(document.html()); return matcher.find() ? Math.round(Double.parseDouble(matcher.group(1))) : -1; }
    static long duration(final String value) { final Matcher matcher = Pattern.compile("(\\d+):(\\d+)(?::(\\d+))?").matcher(value); if (!matcher.find()) return -1; final long hours = matcher.group(3) == null ? 0 : Long.parseLong(matcher.group(1)); final long minutes = matcher.group(3) == null ? Long.parseLong(matcher.group(1)) : Long.parseLong(matcher.group(2)); final long seconds = Long.parseLong(matcher.group(3) == null ? matcher.group(2) : matcher.group(3)); return hours * 3600 + minutes * 60 + seconds; }
    static long number(final String value) { final Matcher matcher = Pattern.compile("[\\d,]+").matcher(value); return matcher.find() ? Long.parseLong(matcher.group().replace(",", "")) : -1; }
    static String searchUrl(final String query, final int page) { return BASE + "/search/videos?search_query=" + encode(query) + (page > 1 ? "&page=" + page : ""); }
    static String kioskUrl(final int page) { return BASE + "/" + (page > 1 ? "?page=" + page : ""); }
    static int page(final Page page) { try { return Math.max(1, Integer.parseInt(page.getId())); } catch (final Exception ignored) { return 1; } }
    static boolean hasNext(final Document document, final int currentPage) {
        for (final Element link : document.select("ul.pagination a[href], .pagination a[href]")) {
            final Matcher matcher = PAGE.matcher(link.attr("href"));
            if (matcher.find()) {
                try {
                    if (Integer.parseInt(matcher.group(1)) > currentPage) return true;
                } catch (final NumberFormatException ignored) {
                    // Ignore a malformed pagination target and inspect other links.
                }
            }
            final String text = link.text().trim();
            if (text.equalsIgnoreCase("next") || text.equals(">")) return true;
        }
        return false;
    }
    static String mark(final String source, final String page) { return source + "#141tube=1&ref=" + encode(page); }
    static boolean isVideo(final String url) { return url != null && ID.matcher(normalize(url)).find(); }
    static String id(final String url) throws ParsingException { final Matcher matcher = ID.matcher(normalize(url)); if (matcher.find()) return matcher.group(1); throw new ParsingException("Could not extract 141tube id: " + url); }
    static String normalize(final String value) { if (value == null) return ""; final String result = value.trim().replace("&amp;", "&"); return result.startsWith("//") ? "https:" + result : result.startsWith("/") ? BASE + result : result; }
    static String first(final String... values) { for (final String value : values) if (value != null && !value.trim().isEmpty()) return Jsoup.parse(value).text().replaceAll("\\s+", " ").trim(); return ""; }
    private static Map<String, List<String>> headers() { final Map<String, List<String>> values = new HashMap<>(); values.put("User-Agent", Collections.singletonList("Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")); values.put("Referer", Collections.singletonList(BASE + "/")); values.put("Accept-Language", Collections.singletonList("ja-JP,ja;q=0.9,en-US;q=0.8")); return values; }
    private static String encode(final String value) { try { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name()); } catch (final Exception e) { throw new IllegalStateException("UTF-8 unavailable", e); } }
    private static String idOrEmpty(final String url) { try { return id(url); } catch (final ParsingException ignored) { return ""; } }
}
