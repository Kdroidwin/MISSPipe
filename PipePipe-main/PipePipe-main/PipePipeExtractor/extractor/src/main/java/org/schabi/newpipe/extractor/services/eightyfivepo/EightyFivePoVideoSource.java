package org.schabi.newpipe.extractor.services.eightyfivepo;

public final class EightyFivePoVideoSource {
    public final String id;
    public final String url;
    public final String resolution;

    EightyFivePoVideoSource(final String id, final String url, final String resolution) {
        this.id = id;
        this.url = url;
        this.resolution = resolution;
    }
}
