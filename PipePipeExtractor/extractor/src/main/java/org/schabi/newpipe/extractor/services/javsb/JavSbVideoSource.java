package org.schabi.newpipe.extractor.services.javsb;

import org.schabi.newpipe.extractor.stream.DeliveryMethod;

final class JavSbVideoSource {
    final String id;
    final String url;
    final String resolution;
    final DeliveryMethod deliveryMethod;

    JavSbVideoSource(final String id,
                       final String url,
                       final String resolution,
                       final DeliveryMethod deliveryMethod) {
        this.id = id;
        this.url = url;
        this.resolution = resolution;
        this.deliveryMethod = deliveryMethod;
    }
}

