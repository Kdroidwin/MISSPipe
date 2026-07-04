package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Streaming counterpart of {@link SabrResponseDecoder#decode(byte[])}: parse the UMP envelope from a
 * stream one part at a time, assembling MEDIA segments on the fly (via
 * {@link SabrMediaSegmentCollector.Incremental}) so the big MEDIA payloads are never all held at
 * once. Only the small control parts (everything except the MEDIA payloads) are kept and decoded
 * into a {@link SabrDecodedResponse}. This is what fixes the 4K OOM: peak transient drops from the
 * whole response body (50-150MB) to a single in-flight segment.
 */
public final class SabrStreamingResponseReader {
    private SabrStreamingResponseReader() {
    }

    @Nonnull
    public static Result read(@Nonnull final InputStream in)
            throws SabrProtocolException, IOException {
        final List<UmpPart> controlParts = new ArrayList<>();
        final List<SabrMediaSegment> segments = new ArrayList<>();
        // headerId -> total media bytes seen, so the decoded response passes the same integrity check
        // (getIntegrityIssues -> "missing-media") as the buffered path WITHOUT retaining the bytes.
        final Map<Integer, Long> mediaBytesByHeaderId = new HashMap<>();
        final SabrMediaSegmentCollector.Incremental collector =
                new SabrMediaSegmentCollector.Incremental();
        UmpReader.readStreaming(in, (type, payload) -> {
            switch (type) {
                case SabrResponseDecoder.MEDIA_HEADER:
                    collector.onMediaHeader(payload);
                    // small (just the header) -> keep so the decoder records it (observeHeader).
                    controlParts.add(new UmpPart(type, payload.length, payload));
                    break;
                case SabrResponseDecoder.MEDIA:
                    // big payload -> assemble into the open segment, do NOT retain.
                    collector.onMedia(payload);
                    if (payload.length > 0) {
                        final int headerId = payload[0] & 0xff;
                        mediaBytesByHeaderId.put(headerId,
                                mediaBytesByHeaderId.getOrDefault(headerId, 0L)
                                        + (payload.length - 1L));
                    }
                    break;
                case SabrResponseDecoder.MEDIA_END: {
                    final SabrMediaSegment segment = collector.onMediaEnd(payload);
                    if (segment != null) {
                        segments.add(segment);
                    }
                    controlParts.add(new UmpPart(type, payload.length, payload));
                    break;
                }
                default:
                    controlParts.add(new UmpPart(type, payload.length, payload));
                    break;
            }
        });
        final SabrDecodedResponse decoded = SabrResponseDecoder.decodeParts(controlParts);
        for (final Map.Entry<Integer, Long> entry : mediaBytesByHeaderId.entrySet()) {
            decoded.addMediaBytes(entry.getKey(), entry.getValue());
        }
        return new Result(decoded, segments);
    }

    /** The decoded control response plus the segments assembled while streaming. */
    public static final class Result {
        @Nonnull
        private final SabrDecodedResponse decodedResponse;
        @Nonnull
        private final List<SabrMediaSegment> segments;

        Result(@Nonnull final SabrDecodedResponse decodedResponse,
               @Nonnull final List<SabrMediaSegment> segments) {
            this.decodedResponse = decodedResponse;
            this.segments = segments;
        }

        @Nonnull
        public SabrDecodedResponse getDecodedResponse() {
            return decodedResponse;
        }

        @Nonnull
        public List<SabrMediaSegment> getSegments() {
            return segments;
        }
    }
}
