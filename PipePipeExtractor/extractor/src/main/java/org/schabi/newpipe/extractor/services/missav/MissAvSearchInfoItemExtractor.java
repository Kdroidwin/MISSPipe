package org.schabi.newpipe.extractor.services.missav;

import org.jsoup.nodes.Document;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.util.Locale;

import javax.annotation.Nullable;

final class MissAvSearchInfoItemExtractor implements StreamInfoItemExtractor {
    private final MissAvSearchResult result;

    MissAvSearchInfoItemExtractor(final MissAvSearchResult result) {
        this.result = result;
    }

    @Override
    public String getName() throws ParsingException {
        final String title = firstUsableTitle(
                "title_ja", "ja_title", "display_title_ja", "name_ja",
                "title", "display_title", "name", "english_title", "title_en", "en_title");
        final String usableTitle = title.isEmpty() ? fetchDetailTitle() : title;
        return formatTitle(usableTitle);
    }

    @Override
    public String getUrl() {
        return result.url;
    }

    @Override
    public String getThumbnailUrl() {
        String thumbnail = result.properties.getString("thumbnail", "");
        if (thumbnail.isEmpty()) {
            thumbnail = result.properties.getString("thumbnail_url", "");
        }
        if (thumbnail.isEmpty()) {
            thumbnail = result.properties.getString("cover", "");
        }
        if (thumbnail.isEmpty()) {
            thumbnail = result.properties.getString("image", "");
        }
        if (thumbnail.isEmpty()) {
            thumbnail = result.properties.getString("poster", "");
        }
        return thumbnail.isEmpty() ? MissAvParsingHelper.toThumbnailUrl(result.id) : thumbnail;
    }

    @Override
    public StreamType getStreamType() {
        return StreamType.VIDEO_STREAM;
    }

    @Override
    public long getDuration() {
        return -1;
    }

    @Override
    public long getViewCount() {
        return -1;
    }

    @Override
    public String getUploaderName() {
        String actress = result.properties.getString("actress_ja", "");
        if (actress.isEmpty()) {
            actress = result.properties.getString("ja_actress", "");
        }
        if (actress.isEmpty()) {
            actress = result.properties.getString("actress", "");
        }
        return actress.isEmpty() ? "MissAV" : actress;
    }

    @Nullable
    @Override
    public String getTextualUploadDate() {
        return result.properties.getString("release_date", null);
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return null;
    }

    private static boolean titleContainsId(final String title, final String id) {
        final String normalizedTitle = title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        final String normalizedId = id.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        return normalizedTitle.contains(normalizedId);
    }

    private String firstUsableTitle(final String... keys) {
        for (final String key : keys) {
            final String value = result.properties.getString(key, "").trim();
            if (!value.isEmpty() && !isCodeOnlyTitle(value)) {
                return value;
            }
        }
        return "";
    }

    private String fetchDetailTitle() throws ParsingException {
        try {
            final Document document = MissAvParsingHelper.fetchDocument(result.url);
            final String title = MissAvParsingHelper.text(document.selectFirst("h1.text-base, h1"));
            return isCodeOnlyTitle(title) ? "" : title;
        } catch (final IOException | ExtractionException e) {
            throw new ParsingException("Could not fetch MissAV detail title", e);
        }
    }

    private String formatTitle(final String title) {
        if (title == null || title.isEmpty()) {
            return result.id.toUpperCase(Locale.ROOT);
        }
        return titleContainsId(title, result.id)
                ? title
                : result.id.toUpperCase(Locale.ROOT) + " " + title;
    }

    private static boolean isCodeOnlyTitle(final String title) {
        final String normalized = title.toLowerCase(Locale.ROOT).trim();
        if (normalized.isEmpty()) {
            return true;
        }
        return normalized.matches("[a-z]{2,10}[-_\\s]*\\d{2,8}")
                || normalized.matches("fc2[-_\\s]*(ppv[-_\\s]*)?\\d{4,}")
                || normalized.matches("\\d{6,}")
                || isKnownCodePrefixOnly(normalized);
    }

    private static boolean isKnownCodePrefixOnly(final String normalized) {
        switch (normalized.replaceAll("[^a-z0-9]", "")) {
            case "fc2":
            case "fc2ppv":
            case "mdtm":
            case "mdx":
            case "onex":
            case "miaa":
            case "maan":
            case "oreco":
            case "ipzz":
            case "ssis":
            case "ssni":
            case "sone":
            case "juq":
            case "jufe":
            case "stars":
                return true;
            default:
                return false;
        }
    }
}
