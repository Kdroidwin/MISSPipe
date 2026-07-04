package org.schabi.newpipe.extractor.services.missav;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import javax.annotation.Nullable;

final class MissAvSearchInfoItemExtractor implements StreamInfoItemExtractor {
    private final MissAvSearchResult result;

    MissAvSearchInfoItemExtractor(final MissAvSearchResult result) {
        this.result = result;
    }

    @Override
    public String getName() {
        String title = result.properties.getString("title_ja", "");
        if (title.isEmpty()) {
            title = result.properties.getString("ja_title", "");
        }
        if (title.isEmpty()) {
            title = result.properties.getString("title", "");
        }
        return title.isEmpty() ? result.id : title;
    }

    @Override
    public String getUrl() {
        return result.url;
    }

    @Override
    public String getThumbnailUrl() {
        String thumbnail = result.properties.getString("thumbnail", "");
        if (thumbnail.isEmpty()) {
            thumbnail = result.properties.getString("thumbnail_url", "");
        }
        if (thumbnail.isEmpty()) {
            thumbnail = result.properties.getString("cover", "");
        }
        if (thumbnail.isEmpty()) {
            thumbnail = result.properties.getString("image", "");
        }
        if (thumbnail.isEmpty()) {
            thumbnail = result.properties.getString("poster", "");
        }
        return thumbnail.isEmpty() ? MissAvParsingHelper.toThumbnailUrl(result.id) : thumbnail;
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
        String actress = result.properties.getString("actress_ja", "");
        if (actress.isEmpty()) {
            actress = result.properties.getString("ja_actress", "");
        }
        if (actress.isEmpty()) {
            actress = result.properties.getString("actress", "");
        }
        return actress.isEmpty() ? "MissAV" : actress;
    }

    @Nullable
    @Override
    public String getTextualUploadDate() {
        return result.properties.getString("release_date", null);
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return null;
    }
}
