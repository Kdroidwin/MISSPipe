package org.schabi.newpipe.extractor.services.pornhub;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.List;

public final class PornhubKioskLinkHandlerFactory extends ListLinkHandlerFactory {
    private static final PornhubKioskLinkHandlerFactory INSTANCE =
            new PornhubKioskLinkHandlerFactory();

    public static PornhubKioskLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    private PornhubKioskLinkHandlerFactory() {
    }

    @Override
    public String getUrl(final String id,
                         final List<FilterItem> contentFilters,
                         final List<FilterItem> sortFilter) throws ParsingException {
        switch (id) {
            case "popular":
                return PornhubParsingHelper.BASE_URL + "/video?o=mv&t=m";
            case "recommended":
                return PornhubParsingHelper.BASE_URL + "/video?o=tr&t=m";
            case "latest":
            default:
                return PornhubParsingHelper.BASE_URL + "/video?o=cm";
        }
    }

    @Override
    public String getId(final String url) {
        final String normalized = PornhubParsingHelper.normalizeUrl(url);
        if (normalized != null && normalized.contains("o=mv")) {
            return "popular";
        }
        if (normalized != null && normalized.contains("o=tr")) {
            return "recommended";
        }
        return "latest";
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        final String normalized = PornhubParsingHelper.normalizeUrl(url);
        return normalized != null && normalized.contains("pornhub.com/video");
    }
}
