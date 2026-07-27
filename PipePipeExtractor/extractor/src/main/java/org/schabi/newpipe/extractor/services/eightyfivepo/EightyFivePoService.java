package org.schabi.newpipe.extractor.services.eightyfivepo;

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsExtractor;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor;
import org.schabi.newpipe.extractor.comments.CommentsExtractor;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;

import java.util.Collections;

public final class EightyFivePoService extends StreamingService {
    public EightyFivePoService(final int id) {
        super(id, "85po", Collections.singletonList(ServiceInfo.MediaCapability.VIDEO));
    }

    @Override
    public String getBaseUrl() {
        return EightyFivePoParsingHelper.BASE_URL;
    }

    @Override
    public LinkHandlerFactory getStreamLHFactory() {
        return EightyFivePoStreamLinkHandlerFactory.getInstance();
    }

    @Override
    public ListLinkHandlerFactory getChannelLHFactory() {
        return null;
    }

    @Override
    public ListLinkHandlerFactory getChannelTabLHFactory() {
        return null;
    }

    @Override
    public ListLinkHandlerFactory getPlaylistLHFactory() {
        return null;
    }

    @Override
    public SearchQueryHandlerFactory getSearchQHFactory() {
        return EightyFivePoSearchQueryHandlerFactory.getInstance();
    }

    @Override
    public ListLinkHandlerFactory getCommentsLHFactory() {
        return null;
    }

    @Override
    public SearchExtractor getSearchExtractor(final SearchQueryHandler queryHandler) {
        return new EightyFivePoSearchExtractor(this, queryHandler);
    }

    @Override
    public SuggestionExtractor getSuggestionExtractor() {
        return new EightyFivePoSuggestionExtractor(this);
    }

    @Override
    public SubscriptionExtractor getSubscriptionExtractor() {
        return null;
    }

    @Override
    public KioskList getKioskList() throws ExtractionException {
        final KioskList list = new KioskList(this);
        try {
            list.addKioskEntry((service, url, kioskId) -> new EightyFivePoKioskExtractor(
                            service,
                            EightyFivePoKioskLinkHandlerFactory.getInstance().fromId(kioskId),
                            kioskId),
                    EightyFivePoKioskLinkHandlerFactory.getInstance(),
                    "latest");
            list.addKioskEntry((service, url, kioskId) -> new EightyFivePoKioskExtractor(
                            service,
                            EightyFivePoKioskLinkHandlerFactory.getInstance().fromId(kioskId),
                            kioskId),
                    EightyFivePoKioskLinkHandlerFactory.getInstance(),
                    "popular");
            list.addKioskEntry((service, url, kioskId) -> new EightyFivePoKioskExtractor(
                            service,
                            EightyFivePoKioskLinkHandlerFactory.getInstance().fromId(kioskId),
                            kioskId),
                    EightyFivePoKioskLinkHandlerFactory.getInstance(),
                    "recommended");
            list.setDefaultKiosk("latest");
        } catch (final Exception e) {
            throw new ExtractionException("Could not initialize 85po kiosks", e);
        }
        return list;
    }

    @Override
    public ChannelExtractor getChannelExtractor(final ListLinkHandler linkHandler)
            throws ExtractionException {
        throw new ExtractionException("85po channels are not implemented");
    }

    @Override
    public ChannelTabExtractor getChannelTabExtractor(final ListLinkHandler linkHandler)
            throws ExtractionException {
        throw new ExtractionException("85po channel tabs are not implemented");
    }

    @Override
    public PlaylistExtractor getPlaylistExtractor(final ListLinkHandler linkHandler)
            throws ExtractionException {
        throw new ExtractionException("85po playlists are not implemented");
    }

    @Override
    public StreamExtractor getStreamExtractor(final LinkHandler linkHandler) {
        return new EightyFivePoStreamExtractor(this, linkHandler);
    }

    @Override
    public CommentsExtractor getCommentsExtractor(final ListLinkHandler linkHandler) {
        return null;
    }

    @Override
    public BulletCommentsExtractor getBulletCommentsExtractor(final ListLinkHandler linkHandler) {
        return null;
    }
}
