package org.schabi.newpipe.extractor.services.eightyfivepo;

import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.List;

public final class EightyFivePoSearchQueryHandlerFactory extends SearchQueryHandlerFactory {
    private static final EightyFivePoSearchQueryHandlerFactory INSTANCE =
            new EightyFivePoSearchQueryHandlerFactory();

    public static EightyFivePoSearchQueryHandlerFactory getInstance() {
        return INSTANCE;
    }

    private EightyFivePoSearchQueryHandlerFactory() {
    }

    @Override
    public String getUrl(final String query,
                         final List<FilterItem> contentFilters,
                         final List<FilterItem> sortFilter) {
        return EightyFivePoParsingHelper.BASE_URL + "/ja/search/"
                + EightyFivePoParsingHelper.encodeQuery(query) + "/";
    }
}
