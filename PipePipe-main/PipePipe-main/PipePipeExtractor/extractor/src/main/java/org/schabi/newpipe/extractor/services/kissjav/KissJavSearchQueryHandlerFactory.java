package org.schabi.newpipe.extractor.services.kissjav;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.List;

public final class KissJavSearchQueryHandlerFactory extends SearchQueryHandlerFactory {
    private static final KissJavSearchQueryHandlerFactory INSTANCE =
            new KissJavSearchQueryHandlerFactory();

    public static KissJavSearchQueryHandlerFactory getInstance() {
        return INSTANCE;
    }

    private KissJavSearchQueryHandlerFactory() {
    }

    @Override
    public String getUrl(final String query, final List<FilterItem> selectedContentFilter,
                         final List<FilterItem> selectedSortFilter) throws ParsingException {
        return KissJavParsingHelper.BASE_URL + "/search/"
                + KissJavParsingHelper.encodeQuery(query) + "/";
    }
}
