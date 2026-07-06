package org.schabi.newpipe.extractor.services.pornhub;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.net.URI;
import java.util.List;
import java.util.Locale;

public final class PornhubChannelLinkHandlerFactory extends ListLinkHandlerFactory {
    private static final PornhubChannelLinkHandlerFactory INSTANCE =
            new PornhubChannelLinkHandlerFactory();

    private PornhubChannelLinkHandlerFactory() {
    }

    public static PornhubChannelLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    @Override
    public String getId(final String url) throws ParsingException {
        final String normalized = PornhubParsingHelper.normalizeUrl(url);
        try {
            final String path = URI.create(normalized).getPath();
            final String id = normalizePath(path);
            if (id.isEmpty()) {
                throw new ParsingException("Could not extract Pornhub channel id from URL: " + url);
            }
            return id;
        } catch (final IllegalArgumentException e) {
            throw new ParsingException("Could not parse Pornhub channel URL: " + url, e);
        }
    }

    @Override
    public String getUrl(final String id,
                         final List<FilterItem> contentFilter,
                         final List<FilterItem> sortFilter) throws ParsingException {
        final String normalizedId = normalizePath(id);
        if (normalizedId.isEmpty()) {
            throw new ParsingException("Pornhub channel id is empty");
        }
        return PornhubParsingHelper.BASE_URL + "/" + normalizedId;
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        final String normalized = PornhubParsingHelper.normalizeUrl(url);
        if (normalized == null || !normalized.toLowerCase(Locale.ROOT).contains("pornhub.com/")) {
            return false;
        }
        final String lower = normalized.toLowerCase(Locale.ROOT);
        return lower.contains("/model/")
                || lower.contains("/users/")
                || lower.contains("/channels/")
                || lower.contains("/pornstar/");
    }

    private static String normalizePath(final String value) {
        if (value == null) {
            return "";
        }
        String path = value.trim();
        if (path.startsWith(PornhubParsingHelper.BASE_URL)) {
            path = path.substring(PornhubParsingHelper.BASE_URL.length());
        }
        final int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        final int fragmentIndex = path.indexOf('#');
        if (fragmentIndex >= 0) {
            path = path.substring(0, fragmentIndex);
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
