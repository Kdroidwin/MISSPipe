package org.schabi.newpipe.extractor.services.eightyfivepo;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;

public final class EightyFivePoStreamLinkHandlerFactory extends LinkHandlerFactory {
    private static final EightyFivePoStreamLinkHandlerFactory INSTANCE =
            new EightyFivePoStreamLinkHandlerFactory();

    public static EightyFivePoStreamLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    private EightyFivePoStreamLinkHandlerFactory() {
    }

    @Override
    public String getId(final String url) throws ParsingException {
        return EightyFivePoParsingHelper.extractId(url);
    }

    @Override
    public String getUrl(final String id) {
        return EightyFivePoParsingHelper.videoUrlFromId(id);
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        final String normalized = EightyFivePoParsingHelper.normalizeUrl(url);
        return normalized != null
                && normalized.contains("85po.com/")
                && (normalized.contains("/v/") || normalized.contains("/embed/"));
    }
}
