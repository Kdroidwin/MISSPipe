package org.schabi.newpipe.extractor.services.pornhub;

import org.schabi.newpipe.extractor.stream.DeliveryMethod;

final class PornhubVideoSource {
    final String id;
    final String url;
    final String resolution;
    final DeliveryMethod deliveryMethod;

    PornhubVideoSource(final String id,
                       final String url,
                       final String resolution,
                       final DeliveryMethod deliveryMethod) {
        this.id = id;
        this.url = url;
        this.resolution = resolution;
        this.deliveryMethod = deliveryMethod;
    }
}
