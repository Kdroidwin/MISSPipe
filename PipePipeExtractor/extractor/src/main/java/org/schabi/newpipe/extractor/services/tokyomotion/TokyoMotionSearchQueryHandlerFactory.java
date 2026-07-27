package org.schabi.newpipe.extractor.services.tokyomotion;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.List;

public final class TokyoMotionSearchQueryHandlerFactory extends SearchQueryHandlerFactory {
    private static final TokyoMotionSearchQueryHandlerFactory INSTANCE =
            new TokyoMotionSearchQueryHandlerFactory();

    public static TokyoMotionSearchQueryHandlerFactory getInstance() {
        return INSTANCE;
    }

    private TokyoMotionSearchQueryHandlerFactory() {
    }

    @Override
    public String getUrl(final String query,
                         final List<FilterItem> selectedContentFilter,
                         final List<FilterItem> selectedSortFilter) throws ParsingException {
        return TokyoMotionParsingHelper.BASE_URL + "/search?search_query="
                + TokyoMotionParsingHelper.encodeQuery(query) + "&search_type=videos";
    }
}

