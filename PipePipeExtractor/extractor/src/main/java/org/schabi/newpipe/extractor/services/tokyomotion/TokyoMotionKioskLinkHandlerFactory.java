package org.schabi.newpipe.extractor.services.tokyomotion;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.List;

public final class TokyoMotionKioskLinkHandlerFactory extends ListLinkHandlerFactory {
    private static final TokyoMotionKioskLinkHandlerFactory INSTANCE =
            new TokyoMotionKioskLinkHandlerFactory();

    public static TokyoMotionKioskLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    private TokyoMotionKioskLinkHandlerFactory() {
    }

    @Override
    public String getId(final String url) {
        if (url != null && url.contains("sort=popular")) {
            return "popular";
        }
        if (url != null && url.contains("sort=rating")) {
            return "recommended";
        }
        return "latest";
    }

    @Override
    public String getUrl(final String id,
                         final List<FilterItem> selectedContentFilter,
                         final List<FilterItem> selectedSortFilter) throws ParsingException {
        switch (id) {
            case "popular":
                return TokyoMotionParsingHelper.BASE_URL + "/videos?sort=popular";
            case "recommended":
                return TokyoMotionParsingHelper.BASE_URL + "/videos?sort=rating";
            case "latest":
            default:
                return TokyoMotionParsingHelper.BASE_URL + "/videos";
        }
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        return url != null && TokyoMotionParsingHelper.normalizeUrl(url).contains("tokyomotion.net/");
    }
}

