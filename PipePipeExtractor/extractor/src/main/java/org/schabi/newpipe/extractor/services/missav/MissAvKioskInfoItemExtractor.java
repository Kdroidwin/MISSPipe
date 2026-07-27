package org.schabi.newpipe.extractor.services.missav;

import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import javax.annotation.Nullable;

final class MissAvKioskInfoItemExtractor implements StreamInfoItemExtractor {
    private final Element element;
    private final String url;

    MissAvKioskInfoItemExtractor(final Element link) {
        this(link, link.absUrl("href"));
    }

    MissAvKioskInfoItemExtractor(final Element element, final String url) {
        this.element = element;
        this.url = url;
    }

    @Override
    public String getName() {
        final Element image = element.selectFirst("img[alt]");
        if (image != null && !image.attr("alt").trim().isEmpty()) {
            return image.attr("alt").trim();
        }
        final Element title = element.selectFirst("h1, h2, h3, .my-2, .text-secondary, a[title]");
        if (title != null && title.hasAttr("title") && !title.attr("title").trim().isEmpty()) {
            return title.attr("title").trim();
        }
        final String text = MissAvParsingHelper.text(title);
        return text.isEmpty() ? MissAvParsingHelper.text(element) : text;
    }

    @Override
    public String getUrl() {
        return MissAvParsingHelper.localizeUrl(url);
    }

    @Override
    public String getThumbnailUrl() {
        final Element image = element.selectFirst("img[data-src], img[src]");
        if (image == null) {
            return "";
        }
        final String dataSrc = image.absUrl("data-src");
        return dataSrc.isEmpty() ? image.absUrl("src") : dataSrc;
    }

    @Override
    public StreamType getStreamType() {
        return StreamType.VIDEO_STREAM;
    }

    @Override
    public long getDuration() {
        return -1;
    }

    @Override
    public long getViewCount() {
        return -1;
    }

    @Override
    public String getUploaderName() {
        return "MissAV";
    }

    @Nullable
    @Override
    public String getTextualUploadDate() {
        return null;
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() {
        return null;
    }
}
