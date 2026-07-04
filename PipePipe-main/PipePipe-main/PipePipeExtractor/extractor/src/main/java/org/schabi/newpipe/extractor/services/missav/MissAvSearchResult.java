package org.schabi.newpipe.extractor.services.missav;

import com.grack.nanojson.JsonObject;

final class MissAvSearchResult {
    final String id;
    final String url;
    final JsonObject properties;

    MissAvSearchResult(final String id, final String url, final JsonObject properties) {
        this.id = id;
        this.url = url;
        this.properties = properties;
    }
}
