package org.schabi.newpipe.extractor.services.tokyomotion;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;

public final class TokyoMotionStreamLinkHandlerFactory extends LinkHandlerFactory {
    private static final TokyoMotionStreamLinkHandlerFactory INSTANCE =
            new TokyoMotionStreamLinkHandlerFactory();

    public static TokyoMotionStreamLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    private TokyoMotionStreamLinkHandlerFactory() {
    }

    @Override
    public String getId(final String url) throws ParsingException {
        return TokyoMotionParsingHelper.extractId(url);
    }

    @Override
    public String getUrl(final String id) {
        return TokyoMotionParsingHelper.videoUrlFromId(id);
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        return url != null
                && TokyoMotionParsingHelper.normalizeUrl(url).contains("tokyomotion.net/")
                && TokyoMotionParsingHelper.normalizeUrl(url).contains("/video/");
    }
}

