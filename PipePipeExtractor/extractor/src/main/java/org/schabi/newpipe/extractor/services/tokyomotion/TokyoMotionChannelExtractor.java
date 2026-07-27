package org.schabi.newpipe.extractor.services.tokyomotion;

import org.jsoup.nodes.Document;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nonnull;

public final class TokyoMotionChannelExtractor extends ChannelExtractor {
    private static final int PAGE_SIZE = 24;
    private Document document;

    public TokyoMotionChannelExtractor(final StreamingService service,
                                       final ListLinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        document = TokyoMotionParsingHelper.fetchDocument(getUrl(), TokyoMotionParsingHelper.BASE_URL + "/");
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        ensureFetched();
        return TokyoMotionParsingHelper.extractChannelName(document, getId());
    }

    @Override
    public String getAvatarUrl() throws ParsingException {
        ensureFetched();
        return TokyoMotionParsingHelper.extractChannelAvatarUrl(document);
    }

    @Override
    public long getSubscriberCount() {
        return UNKNOWN_SUBSCRIBER_COUNT;
    }

    @Override
    public String getDescription() throws ParsingException {
        ensureFetched();
        return TokyoMotionParsingHelper.extractChannelDescription(document);
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage()
            throws IOException, ExtractionException {
        ensureFetched();
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        final List<TokyoMotionSearchResult> videos =
                TokyoMotionParsingHelper.extractVideoCards(document, PAGE_SIZE);
        for (final TokyoMotionSearchResult video : videos) {
            collector.commit(new TokyoMotionInfoItemExtractor(video));
        }
        return new ListExtractor.InfoItemsPage<>(collector, null);
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page) {
        return InfoItemsPage.emptyPage();
    }

    private void ensureFetched() throws ParsingException {
        if (document == null) {
            throw new ParsingException("TOKYO Motion channel page was not fetched");
        }
    }
}
