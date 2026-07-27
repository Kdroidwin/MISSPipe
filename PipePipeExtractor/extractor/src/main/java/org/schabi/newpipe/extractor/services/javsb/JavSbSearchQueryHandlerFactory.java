package org.schabi.newpipe.extractor.services.javsb;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.List;

public final class JavSbSearchQueryHandlerFactory extends SearchQueryHandlerFactory {
    private static final JavSbSearchQueryHandlerFactory INSTANCE =
            new JavSbSearchQueryHandlerFactory();

    public static JavSbSearchQueryHandlerFactory getInstance() {
        return INSTANCE;
    }

    private JavSbSearchQueryHandlerFactory() {
    }

    @Override
    public String getUrl(final String query,
                         final List<FilterItem> selectedContentFilter,
                         final List<FilterItem> selectedSortFilter) throws ParsingException {
        return JavSbParsingHelper.BASE_URL + "/ja/vod/search.html?wd="
                + JavSbParsingHelper.encodeQuery(query);
    }
}

