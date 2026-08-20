package org.schabi.newpipe.extractor.services.xnxx;

import static org.junit.Assert.assertEquals;

import org.jsoup.Jsoup;
import org.junit.Test;

public final class XnxxParserTest {
    @Test
    public void currentListingCardUsesTheVideoLinkInsteadOfTheUploaderLink() {
        final String html = "<div class='thumb-block video'>"
                + "<div class='thumb'><a href='/video-1hy04769/example'><img "
                + "data-sfwthumb='https://cdn.example/thumbnail.jpg'></a></div>"
                + "<div class='thumb-under'><div class='uploader'><a href='/porn-maker/name'>"
                + "Uploader</a></div><a class='title' href='/video-1hy04769/example' "
                + "title='Video title'>Video title</a><div class='metadata'>10 min</div></div>"
                + "</div>";

        final java.util.List<XnxxItem> items = XnxxParser.cards(Jsoup.parse(html), 10);

        assertEquals(1, items.size());
        assertEquals("1hy04769", items.get(0).id);
        assertEquals("Video title", items.get(0).title);
        assertEquals("https://cdn.example/thumbnail.jpg", items.get(0).thumbnail);
    }

    @Test
    public void searchUsesTheCanonicalFirstPageUrl() {
        assertEquals("https://www.xnxx.com/search/test+query",
                XnxxParser.searchUrl("test query", 0));
        assertEquals("https://www.xnxx.com/search/test+query/1",
                XnxxParser.searchUrl("test query", 1));
    }
}
