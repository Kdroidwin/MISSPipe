package org.schabi.newpipe.extractor.services.missav;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;

public final class MissAvStreamLinkHandlerFactory extends LinkHandlerFactory {
    private static final MissAvStreamLinkHandlerFactory INSTANCE =
            new MissAvStreamLinkHandlerFactory();

    public static MissAvStreamLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    private MissAvStreamLinkHandlerFactory() {
    }

    @Override
    public String getId(final String url) throws ParsingException {
        return MissAvParsingHelper.extractId(url);
    }

    @Override
    public String getUrl(final String id) {
        return MissAvParsingHelper.toVideoUrl(id);
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        return url != null
                && (url.contains("missav.ws/") || url.contains("missav.ai/"))
                && !url.endsWith("missav.ws")
                && !url.endsWith("missav.ws/");
    }
}
