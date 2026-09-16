package org.schabi.newpipe.extractor.services.simplehtml;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public final class SimpleHtmlVideoServiceTest {
    @Test
    public void unpackPackerRestoresLulustreamHlsUrl() {
        final String packedPlayer = "eval(function(p,a,c,k,e,d){}('"
                + "0://1.example/2/3.m3u8',4,4,'https|cdn|hls|master'.split('|')))";

        final String unpacked = Parser.unpackPacker(packedPlayer);

        assertTrue(unpacked.contains("https://cdn.example/hls/master.m3u8"));
    }
}
