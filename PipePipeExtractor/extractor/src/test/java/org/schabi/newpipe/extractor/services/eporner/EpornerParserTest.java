package org.schabi.newpipe.extractor.services.eporner;

import org.jsoup.Jsoup;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class EpornerParserTest {
    @Test
    public void kioskUsesTheVideoListingInsteadOfTheMixedHomePage() {
        assertEquals("https://www.eporner.com/cat/all/", EpornerParser.kioskUrl());
    }

    @Test
    public void cardsParseTheCategoryListingMarkup() {
        final List<EpornerItem> items = EpornerParser.cards(Jsoup.parse(
                "<div class='mb' data-id='17681385'><div class='mbimg'><a "
                        + "href='/video-DJ999oYH9ei/example/'><img data-src='https://cdn.example/thumb.jpg'/></a></div>"
                        + "<p class='mbtit'><a href='/video-DJ999oYH9ei/example/'>Example title</a></p>"
                        + "<span class='mbtim'>10:49</span><span class='mbvie'>637,458</span></div>",
                EpornerParser.BASE), 10);

        assertEquals(1, items.size());
        assertEquals("17681385", items.get(0).id);
        assertEquals("Example title", items.get(0).title);
        assertFalse(items.get(0).thumbnail.isEmpty());
    }
}
