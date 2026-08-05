package org.schabi.newpipe.extractor.services.xvideos;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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

import javax.annotation.Nonnull;

/** Public XVideos profile/channel pages; subscriptions remain local to the application. */
public final class XVideosChannelExtractor extends ChannelExtractor {
    private static final int PAGE_SIZE = 40;
    private Document document;

    public XVideosChannelExtractor(final StreamingService service, final ListLinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader) throws IOException, ExtractionException {
        document = XVideosParser.fetch(getUrl());
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        requireDocument();
        final String title = XVideosParser.value(document, "h1, meta[property=og:title]", "");
        return title.isEmpty() ? getId() : title.replaceFirst("\\s*-\\s*XVIDEOS(?:\\.COM)?$", "").trim();
    }

    @Override
    public String getAvatarUrl() throws ParsingException {
        requireDocument();
        final Element image = document.selectFirst(".profile-header img, .profile-image img, img.avatar, img[src*=avatar]");
        return image == null ? "" : XVideosParser.firstNonEmpty(image.absUrl("data-src"), image.absUrl("src"));
    }

    @Override
    public String getBannerUrl() {
        return "";
    }

    @Override
    public long getSubscriberCount() {
        return UNKNOWN_SUBSCRIBER_COUNT;
    }

    @Override
    public String getDescription() throws ParsingException {
        requireDocument();
        return XVideosParser.value(document,
                ".profile-description, .profile-about, meta[name=description]", "");
    }

    @Override
    public boolean isVerified() {
        return false;
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {
        requireDocument();
        return pageFor(XVideosParser.channelVideosUrl(getId(), 0), 0);
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page) throws IOException, ExtractionException {
        return pageFor(page.getUrl(), XVideosParser.channelPageNumber(page.getUrl()));
    }

    private InfoItemsPage<StreamInfoItem> pageFor(final String url, final int page)
            throws IOException, ExtractionException {
        final XVideosParser.ChannelVideos channelVideos = XVideosParser.channelVideos(getId(), page);
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        for (final XVideosItem item : channelVideos.items) {
            collector.commit(new XVideosItemExtractor(item));
        }
        if (channelVideos.items.isEmpty()) {
            for (final XVideosItem item : XVideosParser.cards(document, PAGE_SIZE)) {
                collector.commit(new XVideosItemExtractor(item));
            }
        }
        final Page nextPage = channelVideos.hasNextPage
                ? new Page(XVideosParser.channelVideosUrl(getId(), page + 1)) : null;
        return new ListExtractor.InfoItemsPage<>(collector, nextPage);
    }

    private void requireDocument() throws ParsingException {
        if (document == null) {
            throw new ParsingException("XVideos channel page was not fetched");
        }
    }
}
