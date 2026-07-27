package org.schabi.newpipe.extractor.services.tokyomotion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.util.List;

public class TokyoMotionParsingHelperTest {
    private static final String VIDEO_HTML = "<html><head>"
            + "<meta property=\"og:title\" content=\"Japanese video title\">"
            + "<meta property=\"og:description\" content=\"Video description\">"
            + "<meta property=\"og:image\" content=\"https://cdn.tokyo-motion.net/cover.jpg\">"
            + "<meta property=\"video:duration\" content=\"162.03\">"
            + "<meta property=\"video:tag\" content=\"tag one\">"
            + "<meta property=\"video:tag\" content=\"tag two\">"
            + "</head><body><video id=\"vjsplayer\"><source "
            + "src=\"/vsrc/sd/token\" title=\"SD\" type=\"video/mp4\"></video>"
            + "<div class=\"user-container\"><a href=\"/user/creator\"><span>creator</span></a></div>"
            + "<div id=\"related_videos\"><div class=\"well\"><a class=\"thumb-popu\" "
            + "href=\"/video/1234567/related-title\"><img src=\"https://cdn.tokyo-motion.net/t.jpg\" "
            + "alt=\"Related video\"><span class=\"video-title\">Related video</span>"
            + "<div class=\"duration\">12:34</div></a></div></div></body></html>";

    @Test
    public void extractsTokyoMotionPageData() {
        final Document document = Jsoup.parse(VIDEO_HTML, TokyoMotionParsingHelper.BASE_URL + "/");

        assertEquals("Japanese video title", TokyoMotionParsingHelper.extractTitle(document));
        assertEquals("Video description", TokyoMotionParsingHelper.extractDescription(document));
        assertEquals(162, TokyoMotionParsingHelper.extractDuration(document));
        assertEquals(2, TokyoMotionParsingHelper.extractTags(document).size());

        final List<TokyoMotionVideoSource> sources =
                TokyoMotionParsingHelper.findVideoSources(document, TokyoMotionParsingHelper.BASE_URL);
        assertEquals(1, sources.size());
        assertEquals("https://www.tokyomotion.net/vsrc/sd/token#tokyomotion=1", sources.get(0).url);

        final List<TokyoMotionSearchResult> related =
                TokyoMotionParsingHelper.extractRelatedVideoCards(document, 10);
        assertFalse(related.isEmpty());
        assertEquals("1234567", related.get(0).id);
        assertEquals("Related video", related.get(0).title);
        assertEquals(754, related.get(0).duration);
        assertEquals("creator", TokyoMotionParsingHelper.extractUploaderName(document));
        assertEquals("https://www.tokyomotion.net/user/creator",
                TokyoMotionParsingHelper.extractUploaderUrl(document));
    }

    @Test
    public void extractsTokyoMotionChannelData() throws Exception {
        final Document document = Jsoup.parse("<div class=\"panel-heading\"><a href=\"/user/creator\">"
                + "creator profile</a></div><a href=\"/user/creator\"><img src=\"https://cdn.example/a.jpg\">"
                + "</a><div id=\"info-container\"><span class=\"text-white\">About creator</span></div>"
                + "<div id=\"video_2\"><a href=\"/video/2/title\"><img src=\"/cover.jpg\">"
                + "</a><div class=\"video-title\">Channel video</div><div class=\"duration\">01:02</div></div>",
                TokyoMotionParsingHelper.BASE_URL + "/");

        assertEquals("creator", TokyoMotionParsingHelper.extractChannelName(document, "fallback"));
        assertEquals("https://cdn.example/a.jpg", TokyoMotionParsingHelper.extractChannelAvatarUrl(document));
        assertEquals("About creator", TokyoMotionParsingHelper.extractChannelDescription(document));
        assertEquals(1, TokyoMotionParsingHelper.extractVideoCards(document, 24).size());
        assertEquals("Channel video", TokyoMotionParsingHelper.extractVideoCards(document, 24).get(0).title);

        final TokyoMotionChannelLinkHandlerFactory factory =
                TokyoMotionChannelLinkHandlerFactory.getInstance();
        assertEquals("creator", factory.getId("https://www.tokyomotion.net/user/creator/videos"));
        assertEquals("https://www.tokyomotion.net/user/creator",
                factory.getUrl("creator", java.util.Collections.emptyList(), null));
    }
}
