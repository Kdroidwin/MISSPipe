package org.schabi.newpipe.extractor.services.pornhub;

import java.util.Collections;
import java.util.List;

final class PornhubSearchResult {
    final String id;
    final String url;
    final String title;
    final String thumbnail;
    final long duration;
    final String uploaderName;
    final String uploaderUrl;
    final List<String> tags;
    final List<String> categories;
    final String searchableText;
    final int relevanceScore;

    PornhubSearchResult(final String id,
                        final String url,
                        final String title,
                        final String thumbnail,
                        final long duration,
                        final String uploaderName,
                        final String uploaderUrl) {
        this(id, url, title, thumbnail, duration, uploaderName, uploaderUrl,
                Collections.emptyList(), Collections.emptyList(), "", 0);
    }

    PornhubSearchResult(final String id,
                        final String url,
                        final String title,
                        final String thumbnail,
                        final long duration,
                        final String uploaderName,
                        final String uploaderUrl,
                        final List<String> tags,
                        final List<String> categories,
                        final String searchableText,
                        final int relevanceScore) {
        this.id = id;
        this.url = url;
        this.title = title;
        this.thumbnail = thumbnail;
        this.duration = duration;
        this.uploaderName = uploaderName;
        this.uploaderUrl = uploaderUrl;
        this.tags = tags == null ? Collections.emptyList() : Collections.unmodifiableList(tags);
        this.categories = categories == null ? Collections.emptyList() : Collections.unmodifiableList(categories);
        this.searchableText = searchableText == null ? "" : searchableText;
        this.relevanceScore = relevanceScore;
    }
}
