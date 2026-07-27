package org.schabi.newpipe.extractor.services.javsb;

import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import javax.annotation.Nullable;

final class JavSbInfoItemExtractor implements StreamInfoItemExtractor {
    private final JavSbSearchResult result;

    JavSbInfoItemExtractor(final JavSbSearchResult result) {
        this.result = result;
    }

    @Override
    public String getName() {
        return result.title == null || result.title.isEmpty()
                ? "JAVSB " + result.id
                : result.title;
    }

    @Override
    public String getUrl() {
        return result.url;
    }

    @Override
    public String getThumbnailUrl() {
        return result.thumbnailUrl;
    }

    @Override
    public StreamType getStreamType() {
        return StreamType.VIDEO_STREAM;
    }

    @Override
    public long getDuration() {
        return result.duration;
    }

    @Override
    public long getViewCount() {
        return -1;
    }

    @Override
    public String getUploaderName() {
        return "JAVSB";
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

