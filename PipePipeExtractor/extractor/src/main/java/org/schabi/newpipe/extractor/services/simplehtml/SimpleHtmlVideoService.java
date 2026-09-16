package org.schabi.newpipe.extractor.services.simplehtml;

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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Lightweight extractor for sites whose public HTML contains search cards and media URLs. */
public final class SimpleHtmlVideoService extends StreamingService {
    public enum Site { HANIME1, JAV_FUN }
    private final Config config;
    public SimpleHtmlVideoService(final int id, final Site site) {
        super(id, site == Site.HANIME1 ? "Hanime1.me" : "JAV-FUN.cc",
                Collections.singletonList(ServiceInfo.MediaCapability.VIDEO));
        config = site == Site.HANIME1 ? Config.HANIME1 : Config.JAV_FUN;
    }
    @Override public String getBaseUrl() { return config.base; }
    @Override public LinkHandlerFactory getStreamLHFactory() { return new StreamFactory(config); }
    @Override public ListLinkHandlerFactory getChannelLHFactory() { return null; }
    @Override public ListLinkHandlerFactory getChannelTabLHFactory() { return null; }
    @Override public ListLinkHandlerFactory getPlaylistLHFactory() { return null; }
    @Override public SearchQueryHandlerFactory getSearchQHFactory() { return new SearchFactory(config); }
    @Override public ListLinkHandlerFactory getCommentsLHFactory() { return null; }
    @Override public SearchExtractor getSearchExtractor(final SearchQueryHandler h) { return new Search(this, h, config); }
    @Override public SuggestionExtractor getSuggestionExtractor() { return null; }
    @Override public SubscriptionExtractor getSubscriptionExtractor() { return null; }
    @Override public KioskList getKioskList() throws ExtractionException {
        final KioskList kiosks = new KioskList(this);
        try { kiosks.addKioskEntry((s, u, id) -> new Latest(s, new LatestFactory(config).fromId(id), id, config), new LatestFactory(config), "latest"); kiosks.setDefaultKiosk("latest"); return kiosks; }
        catch (final Exception e) { throw new ExtractionException("Could not initialize latest kiosk", e); }
    }
    @Override public ChannelExtractor getChannelExtractor(final ListLinkHandler h) { return null; }
    @Override public ChannelTabExtractor getChannelTabExtractor(final ListLinkHandler h) throws ExtractionException { throw new ExtractionException("Channel tabs unavailable"); }
    @Override public PlaylistExtractor getPlaylistExtractor(final ListLinkHandler h) throws ExtractionException { throw new ExtractionException("Playlists unavailable"); }
    @Override public StreamExtractor getStreamExtractor(final LinkHandler h) { return new Stream(this, h, config); }
    @Override public CommentsExtractor getCommentsExtractor(final ListLinkHandler h) { return null; }
    @Override public BulletCommentsExtractor getBulletCommentsExtractor(final ListLinkHandler h) { return null; }
}

final class Config {
    static final Config HANIME1 = new Config("https://hanime1.me", "/watch?v=", "/search?query=", true);
    static final Config JAV_FUN = new Config("https://jav-fun.cc", "/archives/", "/?s=", false);
    final String base, videoPath, searchPath; final boolean directMedia;
    Config(final String b, final String v, final String s, final boolean d) { base = b; videoPath = v; searchPath = s; directMedia = d; }
    String search(final String query) { return base + searchPath + encode(query); }
    String absolute(final String value) { if (value == null) return ""; String v = value.trim().replace("&amp;", "&"); return v.startsWith("//") ? "https:" + v : v.startsWith("/") ? base + v : v; }
    boolean accepts(final String url) { return url != null && absolute(url).startsWith(base + videoPath); }
    String id(final String url) throws ParsingException { String value = absolute(url); if (!accepts(value)) throw new ParsingException("Invalid video URL: " + url); return value.substring(value.indexOf(videoPath) + videoPath.length()); }
    Map<String, List<String>> headers() {
        final Map<String, List<String>> h = new java.util.HashMap<>();
        h.put("User-Agent", Collections.singletonList("Mozilla/5.0 (X11; Linux x86_64; rv:155.0) Gecko/20100101 Firefox/155.0"));
        h.put("Accept", Collections.singletonList("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"));
        h.put("Accept-Language", Collections.singletonList("ja"));
        h.put("Referer", Collections.singletonList(base + "/"));
        return h;
    }
    static String encode(final String value) { try { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name()); } catch (final Exception e) { throw new IllegalStateException(e); } }
}

final class StreamFactory extends LinkHandlerFactory {
    private final Config c; StreamFactory(final Config config) { c = config; }
    @Override public String getId(final String url) throws ParsingException { return c.id(url); }
    @Override public String getUrl(final String id) { return c.base + c.videoPath + id; }
    @Override public boolean onAcceptUrl(final String url) { return c.accepts(url); }
}
final class SearchFactory extends SearchQueryHandlerFactory {
    private final Config c; SearchFactory(final Config config) { c = config; }
    @Override public String getUrl(final String query, final List<FilterItem> content, final List<FilterItem> sort) { return c.search(query); }
}
final class LatestFactory extends ListLinkHandlerFactory {
    private final Config c; LatestFactory(final Config config) { c = config; }
    @Override public String getId(final String url) { return "latest"; }
    @Override public String getUrl(final String id, final List<FilterItem> content, final List<FilterItem> sort) { return c.base + "/"; }
    @Override public boolean onAcceptUrl(final String url) { return url != null && c.absolute(url).startsWith(c.base); }
}
final class Latest extends KioskExtractor<org.schabi.newpipe.extractor.stream.StreamInfoItem> {
    private final Config c; private Document page;
    Latest(final StreamingService s, final ListLinkHandler h, final String id, final Config config) { super(s, h, id); c = config; }
    @Override public void onFetchPage(@Nonnull final Downloader d) throws IOException, ExtractionException { page = Parser.fetch(c.base + "/", c); }
    @Nonnull @Override public String getName() { return "Latest"; }
    @Nonnull @Override public InfoItemsPage<org.schabi.newpipe.extractor.stream.StreamInfoItem> getInitialPage() throws ExtractionException { if (page == null) throw new ParsingException("Latest page was not fetched"); final StreamInfoItemsCollector out = new StreamInfoItemsCollector(getServiceId()); for (final Item item : Parser.cards(page, c)) out.commit(new ItemExtractor(item)); return new InfoItemsPage<>(out, null); }
    @Override public InfoItemsPage<org.schabi.newpipe.extractor.stream.StreamInfoItem> getPage(final Page ignored) { return new InfoItemsPage<>(new StreamInfoItemsCollector(getServiceId()), null); }
}
final class Search extends SearchExtractor {
    private final Config c; Search(final StreamingService s, final SearchQueryHandler h, final Config config) { super(s, h); c = config; }
    @Override public void onFetchPage(@Nonnull final Downloader d) { }
    @Override protected InfoItemsPage<InfoItem> getInitialPageInternal() throws IOException, ExtractionException { return result(); }
    @Override protected InfoItemsPage<InfoItem> getPageInternal(final Page page) throws IOException, ExtractionException { return new ListExtractor.InfoItemsPage<>(new MultiInfoItemsCollector(getServiceId()), null); }
    private InfoItemsPage<InfoItem> result() throws IOException, ExtractionException { final MultiInfoItemsCollector out = new MultiInfoItemsCollector(getServiceId()); for (final Item item : Parser.cards(Parser.fetch(c.search(getSearchString()), c), c)) out.commit(new ItemExtractor(item)); return new ListExtractor.InfoItemsPage<>(out, null); }
}
final class Stream extends StreamExtractor {
    private final Config c; private Document page;
    Stream(final StreamingService s, final LinkHandler h, final Config config) { super(s, h); c = config; }
    @Override public void onFetchPage(@Nonnull final Downloader d) throws IOException, ExtractionException { page = Parser.fetch(getUrl(), c); }
    private Document doc() throws ParsingException { if (page == null) throw new ParsingException("Page was not fetched"); return page; }
    @Nonnull @Override public String getName() throws ParsingException { return Parser.first(Parser.meta(doc(), "meta[property=og:title]"), doc().title(), getId()); }
    @Nonnull @Override public String getThumbnailUrl() throws ParsingException { return Parser.meta(doc(), "meta[property=og:image]"); }
    @Nonnull @Override public Description getDescription() throws ParsingException { final String text = Parser.first(Parser.meta(doc(), "meta[property=og:description]"), Parser.meta(doc(), "meta[name=description]")); return text.isEmpty() ? Description.EMPTY_DESCRIPTION : new Description(text, Description.PLAIN_TEXT); }
    @Override public long getLength() { return -1; }
    @Nonnull @Override public String getUploaderName() { return getService().getServiceInfo().getName(); }
    @Nonnull @Override public String getUploaderUrl() { return c.base + "/"; }
    @Nonnull @Override public List<String> getTags() { return Collections.emptyList(); }
    @Override public String getTextualUploadDate() { return ""; }
    @Override public List<AudioStream> getAudioStreams() { return Collections.emptyList(); }
    @Override public List<VideoStream> getVideoStreams() throws IOException, ExtractionException { final LinkedHashMap<String, VideoStream> out = new LinkedHashMap<>(); List<String> media = Parser.media(doc(), c.directMedia); String playerPage = ""; if (media.isEmpty() && !c.directMedia) { final Element frame = doc().selectFirst("iframe[src]"); if (frame != null) { playerPage = frame.absUrl("src"); media = Parser.media(Parser.fetchExternal(playerPage, c.base + "/"), true); } } for (final String url : media) { final String resolution = Parser.resolution(url); final String content = c.directMedia ? url + "#hanime1=1" : url + "#javfun=1&ref=" + Config.encode(playerPage); out.put(url, new VideoStream.Builder().setId(resolution).setContent(content, true).setResolution(resolution).setMediaFormat(MediaFormat.MPEG_4).setDeliveryMethod(url.contains(".m3u8") ? DeliveryMethod.HLS : DeliveryMethod.PROGRESSIVE_HTTP).setIsVideoOnly(false).build()); } if (out.isEmpty()) throw new ParsingException("No direct media URL found on page"); return new ArrayList<>(out.values()); }
    @Override public List<VideoStream> getVideoOnlyStreams() { return Collections.emptyList(); }
    @Override public StreamType getStreamType() { return StreamType.VIDEO_STREAM; }
    @Override public InfoItemsCollector<? extends InfoItem, ? extends InfoItemExtractor> getRelatedItems() throws ParsingException { final StreamInfoItemsCollector out = new StreamInfoItemsCollector(getServiceId()); for (final Item item : Parser.cards(doc(), c)) if (!item.url.equals(getUrl())) out.commit(new ItemExtractor(item)); return out; }
    @Nonnull @Override public List<MetaInfo> getMetaInfo() { return Collections.emptyList(); }
}
final class Item { final String url, title, image; Item(final String u, final String t, final String i) { url = u; title = t; image = i; } }
final class ItemExtractor implements StreamInfoItemExtractor {
    private final Item i; ItemExtractor(final Item item) { i = item; }
    @Override public String getName() { return i.title; } @Override public String getUrl() { return i.url; } @Override public String getThumbnailUrl() { return i.image; } @Override public StreamType getStreamType() { return StreamType.VIDEO_STREAM; } @Override public long getDuration() { return -1; } @Override public long getViewCount() { return -1; } @Override public String getUploaderName() { return ""; } @Override public String getUploaderUrl() { return ""; } @Nullable @Override public String getTextualUploadDate() { return null; } @Nullable @Override public DateWrapper getUploadDate() { return null; }
}
final class Parser {
    private static final Pattern MEDIA = Pattern.compile("https?:(?:\\\\/|/){2}[^\\\"'<>\\s]+?\\.(?:mp4|m3u8)[^\\\"'<>\\s]*", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUALITY = Pattern.compile("(?:-|_)(\\d{3,4})p(?:[.?-]|$)", Pattern.CASE_INSENSITIVE);
    private Parser() { }
    static Document fetch(final String url, final Config c) throws IOException, ExtractionException { final Response r = NewPipe.getDownloader().get(url, c.headers()); if (r.responseCode() != 200) throw new ParsingException("HTTP " + r.responseCode()); return Jsoup.parse(r.responseBody(), r.latestUrl()); }
    static List<Item> cards(final Document d, final Config c) { final LinkedHashMap<String, Item> out = new LinkedHashMap<>(); for (final Element a : d.select("a[href]")) { final String url = c.absolute(a.absUrl("href")); if (!c.accepts(url) || out.containsKey(url)) continue; Element box = c.directMedia ? a.closest(".playlist-hover-wrap, .video-item-container, .video-card") : a.closest("article"); if (box == null) box = a.parent(); final Element img = box.selectFirst("img[data-src], img[src]"); final String title = first(a.attr("title"), img == null ? "" : img.attr("alt"), box.select("h1,h2,h3,h4,.video-title,.title,.entry-header").text(), a.text()); final String image = img == null ? "" : c.absolute(img.hasAttr("data-src") ? img.absUrl("data-src") : img.absUrl("src")); if (!title.isEmpty()) out.put(url, new Item(url, title, image)); } return new ArrayList<>(out.values()); }
    static List<String> media(final Document d, final boolean includeScriptUrls) { final LinkedHashMap<String, String> out = new LinkedHashMap<>(); for (final Element e : d.select("video[src], video source[src]")) { final String url = decodeHtmlUrl(e.absUrl("src")); if (!url.isEmpty()) out.put(url, url); } if (includeScriptUrls) { final String html = unpackPacker(d.html()); final Matcher m = MEDIA.matcher(html); while (m.find()) { final String url = decodeHtmlUrl(m.group().replace("\\/", "/")); out.put(url, url); } } return new ArrayList<>(out.values()); }
    private static String decodeHtmlUrl(final String url) { return url.replace("&amp;", "&"); }
    static Document fetchExternal(final String url, final String referer) throws IOException, ExtractionException { final Map<String, List<String>> headers = new java.util.HashMap<>(); headers.put("User-Agent", Collections.singletonList("Mozilla/5.0 (X11; Linux x86_64; rv:155.0) Gecko/20100101 Firefox/155.0")); headers.put("Accept", Collections.singletonList("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")); headers.put("Accept-Language", Collections.singletonList("ja")); headers.put("Referer", Collections.singletonList(referer)); headers.put("Cookie", Collections.singletonList("ref_url=jav-fun.cc; aff=690")); final Response r = NewPipe.getDownloader().get(url, headers); if (r.responseCode() != 200) throw new ParsingException("Embedded player returned HTTP " + r.responseCode()); return Jsoup.parse(r.responseBody(), r.latestUrl()); }
    private static String unpackPacker(final String html) {
        final Matcher match = Pattern.compile("eval\\(function\\(p,a,c,k,e,d\\)\\{.*?\\}\\('((?:\\\\.|[^'])*)',(\\d+),(\\d+),'((?:\\\\.|[^'])*)'\\.split\\('\\\\|'\\)", Pattern.DOTALL).matcher(html);
        if (!match.find()) return html;
        // Some ordinary page scripts match the loose P.A.C.K.E.R. prefix but omit the
        // dictionary argument. They are not packed media data, so leave the page intact.
        if (match.group(1) == null || match.group(2) == null || match.group(3) == null
                || match.group(4) == null) return html;
        String packed = match.group(1).replace("\\\\'", "'").replace("\\\\\\\\", "\\\\"); final int base = Integer.parseInt(match.group(2)); final int count = Integer.parseInt(match.group(3)); final String[] words = match.group(4).replace("\\\\'", "'").split("\\\\|", -1);
        for (int i = count - 1; i >= 0; i--) if (i < words.length && !words[i].isEmpty()) packed = packed.replaceAll("\\b" + Pattern.quote(toBase(i, base)) + "\\b", Matcher.quoteReplacement(words[i]));
        return html + "\n" + packed;
    }
    private static String toBase(int value, final int base) { final String chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"; if (value == 0) return "0"; final StringBuilder out = new StringBuilder(); while (value > 0) { out.insert(0, chars.charAt(value % base)); value /= base; } return out.toString(); }
    static String meta(final Document d, final String selector) { final Element e = d.selectFirst(selector); return e == null ? "" : e.attr("content").trim(); }
    static String first(final String... values) { for (final String v : values) if (v != null && !v.trim().isEmpty()) return Jsoup.parse(v).text().replaceAll("\\s+", " ").trim(); return ""; }
    static String resolution(final String url) { final Matcher m = QUALITY.matcher(url); return m.find() ? m.group(1) + "p" : "Auto"; }
}
