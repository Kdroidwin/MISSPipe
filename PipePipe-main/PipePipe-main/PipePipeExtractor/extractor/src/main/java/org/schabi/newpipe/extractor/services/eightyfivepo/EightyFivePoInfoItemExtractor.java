package org.schabi.newpipe.extractor.services.eightyfivepo;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

final class EightyFivePoInfoItemExtractor implements StreamInfoItemExtractor {
    private final EightyFivePoSearchResult result;

    EightyFivePoInfoItemExtractor(final EightyFivePoSearchResult result) {
        this.result = result;
    }

    @Override
    public String getName() {
        return result.title == null || result.title.isEmpty() ? "85po " + result.id : result.title;
    }

    @Override
    public String getUrl() {
        return result.url;
    }

    @Override
    public String getThumbnailUrl() {
        return result.thumbnail == null ? "" : result.thumbnail;
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
        return "85po";
    }

    @Override
    public String getUploaderUrl() {
        return EightyFivePoParsingHelper.BASE_URL + "/ja/";
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return false;
    }

    @Override
    public String getTextualUploadDate() {
        return null;
    }

    @Override
    public DateWrapper getUploadDate() {
        return null;
    }
}
