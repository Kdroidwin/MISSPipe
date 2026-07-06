package org.schabi.newpipe.extractor.services.pornhub;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PornhubSearchEngineTest {
    @Test
    public void normalizesUnicodeWidthCaseAndEntities() {
        assertEquals("fc2 ppv 123 abc", PornhubTextNormalizer.normalize("ＦＣ２-ＰＰＶ&nbsp;１２３ ABC"));
    }

    @Test
    public void exactTitleScoresAbovePartialAndNoiseIsRejected() {
        final List<String> queryTokens = PornhubTextNormalizer.tokens("レズビアン カップル");
        final String normalizedQuery = PornhubTextNormalizer.normalize("レズビアン カップル");

        final PornhubSearchResult exact = new PornhubSearchResult(
                "a", PornhubParsingHelper.videoUrlFromId("a"), "レズビアン カップル",
                "", 1, "ChellWray", PornhubParsingHelper.BASE_URL + "/model/chellwray",
                Arrays.asList("レズビアン"), Arrays.asList("カップル"), "レズビアン カップル", 0);
        final PornhubSearchResult partial = new PornhubSearchResult(
                "b", PornhubParsingHelper.videoUrlFromId("b"), "本物のレズビアン動画",
                "", 1, "ChellWray", PornhubParsingHelper.BASE_URL + "/model/chellwray",
                Arrays.asList("カップル"), Arrays.asList("日本"), "本物のレズビアン動画 カップル", 0);
        final PornhubSearchResult noise = new PornhubSearchResult(
                "c", PornhubParsingHelper.videoUrlFromId("c"), "Completely different",
                "", 1, "Uploader", PornhubParsingHelper.BASE_URL + "/users/uploader",
                Arrays.asList("other"), Arrays.asList("misc"), "Completely different", 0);

        assertTrue(PornhubSearchEngine.score(exact, normalizedQuery, queryTokens)
                > PornhubSearchEngine.score(partial, normalizedQuery, queryTokens));
        assertEquals(0, PornhubSearchEngine.score(noise, normalizedQuery, queryTokens));
    }

    @Test
    public void extractsAndDeduplicatesRelatedVideosFromFocusedRelatedSection() throws Exception {
        final Document document = Jsoup.parse(
                "<html><body>"
                        + "<div id='relatedVideosListing'>"
                        + "  <li><a href='/view_video.php?viewkey=rel1' title='Related One' "
                        + "class='linkVideoThumb'><img data-src='//ei.phncdn.com/videos/1.jpg'></a>"
                        + "  <a class='thumbnailTitle' href='/view_video.php?viewkey=rel1'>Related One</a></li>"
                        + "  <li><a href='/view_video.php?viewkey=rel1' title='Related One duplicate' "
                        + "class='linkVideoThumb'><img data-src='//ei.phncdn.com/videos/1b.jpg'></a></li>"
                        + "  <li><a href='/view_video.php?viewkey=rel2' title='Related Two' "
                        + "class='linkVideoThumb'><img data-mediumthumb='https://ei.phncdn.com/videos/2.jpg'></a></li>"
                        + "</div>"
                        + "<div><a href='/view_video.php?viewkey=noise' title='Noise'>Noise</a></div>"
                        + "</body></html>",
                PornhubParsingHelper.BASE_URL + "/view_video.php?viewkey=current");

        final List<PornhubSearchResult> related =
                PornhubParsingHelper.extractRelatedVideoCards(document, "current", 10,
                        PornhubParsingHelper.BASE_URL + "/view_video.php?viewkey=current");

        assertEquals(2, related.size());
        assertEquals("rel1", related.get(0).id);
        assertEquals("rel2", related.get(1).id);
        assertFalse(related.get(0).thumbnail.isEmpty());
    }

    @Test
    public void channelLinksAcceptPornhubChannelTypes() throws Exception {
        final PornhubChannelLinkHandlerFactory factory =
                PornhubChannelLinkHandlerFactory.getInstance();
        assertTrue(factory.onAcceptUrl("https://jp.pornhub.com/model/chellwray"));
        assertTrue(factory.onAcceptUrl("https://www.pornhub.com/users/example"));
        assertTrue(factory.onAcceptUrl("https://pornhub.com/channels/example-channel"));
        assertTrue(factory.onAcceptUrl("https://pornhub.com/pornstar/example-star"));
        assertEquals("model/chellwray", factory.getId("https://jp.pornhub.com/model/chellwray"));
    }
}
