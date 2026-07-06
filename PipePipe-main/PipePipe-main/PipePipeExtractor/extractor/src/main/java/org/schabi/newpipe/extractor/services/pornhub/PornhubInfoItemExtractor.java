package org.schabi.newpipe.extractor.services.pornhub;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

final class PornhubInfoItemExtractor implements StreamInfoItemExtractor {
    private final PornhubSearchResult result;

    PornhubInfoItemExtractor(final PornhubSearchResult result) {
        this.result = result;
    }

    @Override
    public String getName() {
        return result.title == null || result.title.isEmpty() ? "Pornhub " + result.id : result.title;
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
        return result.uploaderName == null || result.uploaderName.isEmpty()
                ? "Pornhub" : result.uploaderName;
    }

    @Override
    public String getUploaderUrl() {
        return result.uploaderUrl == null || result.uploaderUrl.isEmpty()
                ? PornhubParsingHelper.BASE_URL + "/" : result.uploaderUrl;
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
