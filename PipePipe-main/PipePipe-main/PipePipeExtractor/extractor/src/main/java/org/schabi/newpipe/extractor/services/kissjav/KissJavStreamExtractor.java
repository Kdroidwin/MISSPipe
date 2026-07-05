package org.schabi.newpipe.extractor.services.kissjav;

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

public final class KissJavStreamExtractor extends StreamExtractor {
    private static final int MAX_RELATED_ITEMS = 24;
    private Document document;
    private String videoUrl;

    public KissJavStreamExtractor(final StreamingService service, final LinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        document = KissJavParsingHelper.fetchDocument(getUrl(), KissJavParsingHelper.BASE_URL + "/");
        if (KissJavParsingHelper.tryExtractVideoUrl(document).isEmpty()) {
            final String canonicalUrl = KissJavParsingHelper.extractCanonicalVideoUrl(
                    document, getId());
            if (!canonicalUrl.isEmpty()
                    && !canonicalUrl.equals(KissJavParsingHelper.normalizeUrl(getUrl()))) {
                final Document canonicalDocument = KissJavParsingHelper.fetchDocument(
                        canonicalUrl, KissJavParsingHelper.BASE_URL + "/");
                if (!KissJavParsingHelper.tryExtractVideoUrl(canonicalDocument).isEmpty()) {
                    document = canonicalDocument;
                }
            }
        }
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        ensureFetched();
        final String title = KissJavParsingHelper.extractTitle(document);
        return title.isEmpty() ? getId() : title;
    }

    @Nonnull
    @Override
    public String getThumbnailUrl() throws ParsingException {
        ensureFetched();
        return KissJavParsingHelper.extractThumbnail(document);
    }

    @Nonnull
    @Override
    public Description getDescription() throws ParsingException {
        ensureFetched();
        final List<String> lines = new ArrayList<>();
        final String description = KissJavParsingHelper.extractDescription(document);
        if (!description.isEmpty()) {
            lines.add(description);
        }
        final List<String> tags = KissJavParsingHelper.extractTags(document);
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
        return KissJavParsingHelper.extractDuration(document);
    }

    @Nonnull
    @Override
    public String getUploaderName() {
        return "KissJAV";
    }

    @Nonnull
    @Override
    public String getUploaderUrl() {
        return KissJavParsingHelper.BASE_URL + "/";
    }

    @Nonnull
    @Override
    public List<String> getTags() throws ParsingException {
        ensureFetched();
        return KissJavParsingHelper.extractTags(document);
    }

    @Override
    public String getTextualUploadDate() throws ParsingException {
        ensureFetched();
        return "";
    }

    @Override
    public List<AudioStream> getAudioStreams() {
        return Collections.emptyList();
    }

    @Override
    public List<VideoStream> getVideoStreams() throws IOException, ExtractionException {
        ensureFetched();
        final List<VideoStream> streams = new ArrayList<>();
        List<KissJavVideoSource> sources = KissJavParsingHelper.extractVideoSources(document);
        if (sources.isEmpty()) {
            sources = Collections.singletonList(new KissJavVideoSource("mp4",
                    getVideoUrl(), "MP4"));
        }
        for (final KissJavVideoSource source : sources) {
            streams.add(new VideoStream.Builder()
                    .setId(source.id)
                    .setContent(source.url, true)
                    .setIsVideoOnly(false)
                    .setResolution(source.resolution)
                    .setMediaFormat(MediaFormat.MPEG_4)
                    .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                    .build());
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
        final Set<String> seenUrls = new HashSet<>();
        final String currentUrl = KissJavParsingHelper.normalizeUrl(getUrl()).split("[?#]", 2)[0];
        for (final KissJavSearchResult result
                : KissJavParsingHelper.extractVideoCards(document, MAX_RELATED_ITEMS + 1)) {
            final String cleanUrl = result.url.split("[?#]", 2)[0];
            if (!cleanUrl.equals(currentUrl) && seenUrls.add(cleanUrl)) {
                collector.commit(new KissJavInfoItemExtractor(result));
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

    private String getVideoUrl() throws IOException, ExtractionException {
        if (videoUrl == null || videoUrl.isEmpty()) {
            videoUrl = KissJavParsingHelper.tryExtractVideoUrl(document);
            if (videoUrl.isEmpty()) {
                final String originalUrl = KissJavParsingHelper.normalizeUrl(getUrl());
                final String canonicalUrl = KissJavParsingHelper.BASE_URL + "/video/" + getId() + "/";
                videoUrl = tryFetchAndExtract(canonicalUrl, originalUrl);
            }
            if (videoUrl.isEmpty()) {
                final String pageUrl = KissJavParsingHelper.normalizeUrl(getUrl());
                final String embedUrl = KissJavParsingHelper.extractEmbedUrl(document, getId());
                videoUrl = tryFetchAndExtract(embedUrl, pageUrl);
                if (videoUrl.isEmpty()) {
                    videoUrl = tryCanonicalFrom(embedUrl, pageUrl);
                }
            }
            if (videoUrl.isEmpty()) {
                final String canonicalUrl = KissJavParsingHelper.BASE_URL + "/video/" + getId() + "/";
                final String embedUrl = KissJavParsingHelper.BASE_URL + "/embed/" + getId();
                videoUrl = tryFetchAndExtract(embedUrl, canonicalUrl);
                if (videoUrl.isEmpty()) {
                    videoUrl = tryCanonicalFrom(embedUrl, canonicalUrl);
                }
            }
            if (videoUrl.isEmpty()) {
                throw new ParsingException("Could not find KissJAV video URL");
            }
        }
        return videoUrl;
    }

    private String tryFetchAndExtract(final String url, final String referer)
            throws IOException, ExtractionException {
        return KissJavParsingHelper.tryExtractVideoUrl(
                KissJavParsingHelper.fetchDocument(url, referer));
    }

    private String tryCanonicalFrom(final String url, final String referer)
            throws IOException, ExtractionException {
        final Document fetchedDocument = KissJavParsingHelper.fetchDocument(url, referer);
        final String canonicalUrl = KissJavParsingHelper.extractCanonicalVideoUrl(
                fetchedDocument, getId());
        if (canonicalUrl.isEmpty() || canonicalUrl.equals(KissJavParsingHelper.normalizeUrl(url))) {
            return "";
        }
        return tryFetchAndExtract(canonicalUrl, KissJavParsingHelper.BASE_URL + "/");
    }

    private void ensureFetched() throws ParsingException {
        if (document == null) {
            throw new ParsingException("KissJAV page was not fetched");
        }
    }
}
