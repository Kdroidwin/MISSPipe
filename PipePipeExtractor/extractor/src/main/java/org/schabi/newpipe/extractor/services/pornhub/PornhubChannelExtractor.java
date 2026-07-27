package org.schabi.newpipe.extractor.services.pornhub;

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

public final class PornhubChannelExtractor extends ChannelExtractor {
    private static final int PAGE_SIZE = 40;
    private Document document;

    public PornhubChannelExtractor(final StreamingService service,
                                   final ListLinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        document = PornhubParsingHelper.fetchDocument(getUrl(), PornhubParsingHelper.BASE_URL + "/");
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        ensureFetched();
        return PornhubParsingHelper.extractChannelName(document, getId());
    }

    @Override
    public String getAvatarUrl() throws ParsingException {
        ensureFetched();
        return PornhubParsingHelper.extractChannelAvatarUrl(document);
    }

    @Override
    public String getBannerUrl() throws ParsingException {
        ensureFetched();
        return PornhubParsingHelper.extractChannelBannerUrl(document);
    }

    @Override
    public long getSubscriberCount() {
        return UNKNOWN_SUBSCRIBER_COUNT;
    }

    @Override
    public String getDescription() throws ParsingException {
        ensureFetched();
        return PornhubParsingHelper.extractChannelDescription(document);
    }

    @Override
    public boolean isVerified() {
        return false;
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage()
            throws IOException, ExtractionException {
        ensureFetched();
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        final List<PornhubSearchResult> videos =
                PornhubParsingHelper.extractVideoCards(document, PAGE_SIZE);
        for (final PornhubSearchResult video : videos) {
            collector.commit(new PornhubInfoItemExtractor(video));
        }
        return new ListExtractor.InfoItemsPage<>(collector, null);
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page) {
        return InfoItemsPage.emptyPage();
    }

    private void ensureFetched() throws ParsingException {
        if (document == null) {
            throw new ParsingException("Pornhub channel page was not fetched");
        }
    }
}
