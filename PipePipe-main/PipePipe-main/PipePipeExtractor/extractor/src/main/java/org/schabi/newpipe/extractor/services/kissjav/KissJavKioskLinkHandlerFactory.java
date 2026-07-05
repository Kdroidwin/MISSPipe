package org.schabi.newpipe.extractor.services.kissjav;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.List;

public final class KissJavKioskLinkHandlerFactory extends ListLinkHandlerFactory {
    private static final KissJavKioskLinkHandlerFactory INSTANCE =
            new KissJavKioskLinkHandlerFactory();

    public static KissJavKioskLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    private KissJavKioskLinkHandlerFactory() {
    }

    @Override
    public String getId(final String url) {
        if (url != null && url.contains("most-popular")) {
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
                return KissJavParsingHelper.BASE_URL + "/most-popular/?sort_by=video_viewed";
            case "recommended":
                return KissJavParsingHelper.BASE_URL + "/most-popular/?sort_by=rating";
            case "latest":
            default:
                return KissJavParsingHelper.BASE_URL + "/latest-updates/";
        }
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        return url != null && KissJavParsingHelper.normalizeUrl(url).contains("kissjav.li/");
    }
}
