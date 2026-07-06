package org.schabi.newpipe.extractor.services.eightyfivepo;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.List;

public final class EightyFivePoKioskLinkHandlerFactory extends ListLinkHandlerFactory {
    private static final EightyFivePoKioskLinkHandlerFactory INSTANCE =
            new EightyFivePoKioskLinkHandlerFactory();

    public static EightyFivePoKioskLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    private EightyFivePoKioskLinkHandlerFactory() {
    }

    @Override
    public String getUrl(final String id,
                         final List<FilterItem> contentFilters,
                         final List<FilterItem> sortFilter) throws ParsingException {
        switch (id) {
            case "popular":
                return EightyFivePoParsingHelper.BASE_URL + "/ja/most-popular/";
            case "recommended":
                return EightyFivePoParsingHelper.BASE_URL + "/ja/top-rated/";
            case "latest":
            default:
                return EightyFivePoParsingHelper.BASE_URL + "/ja/latest-updates/";
        }
    }

    @Override
    public String getId(final String url) {
        final String normalized = EightyFivePoParsingHelper.normalizeUrl(url);
        if (normalized != null && normalized.contains("/most-popular/")) {
            return "popular";
        }
        if (normalized != null && normalized.contains("/top-rated/")) {
            return "recommended";
        }
        return "latest";
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        final String normalized = EightyFivePoParsingHelper.normalizeUrl(url);
        return normalized != null && normalized.contains("85po.com/");
    }
}
