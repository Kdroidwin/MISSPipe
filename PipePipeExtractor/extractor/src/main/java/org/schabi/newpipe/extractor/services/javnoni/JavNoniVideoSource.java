package org.schabi.newpipe.extractor.services.javnoni;

import org.schabi.newpipe.extractor.stream.DeliveryMethod;

final class JavNoniVideoSource {
    final String id;
    final String url;
    final String resolution;
    final DeliveryMethod deliveryMethod;

    JavNoniVideoSource(final String id,
                       final String url,
                       final String resolution,
                       final DeliveryMethod deliveryMethod) {
        this.id = id;
        this.url = url;
        this.resolution = resolution;
        this.deliveryMethod = deliveryMethod;
    }
}
