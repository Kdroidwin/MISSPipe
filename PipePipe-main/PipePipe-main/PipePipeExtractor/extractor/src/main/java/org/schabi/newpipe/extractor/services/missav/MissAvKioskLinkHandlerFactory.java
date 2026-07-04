package org.schabi.newpipe.extractor.services.missav;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.List;

public final class MissAvKioskLinkHandlerFactory extends ListLinkHandlerFactory {
    private static final MissAvKioskLinkHandlerFactory INSTANCE =
            new MissAvKioskLinkHandlerFactory();

    public static MissAvKioskLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    private MissAvKioskLinkHandlerFactory() {
    }

    @Override
    public String getId(final String url) {
        if (url != null && url.contains("popular")) {
            return "popular";
        }
        if (url != null && url.contains("recommended")) {
            return "recommended";
        }
        return "latest";
    }

    @Override
    public String getUrl(final String id, final List<FilterItem> contentFilter,
                         final List<FilterItem> sortFilter) throws ParsingException {
        switch (id) {
            case "popular":
                return MissAvParsingHelper.BASE_URL + "/" + MissAvParsingHelper.DEFAULT_LANGUAGE
                        + "/popular";
            case "recommended":
                return MissAvParsingHelper.BASE_URL + "/" + MissAvParsingHelper.DEFAULT_LANGUAGE;
            case "latest":
            default:
                return MissAvParsingHelper.BASE_URL + "/" + MissAvParsingHelper.DEFAULT_LANGUAGE;
        }
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        return url != null && url.contains("missav.ws");
    }
}
