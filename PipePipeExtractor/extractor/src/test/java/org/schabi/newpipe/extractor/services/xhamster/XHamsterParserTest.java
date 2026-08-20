package org.schabi.newpipe.extractor.services.xhamster;

import org.jsoup.Jsoup;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class XHamsterParserTest {
    @Test
    public void latestUrlUsesTheCanonicalNewestFeed() {
        assertEquals("https://jp.xhamster.com/newest", XHamsterParser.latestUrl());
    }

    @Test
    public void cardsAcceptTheCurrentThumbnailMarkup() {
        final List<XHamsterItem> items = XHamsterParser.cards(Jsoup.parse(
                "<div class='video-thumb' data-video-id='123'>"
                        + "<a data-role='thumb-link' href='https://jp.xhamster.com/videos/example-123' "
                        + "aria-label='Example title'><img data-src='https://cdn.example/thumb.jpg'/></a>"
                        + "<span data-role='video-duration'>01:02</span></div>"), 10);

        assertEquals(1, items.size());
        assertEquals("123", items.get(0).id);
        assertEquals("Example title", items.get(0).title);
        assertFalse(items.get(0).thumb.isEmpty());
    }
}
