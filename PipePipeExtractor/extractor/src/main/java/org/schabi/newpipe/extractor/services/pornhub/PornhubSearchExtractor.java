package org.schabi.newpipe.extractor.services.pornhub;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.MultiInfoItemsCollector;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.search.SearchExtractor;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nonnull;

public final class PornhubSearchExtractor extends SearchExtractor {
    public PornhubSearchExtractor(final StreamingService service,
                                  final SearchQueryHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader) {
    }

    @Override
    protected InfoItemsPage<InfoItem> getInitialPageInternal()
            throws IOException, ExtractionException {
        final PornhubSearchEngine.SearchPage searchPage =
                PornhubSearchEngine.search(getSearchString(), 1);
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        for (final PornhubSearchResult result : searchPage.results) {
            collector.commit(new PornhubInfoItemExtractor(result));
        }
        return new ListExtractor.InfoItemsPage<>(collector, searchPage.nextPage);
    }

    @Override
    protected InfoItemsPage<InfoItem> getPageInternal(final Page page)
            throws IOException, ExtractionException {
        if (page == null || page.getId() == null) {
            return InfoItemsPage.emptyPage();
        }
        final int pageNumber;
        try {
            pageNumber = Integer.parseInt(page.getId());
        } catch (final NumberFormatException e) {
            return InfoItemsPage.emptyPage();
        }
        final PornhubSearchEngine.SearchPage searchPage =
                PornhubSearchEngine.search(getSearchString(), pageNumber);
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        for (final PornhubSearchResult result : searchPage.results) {
            collector.commit(new PornhubInfoItemExtractor(result));
        }
        return new ListExtractor.InfoItemsPage<>(collector, searchPage.nextPage);
    }
}
