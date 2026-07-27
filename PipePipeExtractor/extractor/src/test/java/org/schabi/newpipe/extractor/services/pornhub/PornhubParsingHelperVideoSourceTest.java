package org.schabi.newpipe.extractor.services.pornhub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;

public class PornhubParsingHelperVideoSourceTest {
    @Test
    public void skipsRemoteMediaAndThumbnailTransformsAsFinalStreams() throws Exception {
        final String html = "\"mediaDefinitions\":["
                + "{\"format\":\"hls\",\"videoUrl\":\"https:\\/\\/iv-h.phncdn.com\\/path\\/master.m3u8?\","
                + "\"quality\":\"720\"},"
                + "{\"format\":\"mp4\",\"videoUrl\":\"https:\\/\\/jp.pornhub.com\\/video\\/get_media?s=abc\","
                + "\"quality\":[]}]"
                + "<img src=\"https://pix-cdn77.phncdn.com/videos/202604/12/45086215/"
                + "original_45086215.mp4/plain/ex:1:no/bg:0:0:0/rs:fit:323:182/vts:322\">"
                + "\"https://hv-h.phncdn.com/hls/videos/202408/01/455956561/"
                + "720P_4000K_455956561.mp4/seg-12-v1-a1.ts?h=abc\"";
        final LinkedHashMap<String, PornhubVideoSource> sources = new LinkedHashMap<>();
        final Method method = PornhubParsingHelper.class.getDeclaredMethod(
                "putVideoSourcesFromHtml", LinkedHashMap.class, String.class);
        method.setAccessible(true);

        method.invoke(null, sources, html);

        assertEquals(1, sources.size());
        final PornhubVideoSource source = sources.values().iterator().next();
        assertEquals(DeliveryMethod.HLS, source.deliveryMethod);
        assertEquals("720p", source.resolution);
        assertEquals("https://iv-h.phncdn.com/path/master.m3u8?#pornhub=1", source.url);
        assertFalse(source.url.contains("/video/get_media"));
        assertFalse(source.url.contains("/plain/"));
        assertFalse(source.url.contains("/seg-"));
    }
}
