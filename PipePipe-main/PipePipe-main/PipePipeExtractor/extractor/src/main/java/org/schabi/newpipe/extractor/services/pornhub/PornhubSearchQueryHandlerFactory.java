package org.schabi.newpipe.extractor.services.pornhub;

import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.List;

public final class PornhubSearchQueryHandlerFactory extends SearchQueryHandlerFactory {
    private static final PornhubSearchQueryHandlerFactory INSTANCE =
            new PornhubSearchQueryHandlerFactory();

    public static PornhubSearchQueryHandlerFactory getInstance() {
        return INSTANCE;
    }

    private PornhubSearchQueryHandlerFactory() {
    }

    @Override
    public String getUrl(final String query,
                         final List<FilterItem> contentFilters,
                         final List<FilterItem> sortFilter) {
        return PornhubParsingHelper.searchUrl(query);
    }
}
