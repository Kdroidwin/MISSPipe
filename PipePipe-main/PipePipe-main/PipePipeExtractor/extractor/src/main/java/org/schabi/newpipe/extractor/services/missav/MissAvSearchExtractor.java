package org.schabi.newpipe.extractor.services.missav;

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

public final class MissAvSearchExtractor extends SearchExtractor {
    private static final int PAGE_SIZE = 50;

    public MissAvSearchExtractor(final StreamingService service,
                                 final SearchQueryHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader) {
        // MissAV search is fetched directly in getInitialPageInternal().
    }

    @Override
    protected InfoItemsPage<InfoItem> getInitialPageInternal()
            throws IOException, ExtractionException {
        final List<MissAvSearchResult> results =
                MissAvParsingHelper.search(getSearchString(), PAGE_SIZE);
        final MultiInfoItemsCollector collector =
                new MultiInfoItemsCollector(getServiceId());
        for (final MissAvSearchResult result : results) {
            collector.commit(new MissAvSearchInfoItemExtractor(result));
        }
        return new ListExtractor.InfoItemsPage<>(collector, null);
    }

    @Override
    protected InfoItemsPage<InfoItem> getPageInternal(final Page page)
            throws IOException, ExtractionException {
        return InfoItemsPage.emptyPage();
    }
}
