package org.schabi.newpipe.extractor.services.xvideos;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.net.URI;
import java.util.List;
import java.util.Locale;

public final class XVideosChannelLinkHandlerFactory extends ListLinkHandlerFactory {
    private static final XVideosChannelLinkHandlerFactory INSTANCE =
            new XVideosChannelLinkHandlerFactory();

    private XVideosChannelLinkHandlerFactory() {
    }

    public static XVideosChannelLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    @Override
    public String getId(final String url) throws ParsingException {
        try {
            final URI uri = URI.create(url);
            if (!isXVideosHost(uri.getHost())) {
                throw new ParsingException("Unsupported XVideos channel URL: " + url);
            }
            final String id = normalizePath(uri.getPath());
            if (id.isEmpty() || isReservedPath(id)) {
                throw new ParsingException("Could not extract XVideos channel id from URL: " + url);
            }
            return id;
        } catch (final IllegalArgumentException exception) {
            throw new ParsingException("Could not parse XVideos channel URL: " + url, exception);
        }
    }

    @Override
    public String getUrl(final String id, final List<FilterItem> contentFilter,
                         final List<FilterItem> sortFilter) throws ParsingException {
        final String path = normalizePath(id);
        if (path.isEmpty() || isReservedPath(path)) {
            throw new ParsingException("XVideos channel id is invalid");
        }
        return XVideosParser.BASE + "/" + path;
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        try {
            final URI uri = URI.create(url);
            final String path = normalizePath(uri.getPath());
            return isXVideosHost(uri.getHost()) && !path.isEmpty() && !isReservedPath(path);
        } catch (final IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isXVideosHost(final String host) {
        if (host == null) {
            return false;
        }
        final String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("xvideos.com") || normalized.endsWith(".xvideos.com");
    }

    private static String normalizePath(final String value) {
        if (value == null) {
            return "";
        }
        String path = value.trim();
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static boolean isReservedPath(final String path) {
        final String firstPart = path.split("/", 2)[0].toLowerCase(Locale.ROOT);
        return firstPart.equals("video") || firstPart.startsWith("video.")
                || firstPart.equals("search") || firstPart.equals("tags")
                || firstPart.equals("categories") || firstPart.equals("channels") && path.split("/", 2).length == 1
                || firstPart.equals("pornstars") || firstPart.equals("profiles") && path.split("/", 2).length == 1
                || firstPart.equals("embedframe") || firstPart.equals("account")
                || firstPart.equals("my-feed") || firstPart.equals("history");
    }
}
