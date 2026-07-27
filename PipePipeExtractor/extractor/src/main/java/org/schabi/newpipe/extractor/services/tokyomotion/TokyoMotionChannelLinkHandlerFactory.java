package org.schabi.newpipe.extractor.services.tokyomotion;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.net.URI;
import java.util.List;
import java.util.Locale;

public final class TokyoMotionChannelLinkHandlerFactory extends ListLinkHandlerFactory {
    private static final TokyoMotionChannelLinkHandlerFactory INSTANCE =
            new TokyoMotionChannelLinkHandlerFactory();

    private TokyoMotionChannelLinkHandlerFactory() {
    }

    public static TokyoMotionChannelLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    @Override
    public String getId(final String url) throws ParsingException {
        try {
            final String[] parts = URI.create(TokyoMotionParsingHelper.normalizeUrl(url)).getPath()
                    .split("/");
            for (int index = 0; index + 1 < parts.length; index++) {
                if ("user".equalsIgnoreCase(parts[index]) && !parts[index + 1].isEmpty()) {
                    return parts[index + 1];
                }
            }
        } catch (final IllegalArgumentException e) {
            throw new ParsingException("Could not parse TOKYO Motion channel URL: " + url, e);
        }
        throw new ParsingException("Could not extract TOKYO Motion channel id from URL: " + url);
    }

    @Override
    public String getUrl(final String id, final List<FilterItem> contentFilter,
                         final List<FilterItem> sortFilter) throws ParsingException {
        if (id == null || id.trim().isEmpty() || id.contains("/")) {
            throw new ParsingException("Invalid TOKYO Motion channel id");
        }
        return TokyoMotionParsingHelper.BASE_URL + "/user/" + id.trim();
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        final String normalized = TokyoMotionParsingHelper.normalizeUrl(url).toLowerCase(Locale.ROOT);
        return normalized.contains("tokyomotion.net/user/");
    }
}
