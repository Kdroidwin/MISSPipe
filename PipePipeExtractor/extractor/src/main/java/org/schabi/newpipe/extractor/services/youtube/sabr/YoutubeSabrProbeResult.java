package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import java.util.List;

public final class YoutubeSabrProbeResult {
    @Nonnull
    private final YoutubeSabrInfo info;
    @Nonnull
    private final SabrDecodedResponse decodedResponse;
    @Nonnull
    private final List<SabrMediaSegment> segments;
    private final int responseCode;
    @Nonnull
    private final String contentType;

    YoutubeSabrProbeResult(@Nonnull final YoutubeSabrInfo info,
                           @Nonnull final SabrDecodedResponse decodedResponse,
                           @Nonnull final List<SabrMediaSegment> segments,
                           final int responseCode,
                           @Nonnull final String contentType) {
        this.info = info;
        this.decodedResponse = decodedResponse;
        this.segments = segments;
        this.responseCode = responseCode;
        this.contentType = contentType;
    }

    @Nonnull
    public YoutubeSabrInfo getInfo() {
        return info;
    }

    @Nonnull
    public SabrDecodedResponse getDecodedResponse() {
        return decodedResponse;
    }

    /** Segments assembled while streaming the response (the buffered path collected these later). */
    @Nonnull
    public List<SabrMediaSegment> getSegments() {
        return segments;
    }

    public int getResponseCode() {
        return responseCode;
    }

    @Nonnull
    public String getContentType() {
        return contentType;
    }
}
