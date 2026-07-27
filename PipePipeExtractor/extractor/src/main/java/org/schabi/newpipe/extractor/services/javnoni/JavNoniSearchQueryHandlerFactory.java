package org.schabi.newpipe.extractor.services.javnoni;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.List;

public final class JavNoniSearchQueryHandlerFactory extends SearchQueryHandlerFactory {
    private static final JavNoniSearchQueryHandlerFactory INSTANCE =
            new JavNoniSearchQueryHandlerFactory();

    public static JavNoniSearchQueryHandlerFactory getInstance() {
        return INSTANCE;
    }

    private JavNoniSearchQueryHandlerFactory() {
    }

    @Override
    public String getUrl(final String query,
                         final List<FilterItem> selectedContentFilter,
                         final List<FilterItem> selectedSortFilter) throws ParsingException {
        return JavNoniParsingHelper.BASE_URL + "/?s=" + JavNoniParsingHelper.encodeQuery(query);
    }
}
