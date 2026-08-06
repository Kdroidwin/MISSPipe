package org.schabi.newpipe.extractor.services.xnxx;

import static org.junit.Assert.assertEquals;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.util.List;

public final class XnxxParserTest {
    @Test
    public void extractsSearchCardsWithRelativeUrlsWithoutDocumentBaseUri() {
        final Document document = Jsoup.parse("<div id=\"video_h7353fa\" data-eid=\"h7353fa\" "
                + "class=\"thumb-block\"><div class=\"thumb\"><img "
                + "data-src=\"//img-hw.xnxx-cdn.com/videos/thumbs/ab/cd.jpg\"></div>"
                + "<div class=\"thumb-under\"><p><a href=\"/video-h7353fa/bwc_\" "
                + "title=\"BWC search result\">BWC search result</a></p>"
                + "<span class=\"metadata\">12 min</span></div></div>");

        final List<XnxxItem> items = XnxxParser.cards(document, 10);

        assertEquals(1, items.size());
        assertEquals("h7353fa", items.get(0).id);
        assertEquals("https://www.xnxx.com/video-h7353fa/bwc_", items.get(0).url);
        assertEquals("https://img-hw.xnxx-cdn.com/videos/thumbs/ab/cd.jpg",
                items.get(0).thumbnail);
    }
}
