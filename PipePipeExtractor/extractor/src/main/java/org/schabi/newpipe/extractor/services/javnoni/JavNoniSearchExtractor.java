package org.schabi.newpipe.extractor.services.javnoni;

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

public final class JavNoniSearchExtractor extends SearchExtractor {
    private static final int PAGE_SIZE = 40;

    public JavNoniSearchExtractor(final StreamingService service,
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
        for (final JavNoniSearchResult result
                : JavNoniParsingHelper.search(getSearchString(), PAGE_SIZE)) {
            collector.commit(new JavNoniInfoItemExtractor(result));
        }
        return new ListExtractor.InfoItemsPage<>(collector, null);
    }

    @Override
    protected InfoItemsPage<InfoItem> getPageInternal(final Page page)
            throws IOException, ExtractionException {
        return InfoItemsPage.emptyPage();
    }
}
