package org.schabi.newpipe.extractor.services.javsb;

final class JavSbSearchResult {
    final String id;
    final String url;
    final String title;
    final String thumbnailUrl;
    final long duration;

    JavSbSearchResult(final String id,
                        final String url,
                        final String title,
                        final String thumbnailUrl,
                        final long duration) {
        this.id = id;
        this.url = url;
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
        this.duration = duration;
    }
}

