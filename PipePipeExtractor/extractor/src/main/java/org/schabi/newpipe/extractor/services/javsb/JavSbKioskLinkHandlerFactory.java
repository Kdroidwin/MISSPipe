package org.schabi.newpipe.extractor.services.javsb;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.List;

public final class JavSbKioskLinkHandlerFactory extends ListLinkHandlerFactory {
    private static final JavSbKioskLinkHandlerFactory INSTANCE =
            new JavSbKioskLinkHandlerFactory();

    public static JavSbKioskLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    private JavSbKioskLinkHandlerFactory() {
    }

    @Override
    public String getId(final String url) {
        if (url != null && url.contains("/label/rank/by/hits_week.html")) {
            return "popular";
        }
        if (url != null && url.contains("/label/rank/by/score.html")) {
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
                return JavSbParsingHelper.BASE_URL + "/ja/label/rank/by/hits_week.html";
            case "recommended":
                return JavSbParsingHelper.BASE_URL + "/ja/label/rank/by/score.html";
            case "latest":
            default:
                return JavSbParsingHelper.BASE_URL + "/ja/";
        }
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        return url != null && JavSbParsingHelper.normalizeUrl(url).contains("jav.sb/");
    }
}

