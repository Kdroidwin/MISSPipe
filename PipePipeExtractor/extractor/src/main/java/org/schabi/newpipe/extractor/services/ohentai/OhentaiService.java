package org.schabi.newpipe.extractor.services.ohentai;

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

/** OHentai extractor using only the first-party page and its JW Player source. */
public final class OhentaiService extends StreamingService {
    public OhentaiService(final int id) { super(id, "OHentai", Collections.singletonList(ServiceInfo.MediaCapability.VIDEO)); }
    @Override public String getBaseUrl() { return OhentaiParser.BASE; }
    @Override public LinkHandlerFactory getStreamLHFactory() { return OhentaiLinkHandlerFactory.INSTANCE; }
    @Override public ListLinkHandlerFactory getChannelLHFactory() { return null; }
    @Override public ListLinkHandlerFactory getChannelTabLHFactory() { return null; }
    @Override public ListLinkHandlerFactory getPlaylistLHFactory() { return null; }
    @Override public SearchQueryHandlerFactory getSearchQHFactory() { return OhentaiSearchFactory.INSTANCE; }
    @Override public ListLinkHandlerFactory getCommentsLHFactory() { return null; }
    @Override public SearchExtractor getSearchExtractor(final SearchQueryHandler handler) { return new OhentaiSearchExtractor(this, handler); }
    @Override public SuggestionExtractor getSuggestionExtractor() { return null; }
    @Override public SubscriptionExtractor getSubscriptionExtractor() { return null; }
    @Override public KioskList getKioskList() { return new KioskList(this); }
    @Override public ChannelExtractor getChannelExtractor(final ListLinkHandler handler) { return null; }
    @Override public ChannelTabExtractor getChannelTabExtractor(final ListLinkHandler handler) throws ExtractionException { throw new ExtractionException("OHentai channel tabs unavailable"); }
    @Override public PlaylistExtractor getPlaylistExtractor(final ListLinkHandler handler) throws ExtractionException { throw new ExtractionException("OHentai playlists unavailable"); }
    @Override public StreamExtractor getStreamExtractor(final LinkHandler handler) { return new OhentaiStreamExtractor(this, handler); }
    @Override public CommentsExtractor getCommentsExtractor(final ListLinkHandler handler) { return null; }
    @Override public BulletCommentsExtractor getBulletCommentsExtractor(final ListLinkHandler handler) { return null; }
}

final class OhentaiLinkHandlerFactory extends LinkHandlerFactory {
    static final OhentaiLinkHandlerFactory INSTANCE = new OhentaiLinkHandlerFactory();
    @Override public String getId(final String url) throws ParsingException { return OhentaiParser.id(url); }
    @Override public String getUrl(final String id) { return OhentaiParser.BASE + "/detail.php?vid=" + id; }
    @Override public boolean onAcceptUrl(final String url) { return OhentaiParser.isVideo(url); }
    @Override public LinkHandler fromUrl(final String url, final String baseUrl) throws ParsingException {
        if (!acceptUrl(url)) throw new ParsingException("URL not accepted: " + url);
        return new LinkHandler(url, OhentaiParser.normalize(url), getId(url));
    }
}

final class OhentaiSearchFactory extends SearchQueryHandlerFactory {
    static final OhentaiSearchFactory INSTANCE = new OhentaiSearchFactory();
    @Override public String getUrl(final String query, final List<FilterItem> content, final List<FilterItem> sort) { return OhentaiParser.searchUrl(query); }
}

final class OhentaiSearchExtractor extends SearchExtractor {
    OhentaiSearchExtractor(final StreamingService service, final SearchQueryHandler handler) { super(service, handler); }
    @Override public void onFetchPage(@Nonnull final Downloader downloader) { }
    @Override protected InfoItemsPage<InfoItem> getInitialPageInternal() throws IOException, ExtractionException {
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        for (final OhentaiItem item : OhentaiParser.cards(OhentaiParser.fetch(OhentaiParser.searchUrl(getSearchString())), 40)) collector.commit(new OhentaiItemExtractor(item));
        return new ListExtractor.InfoItemsPage<>(collector, null);
    }
    @Override protected InfoItemsPage<InfoItem> getPageInternal(final Page page) { return InfoItemsPage.emptyPage(); }
}

final class OhentaiStreamExtractor extends StreamExtractor {
    private Document document;
    OhentaiStreamExtractor(final StreamingService service, final LinkHandler handler) { super(service, handler); }
    @Override public void onFetchPage(@Nonnull final Downloader downloader) throws IOException, ExtractionException { document = OhentaiParser.fetch(getUrl()); }
    @Nonnull @Override public String getName() throws ParsingException { page(); return OhentaiParser.title(document, getId()); }
    @Nonnull @Override public String getThumbnailUrl() throws ParsingException { page(); return OhentaiParser.meta(document, "meta[property=og:image]"); }
    @Nonnull @Override public Description getDescription() throws ParsingException { page(); final String text = OhentaiParser.meta(document, "meta[property=og:description], meta[name=description]"); return text.isEmpty() ? Description.EMPTY_DESCRIPTION : new Description(text, Description.PLAIN_TEXT); }
    @Override public long getLength() { return -1; }
    @Nonnull @Override public String getUploaderName() { return "OHentai"; }
    @Nonnull @Override public String getUploaderUrl() { return OhentaiParser.BASE + "/"; }
    @Nonnull @Override public List<String> getTags() throws ParsingException { page(); final List<String> tags = new ArrayList<>(); for (final Element tag : document.select("a[href*=tagsearch.php]")) { final String value = tag.text().trim(); if (!value.isEmpty() && !tags.contains(value)) tags.add(value); } return tags; }
    @Override public String getTextualUploadDate() { return ""; }
    @Override public List<AudioStream> getAudioStreams() { return Collections.emptyList(); }
    @Override public List<VideoStream> getVideoStreams() throws IOException, ExtractionException {
        page(); final String source = OhentaiParser.source(document); if (source.isEmpty()) throw new ParsingException("Could not find OHentai video URL");
        return Collections.singletonList(new VideoStream.Builder().setId("MP4").setContent(OhentaiParser.mark(source, getUrl()), true).setResolution("MP4").setMediaFormat(MediaFormat.MPEG_4).setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP).setIsVideoOnly(false).build());
    }
    @Override public List<VideoStream> getVideoOnlyStreams() { return Collections.emptyList(); }
    @Override public StreamType getStreamType() { return StreamType.VIDEO_STREAM; }
    @Override public InfoItemsCollector<? extends InfoItem, ? extends InfoItemExtractor> getRelatedItems() throws IOException, ExtractionException { page(); final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId()); for (final OhentaiItem item : OhentaiParser.cards(document, 40)) if (!item.id.equals(getId())) collector.commit(new OhentaiItemExtractor(item)); return collector; }
    @Nonnull @Override public List<MetaInfo> getMetaInfo() { return Collections.emptyList(); }
    private void page() throws ParsingException { if (document == null) throw new ParsingException("OHentai page was not fetched"); }
}

final class OhentaiItem { final String id, url, title, thumbnail; OhentaiItem(final String id, final String url, final String title, final String thumbnail) { this.id = id; this.url = url; this.title = title; this.thumbnail = thumbnail; } }
final class OhentaiItemExtractor implements StreamInfoItemExtractor {
    private final OhentaiItem item; OhentaiItemExtractor(final OhentaiItem item) { this.item = item; }
    @Override public String getName() { return item.title; } @Override public String getUrl() { return item.url; } @Override public String getThumbnailUrl() { return item.thumbnail; } @Override public StreamType getStreamType() { return StreamType.VIDEO_STREAM; } @Override public long getDuration() { return -1; } @Override public long getViewCount() { return -1; } @Override public String getUploaderName() { return "OHentai"; } @Override public String getUploaderUrl() { return OhentaiParser.BASE + "/"; } @Nullable @Override public String getTextualUploadDate() { return null; } @Nullable @Override public DateWrapper getUploadDate() { return null; }
}

final class OhentaiParser {
    static final String BASE = "https://ohentai.org";
    private static final Pattern ID = Pattern.compile("[?&]vid=([^&#]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SOURCE = Pattern.compile("[\\\"']file[\\\"']\\s*:\\s*[\\\"']([^\\\"']+)[\\\"']", Pattern.CASE_INSENSITIVE);
    private OhentaiParser() { }
    static Document fetch(final String url) throws IOException, ExtractionException { final Response response = NewPipe.getDownloader().get(normalize(url), headers()); if (response.responseCode() != 200) throw new ParsingException("OHentai page request returned HTTP " + response.responseCode()); return Jsoup.parse(response.responseBody(), response.latestUrl()); }
    static List<OhentaiItem> cards(final Element scope, final int maximum) { if (scope == null) return Collections.emptyList(); final LinkedHashMap<String, OhentaiItem> items = new LinkedHashMap<>(); for (final Element link : scope.select("a[href*=detail.php][href*=vid]")) { final String url = normalize(link.absUrl("href")); final String id = idOrEmpty(url); if (id.isEmpty() || items.containsKey(id)) continue; final Element card = link.parent(); final Element image = link.selectFirst("img"); final String title = first(link.attr("title"), image == null ? "" : image.attr("alt"), card.text()); if (title.isEmpty()) continue; final String thumbnail = image == null ? "" : normalize(image.absUrl("src")); items.put(id, new OhentaiItem(id, url, title, thumbnail)); if (items.size() >= maximum) break; } return new ArrayList<>(items.values()); }
    static String source(final Document document) { final Matcher matcher = SOURCE.matcher(document.html()); while (matcher.find()) { final String value = normalize(matcher.group(1)); if (value.contains(".mp4") || value.contains(".m3u8")) return value; } return ""; }
    static String title(final Document document, final String fallback) { return first(meta(document, "meta[property=og:title]"), document.title(), fallback).replaceFirst("\\s*Hentai Video Stream.*$", "").trim(); }
    static String meta(final Document document, final String selector) { final Element element = document.selectFirst(selector); return element == null ? "" : element.attr("content").trim(); }
    static String searchUrl(final String query) { return BASE + "/search.php?k=" + encode(query); }
    static String mark(final String source, final String page) { return source + "#ohentai=1&ref=" + encode(page); }
    static boolean isVideo(final String url) { return url != null && normalize(url).matches("https?://(?:www\\.)?ohentai\\.org/detail\\.php\\?[^#]*\\bvid=[^&#]+.*"); }
    static String id(final String url) throws ParsingException { final Matcher m = ID.matcher(normalize(url)); if (m.find()) return m.group(1); throw new ParsingException("Could not extract OHentai id: " + url); }
    static String normalize(final String value) { if (value == null) return ""; final String result = value.trim().replace("&amp;", "&"); return result.startsWith("//") ? "https:" + result : result.startsWith("/") ? BASE + result : result; }
    private static Map<String, List<String>> headers() { final Map<String, List<String>> values = new HashMap<>(); values.put("User-Agent", Collections.singletonList("Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")); values.put("Referer", Collections.singletonList(BASE + "/")); values.put("Accept-Language", Collections.singletonList("ja-JP,ja;q=0.9,en-US;q=0.8")); return values; }
    private static String encode(final String value) { try { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name()); } catch (final Exception e) { throw new IllegalStateException("UTF-8 unavailable", e); } }
    private static String idOrEmpty(final String url) { try { return id(url); } catch (final ParsingException ignored) { return ""; } }
    private static String first(final String... values) { for (final String value : values) if (value != null && !value.trim().isEmpty()) return Jsoup.parse(value).text().replaceAll("\\s+", " ").trim(); return ""; }
}
