package org.schabi.newpipe.extractor.services.javnoni;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.List;

public final class JavNoniKioskLinkHandlerFactory extends ListLinkHandlerFactory {
    private static final JavNoniKioskLinkHandlerFactory INSTANCE =
            new JavNoniKioskLinkHandlerFactory();

    public static JavNoniKioskLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    private JavNoniKioskLinkHandlerFactory() {
    }

    @Override
    public String getId(final String url) {
        if (url != null && url.contains("/archives/category/uncensored/")) {
            return "popular";
        }
        if (url != null && url.contains("/archives/tag/japan/")) {
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
                return JavNoniParsingHelper.BASE_URL + "/archives/category/uncensored/";
            case "recommended":
                return JavNoniParsingHelper.BASE_URL + "/archives/tag/japan/";
            case "latest":
            default:
                return JavNoniParsingHelper.BASE_URL + "/";
        }
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        return url != null && JavNoniParsingHelper.normalizeUrl(url).contains("jav-noni.vip/");
    }
}
