package org.schabi.newpipe.extractor.services.pornhub;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;

public final class PornhubStreamLinkHandlerFactory extends LinkHandlerFactory {
    private static final PornhubStreamLinkHandlerFactory INSTANCE =
            new PornhubStreamLinkHandlerFactory();

    public static PornhubStreamLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    private PornhubStreamLinkHandlerFactory() {
    }

    @Override
    public String getId(final String url) throws ParsingException {
        return PornhubParsingHelper.extractId(url);
    }

    @Override
    public String getUrl(final String id) {
        return PornhubParsingHelper.videoUrlFromId(id);
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        final String normalized = PornhubParsingHelper.normalizeUrl(url);
        return normalized != null
                && normalized.contains("pornhub.com/")
                && normalized.contains("view_video.php")
                && normalized.contains("viewkey=");
    }
}
