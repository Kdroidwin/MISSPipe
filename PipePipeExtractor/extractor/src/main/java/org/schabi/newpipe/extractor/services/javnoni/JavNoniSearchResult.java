package org.schabi.newpipe.extractor.services.javnoni;

final class JavNoniSearchResult {
    final String id;
    final String url;
    final String title;
    final String thumbnailUrl;
    final long duration;

    JavNoniSearchResult(final String id,
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
