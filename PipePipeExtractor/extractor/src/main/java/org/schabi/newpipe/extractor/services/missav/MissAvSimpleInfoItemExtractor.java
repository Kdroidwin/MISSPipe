package org.schabi.newpipe.extractor.services.missav;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import javax.annotation.Nullable;

final class MissAvSimpleInfoItemExtractor implements StreamInfoItemExtractor {
    private final String url;
    private final String name;
    private final String thumbnailUrl;

    MissAvSimpleInfoItemExtractor(final String url, final String name) throws ParsingException {
        this.url = MissAvParsingHelper.localizeUrl(url);
        final String id = MissAvParsingHelper.extractId(this.url);
        this.name = name == null || name.trim().isEmpty() ? id.toUpperCase() : name.trim();
        this.thumbnailUrl = MissAvParsingHelper.toThumbnailUrl(id);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public String getThumbnailUrl() {
        return thumbnailUrl;
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
    public DateWrapper getUploadDate() throws ParsingException {
        return null;
    }
}
