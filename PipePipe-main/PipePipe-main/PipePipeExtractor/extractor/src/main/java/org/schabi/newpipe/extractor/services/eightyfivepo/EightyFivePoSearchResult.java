package org.schabi.newpipe.extractor.services.eightyfivepo;

final class EightyFivePoSearchResult {
    final String id;
    final String url;
    final String title;
    final String thumbnail;
    final long duration;

    EightyFivePoSearchResult(final String id,
                             final String url,
                             final String title,
                             final String thumbnail,
                             final long duration) {
        this.id = id;
        this.url = url;
        this.title = title;
        this.thumbnail = thumbnail;
        this.duration = duration;
    }
}
