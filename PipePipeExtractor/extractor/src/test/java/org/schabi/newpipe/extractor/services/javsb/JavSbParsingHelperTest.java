package org.schabi.newpipe.extractor.services.javsb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.util.List;

public class JavSbParsingHelperTest {
    private static final String VIDEO_HTML = "<html><head>"
            + "<meta property=\"og:title\" content=\"DANDY-479 Japanese title\">"
            + "<meta property=\"og:image\" content=\"/upload/vod/cover.jpg\">"
            + "<meta property=\"og:description\" content=\"Video description\">"
            + "</head><body><div id=\"player\"><iframe src=\"/ja/static/player/videojs.html?src="
            + "22a7902ff093f509ed4aba25768d4d11&player=videojs\"></iframe></div>"
            + "<div class=\"text-secondary\"><span>Duration:</span><span>3:15:45</span></div>"
            + "<a href=\"/ja/genres/lesbian.html\" title=\"Lesbian\">Lesbian</a>"
            + "<div class=\"thumbnail group\"><a href=\"/ja/jav/related-123-1-1.html\">"
            + "<img data-src=\"/upload/vod/related.jpg\" alt=\"Related title\"></a>"
            + "<span class=\"bottom-1 right-1\">12:34</span></div></body></html>";

    @Test
    public void extractsJavSbMetadataAndRelatedVideo() throws Exception {
        final Document document = Jsoup.parse(VIDEO_HTML, JavSbParsingHelper.BASE_URL + "/ja/");

        assertEquals("DANDY-479 Japanese title", JavSbParsingHelper.extractTitle(document));
        assertEquals("https://jav.sb/upload/vod/cover.jpg",
                JavSbParsingHelper.extractThumbnail(document));
        assertEquals(11745, JavSbParsingHelper.extractDuration(document));
        assertEquals("Lesbian", JavSbParsingHelper.extractTags(document).get(0));
        assertEquals("related-123-1-1", JavSbParsingHelper.extractId(
                "https://jav.sb/ja/jav/related-123-1-1.html"));

        final List<JavSbSearchResult> related =
                JavSbParsingHelper.extractRelatedVideoCards(document, 10);
        assertFalse(related.isEmpty());
        assertEquals("Related title", related.get(0).title);
        assertEquals("https://jav.sb/upload/vod/related.jpg", related.get(0).thumbnailUrl);
    }

    @Test
    public void createsTheSameStreamSignatureAsTheJavSbPlayer() {
        assertEquals("6ZFINbkpMCtKSeSlybypbVpVYF0CQf1g0KfhV57GIckytOBRHMwkDZRWBt",
                JavSbParsingHelper.createStreamSignature(
                        "22a7902ff093f509ed4aba25768d4d11@1783646308"));
    }
}
