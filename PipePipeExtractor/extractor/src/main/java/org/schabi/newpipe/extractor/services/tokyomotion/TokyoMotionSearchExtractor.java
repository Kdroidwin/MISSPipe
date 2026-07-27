package org.schabi.newpipe.extractor.services.tokyomotion;

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

import javax.annotation.Nonnull;

public final class TokyoMotionSearchExtractor extends SearchExtractor {
    private static final int PAGE_SIZE = 40;

    public TokyoMotionSearchExtractor(final StreamingService service,
                                  final SearchQueryHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader) {
    }

    @Override
    protected InfoItemsPage<InfoItem> getInitialPageInternal()
            throws IOException, ExtractionException {
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        for (final TokyoMotionSearchResult result
                : TokyoMotionParsingHelper.search(getSearchString(), PAGE_SIZE)) {
            collector.commit(new TokyoMotionInfoItemExtractor(result));
        }
        return new ListExtractor.InfoItemsPage<>(collector, null);
    }

    @Override
    protected InfoItemsPage<InfoItem> getPageInternal(final Page page)
            throws IOException, ExtractionException {
        return InfoItemsPage.emptyPage();
    }
}

