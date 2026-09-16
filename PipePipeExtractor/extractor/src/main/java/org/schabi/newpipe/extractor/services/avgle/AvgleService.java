package org.schabi.newpipe.extractor.services.avgle;

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
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
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

/** Extractor for Avgle.net's public video pages and their short-lived media URLs. */
public final class AvgleService extends StreamingService {
    public AvgleService(final int id) { super(id, "Avgle.net", Collections.singletonList(ServiceInfo.MediaCapability.VIDEO)); }
    @Override public String getBaseUrl() { return AvgleParser.BASE; }
    @Override public LinkHandlerFactory getStreamLHFactory() { return AvgleStreamFactory.INSTANCE; }
    @Override public ListLinkHandlerFactory getChannelLHFactory() { return null; }
    @Override public ListLinkHandlerFactory getChannelTabLHFactory() { return null; }
    @Override public ListLinkHandlerFactory getPlaylistLHFactory() { return null; }
    @Override public SearchQueryHandlerFactory getSearchQHFactory() { return AvgleSearchFactory.INSTANCE; }
    @Override public ListLinkHandlerFactory getCommentsLHFactory() { return null; }
    @Override public SearchExtractor getSearchExtractor(final SearchQueryHandler handler) { return new AvgleSearchExtractor(this, handler); }
    @Override public SuggestionExtractor getSuggestionExtractor() { return null; }
    @Override public SubscriptionExtractor getSubscriptionExtractor() { return null; }
    @Override public ChannelExtractor getChannelExtractor(final ListLinkHandler handler) { return null; }
    @Override public ChannelTabExtractor getChannelTabExtractor(final ListLinkHandler handler) throws ExtractionException { throw new ExtractionException("Avgle.net channel tabs unavailable"); }
    @Override public PlaylistExtractor getPlaylistExtractor(final ListLinkHandler handler) throws ExtractionException { throw new ExtractionException("Avgle.net playlists unavailable"); }
    @Override public StreamExtractor getStreamExtractor(final LinkHandler handler) { return new AvgleStreamExtractor(this, handler); }
    @Override public CommentsExtractor getCommentsExtractor(final ListLinkHandler handler) { return null; }
    @Override public BulletCommentsExtractor getBulletCommentsExtractor(final ListLinkHandler handler) { return null; }
    @Override public KioskList getKioskList() throws ExtractionException {
        final KioskList kiosks = new KioskList(this);
        try { kiosks.addKioskEntry((service, url, id) -> new AvgleKioskExtractor(service, AvgleKioskFactory.INSTANCE.fromId(id), id), AvgleKioskFactory.INSTANCE, "latest"); kiosks.setDefaultKiosk("latest"); return kiosks; }
        catch (final Exception e) { throw new ExtractionException("Could not initialize Avgle.net kiosk", e); }
    }
}

final class AvgleStreamFactory extends LinkHandlerFactory {
    static final AvgleStreamFactory INSTANCE = new AvgleStreamFactory();
    @Override public String getId(final String url) throws ParsingException { return AvgleParser.id(url); }
    @Override public String getUrl(final String id) { return AvgleParser.BASE + "/video.php?" + id; }
    @Override public boolean onAcceptUrl(final String url) { return AvgleParser.isVideo(url); }
}
final class AvgleSearchFactory extends SearchQueryHandlerFactory {
    static final AvgleSearchFactory INSTANCE = new AvgleSearchFactory();
    @Override public String getUrl(final String query, final List<FilterItem> content, final List<FilterItem> sort) { return AvgleParser.searchUrl(query, 1); }
}
final class AvgleKioskFactory extends ListLinkHandlerFactory {
    static final AvgleKioskFactory INSTANCE = new AvgleKioskFactory();
    @Override public String getId(final String url) { return "latest"; }
    @Override public String getUrl(final String id, final List<FilterItem> content, final List<FilterItem> sort) { return AvgleParser.BASE + "/?lang=ja"; }
    @Override public boolean onAcceptUrl(final String url) { return url != null && AvgleParser.normalize(url).startsWith(AvgleParser.BASE); }
}
final class AvgleSearchExtractor extends SearchExtractor {
    AvgleSearchExtractor(final StreamingService service, final SearchQueryHandler handler) { super(service, handler); }
    @Override public void onFetchPage(@Nonnull final Downloader downloader) { }
    @Override protected InfoItemsPage<InfoItem> getInitialPageInternal() throws IOException, ExtractionException { return page(1); }
    @Override protected InfoItemsPage<InfoItem> getPageInternal(final Page page) throws IOException, ExtractionException { return page(AvgleParser.page(page)); }
    private InfoItemsPage<InfoItem> page(final int number) throws IOException, ExtractionException { final Document document = AvgleParser.fetch(AvgleParser.searchUrl(getSearchString(), number)); final MultiInfoItemsCollector c = new MultiInfoItemsCollector(getServiceId()); for (final AvgleItem item : AvgleParser.cards(document)) c.commit(new AvgleItemExtractor(item)); return new ListExtractor.InfoItemsPage<>(c, AvgleParser.hasNext(document, number) ? new Page(String.valueOf(number + 1)) : null); }
}
final class AvgleKioskExtractor extends KioskExtractor<org.schabi.newpipe.extractor.stream.StreamInfoItem> {
    private Document document;
    AvgleKioskExtractor(final StreamingService service, final ListLinkHandler handler, final String id) { super(service, handler, id); }
    @Override public void onFetchPage(@Nonnull final Downloader downloader) throws IOException, ExtractionException { document = AvgleParser.fetch(getUrl()); }
    @Nonnull @Override public String getName() { return "Latest"; }
    @Nonnull @Override public InfoItemsPage<org.schabi.newpipe.extractor.stream.StreamInfoItem> getInitialPage() throws ExtractionException { if (document == null) throw new ParsingException("Avgle.net kiosk was not fetched"); final StreamInfoItemsCollector c = new StreamInfoItemsCollector(getServiceId()); for (final AvgleItem item : AvgleParser.cards(document)) c.commit(new AvgleItemExtractor(item)); return new InfoItemsPage<>(c, null); }
    @Override public InfoItemsPage<org.schabi.newpipe.extractor.stream.StreamInfoItem> getPage(final Page page) { return new InfoItemsPage<>(new StreamInfoItemsCollector(getServiceId()), null); }
}
final class AvgleStreamExtractor extends StreamExtractor {
    private Document document;
    AvgleStreamExtractor(final StreamingService service, final LinkHandler handler) { super(service, handler); }
    @Override public void onFetchPage(@Nonnull final Downloader downloader) throws IOException, ExtractionException { document = AvgleParser.fetch(getUrl()); }
    private void page() throws ParsingException { if (document == null) throw new ParsingException("Avgle.net page was not fetched"); }
    @Nonnull @Override public String getName() throws ParsingException { page(); return AvgleParser.first(document.selectFirst("h1") == null ? "" : document.selectFirst("h1").text(), AvgleParser.meta(document, "meta[property=og:title]").replaceFirst("\\s*-\\s*avgle\\.net$", ""), getId()); }
    @Nonnull @Override public String getThumbnailUrl() throws ParsingException { page(); final Element video = document.selectFirst("video[poster]"); return video == null ? "" : AvgleParser.normalize(video.absUrl("poster")); }
    @Nonnull @Override public Description getDescription() throws ParsingException { page(); final String value = AvgleParser.first(document.selectFirst(".video-desc") == null ? "" : document.selectFirst(".video-desc").text(), AvgleParser.meta(document, "meta[name=description]")); return value.isEmpty() ? Description.EMPTY_DESCRIPTION : new Description(value, Description.PLAIN_TEXT); }
    @Override public long getLength() { return -1; }
    @Nonnull @Override public String getUploaderName() { return "Avgle.net"; }
    @Nonnull @Override public String getUploaderUrl() { return AvgleParser.BASE + "/"; }
    @Nonnull @Override public List<String> getTags() throws ParsingException { page(); final List<String> result = new ArrayList<>(); for (final Element tag : document.select(".genre-pill, .tag, a[href*='tag']")) { final String text = tag.text().trim(); if (!text.isEmpty() && !result.contains(text)) result.add(text); } return result; }
    @Override public String getTextualUploadDate() { return ""; }
    @Override public List<AudioStream> getAudioStreams() { return Collections.emptyList(); }
    @Override public List<VideoStream> getVideoStreams() throws IOException, ExtractionException { page(); final String source = AvgleParser.sourceUrl(document); if (source.isEmpty()) throw new ParsingException("Could not find Avgle.net media endpoint"); final Response response = NewPipe.getDownloader().get(source, AvgleParser.headers(getUrl(), "application/json")); try { final String media = JsonParser.object().from(response.responseBody()).getString("url"); if (media == null || media.isEmpty()) throw new ParsingException("Avgle.net media endpoint did not return a URL"); return Collections.singletonList(new VideoStream.Builder().setId("Auto").setContent(AvgleParser.mark(media, getUrl()), true).setResolution("Auto").setMediaFormat(MediaFormat.MPEG_4).setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP).setIsVideoOnly(false).build()); } catch (final Exception e) { if (e instanceof ParsingException) throw (ParsingException) e; throw new ParsingException("Could not parse Avgle.net media response", e); } }
    @Override public List<VideoStream> getVideoOnlyStreams() { return Collections.emptyList(); }
    @Override public StreamType getStreamType() { return StreamType.VIDEO_STREAM; }
    @Override public InfoItemsCollector<? extends InfoItem, ? extends InfoItemExtractor> getRelatedItems() throws ParsingException { page(); final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId()); final String title = getName(); final String thumb = getThumbnailUrl(); for (final AvgleItem item : AvgleParser.episodes(document, title, thumb, getUrl())) collector.commit(new AvgleItemExtractor(item)); return collector; }
    @Nonnull @Override public List<MetaInfo> getMetaInfo() { return Collections.emptyList(); }
}
final class AvgleItem { final String url, title, thumbnail; AvgleItem(final String url, final String title, final String thumbnail) { this.url = url; this.title = title; this.thumbnail = thumbnail; } }
final class AvgleItemExtractor implements StreamInfoItemExtractor {
    private final AvgleItem item; AvgleItemExtractor(final AvgleItem item) { this.item = item; }
    @Override public String getName() { return item.title; } @Override public String getUrl() { return item.url; } @Override public String getThumbnailUrl() { return item.thumbnail; } @Override public StreamType getStreamType() { return StreamType.VIDEO_STREAM; } @Override public long getDuration() { return -1; } @Override public long getViewCount() { return -1; } @Override public String getUploaderName() { return "Avgle.net"; } @Override public String getUploaderUrl() { return AvgleParser.BASE + "/"; } @Override public String getTextualUploadDate() { return null; } @Override public org.schabi.newpipe.extractor.localization.DateWrapper getUploadDate() { return null; }
}
final class AvgleParser {
    static final String BASE = "https://avgle.net";
    private static final Pattern VIDEO_SOURCE = Pattern.compile("(?:https?:)?//avgle\\.net/video_source\\.php[^\\\"'\\s<]+|/video_source\\.php[^\\\"'\\s<]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAGE = Pattern.compile("(?:[?&](?:page|p)=)(\\d+)");
    private AvgleParser() { }
    static Document fetch(final String url) throws IOException, ExtractionException { final Response response = NewPipe.getDownloader().get(normalize(url), headers()); if (response.responseCode() != 200) throw new ParsingException("Avgle.net returned HTTP " + response.responseCode()); return Jsoup.parse(response.responseBody(), response.latestUrl()); }
    static List<AvgleItem> cards(final Document document) { final LinkedHashMap<String, AvgleItem> result = new LinkedHashMap<>(); for (final Element link : document.select("a[href*='video.php']")) { final String url = normalize(link.absUrl("href")); if (!isVideo(url) || result.containsKey(url)) continue; final Element image = link.selectFirst("img"); final Element container = link.closest("article, .card, .video-card, .video-item, li, div"); final String title = first(link.attr("title"), image == null ? "" : image.attr("alt"), container == null ? "" : container.select("h2, h3, .title").text(), link.text()); if (!title.isEmpty()) result.put(url, new AvgleItem(url, title, image == null ? "" : normalize(image.absUrl("src")))); } return new ArrayList<>(result.values()); }
    static List<AvgleItem> episodes(final Document document, final String title, final String thumbnail, final String currentUrl) {
        final LinkedHashMap<String, AvgleItem> result = new LinkedHashMap<>();
        for (final Element link : document.select(".episode-list a[href*='video.php'], a[href*='fid='][href*='ep=']")) {
            final String url = normalize(link.absUrl("href"));
            if (!isVideo(url) || url.equals(normalize(currentUrl)) || result.containsKey(url)) continue;
            final String episode = first(link.text(), "Video");
            result.put(url, new AvgleItem(url, title + " — " + episode, thumbnail));
        }
        return new ArrayList<>(result.values());
    }
    static String sourceUrl(final Document document) { final Matcher matcher = VIDEO_SOURCE.matcher(document.html().replace("&amp;", "&")); return matcher.find() ? normalize(matcher.group()) : ""; }
    static boolean isVideo(final String url) { return url != null && normalize(url).startsWith(BASE + "/video.php?") && normalize(url).contains("slug="); }
    static String id(final String url) throws ParsingException { final String normalized = normalize(url); final int index = normalized.indexOf('?'); if (!isVideo(normalized) || index < 0) throw new ParsingException("Invalid Avgle.net video URL: " + url); return normalized.substring(index + 1); }
    static String searchUrl(final String query, final int page) { return BASE + "/search.php?lang=ja&q=" + encode(query) + (page > 1 ? "&page=" + page : ""); }
    static int page(final Page page) { try { return Math.max(1, Integer.parseInt(page.getId())); } catch (final Exception ignored) { return 1; } }
    static boolean hasNext(final Document document, final int current) { for (final Element link : document.select(".pagination a[href], a[rel=next]")) { final Matcher m = PAGE.matcher(link.attr("href")); if ((m.find() && Integer.parseInt(m.group(1)) > current) || link.text().trim().equalsIgnoreCase("next")) return true; } return false; }
    static String meta(final Document document, final String selector) { final Element e = document.selectFirst(selector); return e == null ? "" : e.attr("content").trim(); }
    static String normalize(final String value) { if (value == null) return ""; final String v = value.trim().replace("&amp;", "&"); return v.startsWith("//") ? "https:" + v : v.startsWith("/") ? BASE + v : v; }
    static String first(final String... values) { for (final String v : values) if (v != null && !v.trim().isEmpty()) return Jsoup.parse(v).text().replaceAll("\\s+", " ").trim(); return ""; }
    static String mark(final String media, final String page) { return media + "#avgle=1&ref=" + encode(page); }
    static Map<String, List<String>> headers() { return headers(BASE + "/", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"); }
    static Map<String, List<String>> headers(final String referer, final String accept) { final Map<String, List<String>> h = new HashMap<>(); h.put("User-Agent", Collections.singletonList("Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")); h.put("Referer", Collections.singletonList(referer)); h.put("Accept", Collections.singletonList(accept)); h.put("Accept-Language", Collections.singletonList("ja-JP,ja;q=0.9,en-US;q=0.8")); return h; }
    private static String encode(final String value) { try { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name()); } catch (final Exception e) { throw new IllegalStateException(e); } }
}
