package org.schabi.newpipe.extractor.services.tokyomotion;

import org.schabi.newpipe.extractor.stream.DeliveryMethod;

final class TokyoMotionVideoSource {
    final String id;
    final String url;
    final String resolution;
    final DeliveryMethod deliveryMethod;

    TokyoMotionVideoSource(final String id,
                       final String url,
                       final String resolution,
                       final DeliveryMethod deliveryMethod) {
        this.id = id;
        this.url = url;
        this.resolution = resolution;
        this.deliveryMethod = deliveryMethod;
    }
}

