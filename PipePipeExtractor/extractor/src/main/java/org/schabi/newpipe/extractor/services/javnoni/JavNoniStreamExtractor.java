package org.schabi.newpipe.extractor.services.javnoni;

import org.jsoup.nodes.Document;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.InfoItemExtractor;
import org.schabi.newpipe.extractor.InfoItemsCollector;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.MetaInfo;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

public final class JavNoniStreamExtractor extends StreamExtractor {
    private static final int MAX_RELATED_ITEMS = 24;
    private Document document;

    public JavNoniStreamExtractor(final StreamingService service, final LinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        document = JavNoniParsingHelper.fetchDocument(getUrl(), JavNoniParsingHelper.BASE_URL + "/");
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        ensureFetched();
        final String title = JavNoniParsingHelper.extractTitle(document);
        return title.isEmpty() ? JavNoniParsingHelper.decodePathForDisplay(getId()) : title;
    }

    @Nonnull
    @Override
    public String getThumbnailUrl() throws ParsingException {
        ensureFetched();
        return JavNoniParsingHelper.extractThumbnail(document);
    }

    @Nonnull
    @Override
    public Description getDescription() throws ParsingException {
        ensureFetched();
        final List<String> lines = new ArrayList<>();
        final String description = JavNoniParsingHelper.extractDescription(document);
        if (!description.isEmpty()) {
            lines.add(description);
        }
        final List<String> tags = JavNoniParsingHelper.extractTags(document);
        if (!tags.isEmpty()) {
            lines.add("Tags: " + String.join(", ", tags));
        }
        return lines.isEmpty()
                ? Description.EMPTY_DESCRIPTION
                : new Description(String.join("\n", lines), Description.PLAIN_TEXT);
    }

    @Override
    public long getLength() throws ParsingException {
        ensureFetched();
        return JavNoniParsingHelper.extractDuration(document);
    }

    @Nonnull
    @Override
    public String getUploaderName() {
        return "JAV-NONI";
    }

    @Nonnull
    @Override
    public String getUploaderUrl() {
        return JavNoniParsingHelper.BASE_URL + "/";
    }

    @Nonnull
    @Override
    public List<String> getTags() throws ParsingException {
        ensureFetched();
        return JavNoniParsingHelper.extractTags(document);
    }

    @Override
    public String getTextualUploadDate() {
        return "";
    }

    @Override
    public List<AudioStream> getAudioStreams() {
        return Collections.emptyList();
    }

    @Override
    public List<VideoStream> getVideoStreams() throws IOException, ExtractionException {
        ensureFetched();
        final List<JavNoniVideoSource> sources =
                JavNoniParsingHelper.findVideoSources(document, getUrl());
        if (sources.isEmpty()) {
            throw new ParsingException("Could not find JAV-NONI video URL");
        }
        final List<VideoStream> streams = new ArrayList<>();
        for (final JavNoniVideoSource source : sources) {
            final VideoStream.Builder builder = new VideoStream.Builder()
                    .setId(source.id)
                    .setContent(source.url, true)
                    .setIsVideoOnly(false)
                    .setResolution(source.resolution)
                    .setDeliveryMethod(source.deliveryMethod)
                    .setMediaFormat(MediaFormat.MPEG_4);
            if (source.deliveryMethod == DeliveryMethod.HLS) {
                builder.setManifestUrl(source.url);
            }
            streams.add(builder.build());
        }
        return streams;
    }

    @Override
    public List<VideoStream> getVideoOnlyStreams() {
        return Collections.emptyList();
    }

    @Override
    public StreamType getStreamType() {
        return StreamType.VIDEO_STREAM;
    }

    @Override
    public InfoItemsCollector<? extends InfoItem, ? extends InfoItemExtractor> getRelatedItems()
            throws IOException, ExtractionException {
        ensureFetched();
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        final Set<String> seen = new HashSet<>();
        final String currentUrl = JavNoniParsingHelper.normalizeUrl(getUrl()).split("[?#]", 2)[0];
        for (final JavNoniSearchResult result
                : JavNoniParsingHelper.extractRelatedVideoCards(document, MAX_RELATED_ITEMS + 1)) {
            final String cleanUrl = result.url.split("[?#]", 2)[0];
            if (!cleanUrl.equals(currentUrl) && seen.add(cleanUrl)) {
                collector.commit(new JavNoniInfoItemExtractor(result));
            }
            if (collector.getItems().size() >= MAX_RELATED_ITEMS) {
                break;
            }
        }
        return collector;
    }

    @Nonnull
    @Override
    public List<MetaInfo> getMetaInfo() {
        return Collections.emptyList();
    }

    private void ensureFetched() throws ParsingException {
        if (document == null) {
            throw new ParsingException("JAV-NONI page was not fetched");
        }
    }
}
