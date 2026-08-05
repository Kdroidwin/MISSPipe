package org.schabi.newpipe.extractor.services.xhamster;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsExtractor;
import org.schabi.newpipe.extractor.comments.CommentsExtractor;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.extractor.linkhandler.*;
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.stream.*;
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** xHamster extractor based on the public page data used by unofficial-api-for-xhamster. */
public final class XHamsterService extends StreamingService {
    public XHamsterService(final int id) { super(id, "xHamster", Collections.singletonList(ServiceInfo.MediaCapability.VIDEO)); }
    @Override public String getBaseUrl() { return XHamsterParser.BASE; }
    @Override public LinkHandlerFactory getStreamLHFactory() { return XHamsterLinks.INSTANCE; }
    @Override public ListLinkHandlerFactory getChannelLHFactory() { return null; }
    @Override public ListLinkHandlerFactory getChannelTabLHFactory() { return null; }
    @Override public ListLinkHandlerFactory getPlaylistLHFactory() { return null; }
    @Override public SearchQueryHandlerFactory getSearchQHFactory() { return XHamsterSearchFactory.INSTANCE; }
    @Override public ListLinkHandlerFactory getCommentsLHFactory() { return null; }
    @Override public SearchExtractor getSearchExtractor(final SearchQueryHandler h) { return new XHamsterSearch(this, h); }
    @Override public SuggestionExtractor getSuggestionExtractor() { return null; }
    @Override public SubscriptionExtractor getSubscriptionExtractor() { return null; }
    @Override public KioskList getKioskList() { return new KioskList(this); }
    @Override public ChannelExtractor getChannelExtractor(final ListLinkHandler h) { return null; }
    @Override public ChannelTabExtractor getChannelTabExtractor(final ListLinkHandler h) throws ExtractionException { throw new ExtractionException("xHamster channel tabs unavailable"); }
    @Override public PlaylistExtractor getPlaylistExtractor(final ListLinkHandler h) throws ExtractionException { throw new ExtractionException("xHamster playlists unavailable"); }
    @Override public StreamExtractor getStreamExtractor(final LinkHandler h) { return new XHamsterStream(this, h); }
    @Override public CommentsExtractor getCommentsExtractor(final ListLinkHandler h) { return null; }
    @Override public BulletCommentsExtractor getBulletCommentsExtractor(final ListLinkHandler h) { return null; }
}
final class XHamsterLinks extends LinkHandlerFactory {
    static final XHamsterLinks INSTANCE = new XHamsterLinks();
    @Override public String getId(String url) throws ParsingException { return XHamsterParser.id(url); }
    @Override public String getUrl(String id) { return XHamsterParser.BASE + "/videos/" + id; }
    @Override public boolean onAcceptUrl(String url) { return url != null && url.contains("xhamster.com/videos/"); }
}
final class XHamsterSearchFactory extends SearchQueryHandlerFactory {
    static final XHamsterSearchFactory INSTANCE = new XHamsterSearchFactory();
    @Override public String getUrl(String q, List<FilterItem> c, List<FilterItem> s) { return XHamsterParser.BASE + "/search/" + XHamsterParser.encode(q); }
}
final class XHamsterSearch extends SearchExtractor {
    XHamsterSearch(StreamingService s, SearchQueryHandler h) { super(s,h); }
    @Override public void onFetchPage(@Nonnull Downloader d) { }
    @Override protected InfoItemsPage<InfoItem> getInitialPageInternal() throws IOException, ExtractionException {
        MultiInfoItemsCollector out=new MultiInfoItemsCollector(getServiceId());
        for(XHamsterItem i:XHamsterParser.cards(XHamsterParser.fetch(XHamsterParser.BASE+"/search/"+XHamsterParser.encode(getSearchString())),40)) out.commit(new XHamsterItemExtractor(i));
        return new ListExtractor.InfoItemsPage<>(out,null);
    }
    @Override protected InfoItemsPage<InfoItem> getPageInternal(Page p) { return InfoItemsPage.emptyPage(); }
}
final class XHamsterStream extends StreamExtractor {
    private Document page;
    XHamsterStream(StreamingService s,LinkHandler h){super(s,h);}
    @Override public void onFetchPage(@Nonnull Downloader d)throws IOException,ExtractionException{page=XHamsterParser.fetch(getUrl());}
    private void ready()throws ParsingException{if(page==null)throw new ParsingException("xHamster page was not fetched");}
    @Nonnull @Override public String getName()throws ParsingException{ready();return XHamsterParser.value(page,"meta[property=og:title],h1",getId());}
    @Nonnull @Override public String getThumbnailUrl()throws ParsingException{ready();return XHamsterParser.value(page,"meta[property=og:image]","");}
    @Nonnull @Override public Description getDescription()throws ParsingException{ready();String d=XHamsterParser.value(page,"meta[name=description],meta[property=og:description]","");return d.isEmpty()?Description.EMPTY_DESCRIPTION:new Description(d,Description.PLAIN_TEXT);}
    @Override public long getLength()throws ParsingException{ready();return XHamsterParser.duration(page);}
    @Nonnull @Override public String getUploaderName(){return "xHamster";} @Nonnull @Override public String getUploaderUrl(){return XHamsterParser.BASE;}
    @Nonnull @Override public List<String> getTags(){return Collections.emptyList();} @Override public String getTextualUploadDate(){return "";}
    @Override public List<AudioStream> getAudioStreams(){return Collections.emptyList();}
    @Override public List<VideoStream> getVideoStreams()throws IOException,ExtractionException{ready();String hls=XHamsterParser.hls(page);if(hls.isEmpty())throw new ParsingException("Could not find xHamster HLS URL");return Collections.singletonList(new VideoStream.Builder().setId("hls").setContent(hls,true).setManifestUrl(hls).setResolution("HLS").setDeliveryMethod(DeliveryMethod.HLS).setMediaFormat(MediaFormat.MPEG_4).setIsVideoOnly(false).build());}
    @Override public List<VideoStream> getVideoOnlyStreams(){return Collections.emptyList();}@Override public StreamType getStreamType(){return StreamType.VIDEO_STREAM;}
    @Override public InfoItemsCollector<? extends InfoItem,? extends InfoItemExtractor> getRelatedItems()throws IOException,ExtractionException{ready();StreamInfoItemsCollector out=new StreamInfoItemsCollector(getServiceId());for(XHamsterItem i:XHamsterParser.relatedCards(page,30))if(!i.id.equals(getId()))out.commit(new XHamsterItemExtractor(i));return out;}
    @Nonnull @Override public List<MetaInfo> getMetaInfo(){return Collections.emptyList();}
}
final class XHamsterItem { final String id,url,title,thumb;final long duration; XHamsterItem(String i,String u,String t,String th,long d){id=i;url=u;title=t;thumb=th;duration=d;} }
final class XHamsterItemExtractor implements StreamInfoItemExtractor { final XHamsterItem i;XHamsterItemExtractor(XHamsterItem x){i=x;} public String getName(){return i.title;}public String getUrl(){return i.url;}public String getThumbnailUrl(){return i.thumb;}public StreamType getStreamType(){return StreamType.VIDEO_STREAM;}public long getDuration(){return i.duration;}public long getViewCount(){return -1;}public String getUploaderName(){return "xHamster";}public String getUploaderUrl(){return XHamsterParser.BASE;}@Nullable public String getTextualUploadDate(){return null;}@Nullable public DateWrapper getUploadDate(){return null;} }
final class XHamsterParser {
    static final String BASE="https://jp.xhamster.com"; private static final Pattern HLS=Pattern.compile("https://[^\\\" ]+?_TPL_\\.(?:h264|av1)\\.mp4\\.m3u8"); private static final Pattern DUR=Pattern.compile("(\\d+):(\\d{2})(?::(\\d{2}))?");
    static Document fetch(String u)throws IOException,ExtractionException{Response r=NewPipe.getDownloader().get(u,headers());return Jsoup.parse(r.responseBody(),u);}
    static Map<String,List<String>> headers(){Map<String,List<String>> h=new HashMap<>();h.put("Referer",Collections.singletonList(BASE+"/"));h.put("User-Agent",Collections.singletonList("Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"));return h;}
    static String id(String u)throws ParsingException{try{String p=URI.create(u).getPath();int n=p.indexOf("/videos/");if(n>=0)return p.substring(n+8).split("/",2)[0];}catch(Exception ignored){}throw new ParsingException("Could not extract xHamster id: "+u);}
    static String encode(String q){try{return URLEncoder.encode(q==null?"":q,StandardCharsets.UTF_8.name()).replace("+","%20");}catch(Exception e){throw new IllegalStateException(e);}}
    static String value(Document d,String s,String f){Element e=d.selectFirst(s);if(e==null)return f;String v=e.hasAttr("content")?e.attr("content"):e.text();return Jsoup.parse(v).text().trim();}
    static String hls(Document d){Matcher m=HLS.matcher(d.html().replace("\\/","/"));return m.find()?m.group():"";}
    static long duration(Document d){return durationText(d.text());}
    static long duration(Element e){return durationText(e.text());}
    private static long durationText(String text){Matcher m=DUR.matcher(text);if(!m.find())return -1;long a=Long.parseLong(m.group(1)),b=Long.parseLong(m.group(2));return m.group(3)==null?a*60+b:a*3600+b*60+Long.parseLong(m.group(3));}
    static List<XHamsterItem> cards(Element root,int max){
        return parseCards(root, "div.video-thumb,div[data-role=mobile-video-thumb]", max);
    }
    static List<XHamsterItem> relatedCards(Element root,int max){
        LinkedHashMap<String,XHamsterItem> items=new LinkedHashMap<>();
        for(XHamsterItem item:initialVideoList(root,max))items.putIfAbsent(item.id,item);
        for(XHamsterItem item:parseCards(root, "div.video-thumb,div[data-role=mobile-video-thumb]", max)){
            items.putIfAbsent(item.id,item);
            if(items.size()>=max)break;
        }
        return new ArrayList<>(items.values());
    }
    private static List<XHamsterItem> parseCards(Element root,String selector,int max){
        LinkedHashMap<String,XHamsterItem> out=new LinkedHashMap<>();
        for(Element card:root.select(selector)){
            Element link=card.selectFirst("a[data-role=thumb-link][href*=/videos/]");
            Element titleLink=card.selectFirst("a[data-role=thumb-link][title]");
            if(link==null)continue;
            String url=link.absUrl("href");
            String id=firstNonEmpty(card.attr("data-video-id"), idFromUrl(url));
            String title=firstNonEmpty(link.attr("aria-label"),titleLink==null?"":titleLink.attr("title"),titleLink==null?"":titleLink.text());
            if(id.isEmpty()||url.isEmpty()||title.isEmpty())continue;
            Element image=card.selectFirst("img[data-role=thumb-preview-img]");
            String thumbnail=image==null?"":firstNonEmpty(image.absUrl("src"),image.absUrl("data-src"),firstSrc(image.attr("srcset")));
            Element durationElement=card.selectFirst("[data-role=video-duration]");
            out.putIfAbsent(id,new XHamsterItem(id,url,title,thumbnail,duration(durationElement==null?card:durationElement)));
            if(out.size()>=max)break;
        }
        return new ArrayList<>(out.values());
    }
    private static String firstNonEmpty(String... values){for(String value:values)if(value!=null&&!value.trim().isEmpty())return value.trim();return "";}
    private static String idFromUrl(String url){try{return id(url);}catch(ParsingException ignored){return "";}}
    private static String firstSrc(String srcset){if(srcset==null||srcset.trim().isEmpty())return "";String first=srcset.split(",",2)[0].trim();int space=first.indexOf(' ');return space<0?first:first.substring(0,space);}
    private static List<XHamsterItem> initialVideoList(Element root,int max){
        try{
            Element script=root.selectFirst("script#initials-script");
            if(script==null)return Collections.emptyList();
            String source=script.data();
            String marker="window.initials=";
            int start=source.indexOf(marker);
            int end=source.lastIndexOf(';');
            if(start<0||end<=start)return Collections.emptyList();
            JsonObject initials=JsonParser.object().from(source.substring(start+marker.length(),end));
            JsonObject related=object(initials,"relatedVideos");
            JsonObject initialData=object(related,"videoTabInitialData");
            JsonObject listProps=object(initialData,"videoListProps");
            Object values=listProps==null?null:listProps.get("videoThumbProps");
            if(!(values instanceof JsonArray))return Collections.emptyList();
            LinkedHashMap<String,XHamsterItem> result=new LinkedHashMap<>();
            for(Object value:(JsonArray)values){
                if(!(value instanceof JsonObject))continue;
                JsonObject item=(JsonObject)value;
                String id=string(item.get("id"));
                String url=string(item.get("pageURL"));
                String title=string(item.get("title"));
                if(id.isEmpty()||url.isEmpty()||title.isEmpty())continue;
                result.putIfAbsent(id,new XHamsterItem(id,url,title,firstNonEmpty(string(item.get("thumbURL")),string(item.get("imageURL"))),number(item.get("duration"))));
                if(result.size()>=max)break;
            }
            return new ArrayList<>(result.values());
        }catch(Exception ignored){return Collections.emptyList();}
    }
    private static JsonObject object(JsonObject parent,String key){if(parent==null)return null;Object value=parent.get(key);return value instanceof JsonObject?(JsonObject)value:null;}
    private static String string(Object value){return value==null?"":String.valueOf(value).trim();}
    private static long number(Object value){return value instanceof Number?((Number)value).longValue():-1;}
}
