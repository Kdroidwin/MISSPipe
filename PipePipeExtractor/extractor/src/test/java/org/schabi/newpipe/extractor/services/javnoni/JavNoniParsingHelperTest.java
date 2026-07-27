package org.schabi.newpipe.extractor.services.javnoni;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;

public class JavNoniParsingHelperTest {
    private static final String VIDEO_HTML = "<html><head>"
            + "<meta property=\"og:title\" content=\"Sample Title\">"
            + "<meta property=\"og:image\" content=\"https://example.com/thumb.jpg\">"
            + "<meta itemprop=\"duration\" content=\"P0DT0H27M17S\">"
            + "<meta itemprop=\"embedURL\" content=\"https://luluvdo.com/e/abc123\">"
            + "</head><body>"
            + "<div class=\"tags-list\"><a href=\"https://jav-noni.vip/archives/tag/japan/\""
            + " title=\"Japan\">Japan</a></div>"
            + "<div class=\"under-video-block\"><h2>Related videos</h2>"
            + "<article data-main-thumb=\"https://example.com/related.jpg\">"
            + "<a href=\"https://jav-noni.vip/archives/sample-related/\""
            + " title=\"Related Title\"><span class=\"duration\">12:34</span></a>"
            + "</article></div></body></html>";

    @Test
    public void extractsVideoMetadata() {
        final Document document = Jsoup.parse(VIDEO_HTML, JavNoniParsingHelper.BASE_URL + "/");

        assertEquals("Sample Title", JavNoniParsingHelper.extractTitle(document));
        assertEquals("https://example.com/thumb.jpg",
                JavNoniParsingHelper.extractThumbnail(document));
        assertEquals(1637, JavNoniParsingHelper.extractDuration(document));
        assertEquals("https://luluvdo.com/e/abc123",
                JavNoniParsingHelper.extractEmbedUrl(document));
        assertEquals("Japan", JavNoniParsingHelper.extractTags(document).get(0));
    }

    @Test
    public void extractsRelatedVideoCards() {
        final Document document = Jsoup.parse(VIDEO_HTML, JavNoniParsingHelper.BASE_URL + "/");

        final List<JavNoniSearchResult> results =
                JavNoniParsingHelper.extractRelatedVideoCards(document, 10);

        assertFalse(results.isEmpty());
        assertEquals("sample-related", results.get(0).id);
        assertEquals("Related Title", results.get(0).title);
        assertEquals("https://example.com/related.jpg", results.get(0).thumbnailUrl);
        assertEquals(754, results.get(0).duration);
    }

    @Test
    public void extractsSearchVideoCardsWithoutArchiveIndexes() {
        final Document document = Jsoup.parse("<html><body>"
                + "<a href=\"https://jav-noni.vip/archives/category/amateur/\">Category</a>"
                + "<article data-main-thumb=\"https://example.com/search.jpg\">"
                + "<a href=\"https://jav-noni.vip/archives/search-hit/\" title=\"Search Hit\">"
                + "<span class=\"duration\">01:02:03</span></a></article>"
                + "</body></html>", JavNoniParsingHelper.BASE_URL + "/");

        final List<JavNoniSearchResult> results =
                JavNoniParsingHelper.extractVideoCards(document, 10);

        assertEquals(1, results.size());
        assertEquals("search-hit", results.get(0).id);
        assertEquals(3723, results.get(0).duration);
    }

    @Test
    public void extractsHlsFromPackedLuluPlayerScript() throws Exception {
        final String packedHtml = "<script>eval(function(p,a,c,k,e,d){"
                + "while(c--)if(k[c])p=p.replace(new RegExp('\\\\b'+c.toString(a)+'\\\\b','g'),k[c]);"
                + "return p}('0({1:[{2:\"3://4.5/6.7?8=9\"}]});',10,10,"
                + "'jwplayer|sources|file|https|cdn|tnmr|master|m3u8|token|abc'.split('|')))"
                + "</script>";
        final LinkedHashMap<String, JavNoniVideoSource> sources = new LinkedHashMap<>();
        final Method method = JavNoniParsingHelper.class.getDeclaredMethod(
                "putVideoSourcesFromHtml", LinkedHashMap.class, String.class);
        method.setAccessible(true);

        method.invoke(null, sources, packedHtml);

        assertEquals(1, sources.size());
        final JavNoniVideoSource source = sources.values().iterator().next();
        assertEquals("https://cdn.tnmr/master.m3u8?token=abc#javnoni=1", source.url);
        assertEquals(DeliveryMethod.HLS, source.deliveryMethod);
    }
}
