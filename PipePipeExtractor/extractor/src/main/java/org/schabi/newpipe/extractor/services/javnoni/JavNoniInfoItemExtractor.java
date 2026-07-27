package org.schabi.newpipe.extractor.services.javnoni;

import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import javax.annotation.Nullable;

final class JavNoniInfoItemExtractor implements StreamInfoItemExtractor {
    private final JavNoniSearchResult result;

    JavNoniInfoItemExtractor(final JavNoniSearchResult result) {
        this.result = result;
    }

    @Override
    public String getName() {
        return result.title == null || result.title.isEmpty()
                ? "JAV-NONI " + result.id
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
        return "JAV-NONI";
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
