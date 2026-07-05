package org.schabi.newpipe.extractor.services.kissjav;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;

public final class KissJavStreamLinkHandlerFactory extends LinkHandlerFactory {
    private static final KissJavStreamLinkHandlerFactory INSTANCE =
            new KissJavStreamLinkHandlerFactory();

    public static KissJavStreamLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    private KissJavStreamLinkHandlerFactory() {
    }

    @Override
    public String getId(final String url) throws ParsingException {
        return KissJavParsingHelper.extractId(url);
    }

    @Override
    public String getUrl(final String id) {
        return KissJavParsingHelper.BASE_URL + "/embed/" + id;
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        return url != null
                && (KissJavParsingHelper.normalizeUrl(url).contains("kissjav.li/video/")
                || KissJavParsingHelper.normalizeUrl(url).contains("kissjav.li/embed/"));
    }
}
