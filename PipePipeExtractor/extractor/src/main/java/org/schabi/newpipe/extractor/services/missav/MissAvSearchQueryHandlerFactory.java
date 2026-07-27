package org.schabi.newpipe.extractor.services.missav;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.List;

public final class MissAvSearchQueryHandlerFactory extends SearchQueryHandlerFactory {
    private static final MissAvSearchQueryHandlerFactory INSTANCE =
            new MissAvSearchQueryHandlerFactory();

    public static MissAvSearchQueryHandlerFactory getInstance() {
        return INSTANCE;
    }

    private MissAvSearchQueryHandlerFactory() {
    }

    @Override
    public String getUrl(final String query, final List<FilterItem> selectedContentFilter,
                         final List<FilterItem> selectedSortFilter) throws ParsingException {
        return MissAvParsingHelper.BASE_URL + "/search/"
                + MissAvParsingHelper.encodeQuery(query);
    }
}
