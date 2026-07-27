package org.schabi.newpipe.extractor.services.javsb;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;

public final class JavSbStreamLinkHandlerFactory extends LinkHandlerFactory {
    private static final JavSbStreamLinkHandlerFactory INSTANCE =
            new JavSbStreamLinkHandlerFactory();

    public static JavSbStreamLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    private JavSbStreamLinkHandlerFactory() {
    }

    @Override
    public String getId(final String url) throws ParsingException {
        return JavSbParsingHelper.extractId(url);
    }

    @Override
    public String getUrl(final String id) {
        return JavSbParsingHelper.videoUrlFromId(id);
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        return url != null
                && JavSbParsingHelper.normalizeUrl(url).contains("jav.sb/")
                && JavSbParsingHelper.normalizeUrl(url).contains("/jav/")
                && JavSbParsingHelper.normalizeUrl(url).contains(".html");
    }
}

