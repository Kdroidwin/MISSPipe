package org.schabi.newpipe.extractor.services.pornhub;

import org.jsoup.nodes.Document;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PornhubSearchEngine {
    static final int PAGE_SIZE = 40;
    private static final int MAX_PAGES = 5;
    private static final int RAW_ITEMS_PER_PAGE = 96;
    private static final int EXACT_TITLE_SCORE = 10_000;
    private static final int TITLE_PHRASE_SCORE = 5_000;
    private static final int TITLE_TOKEN_SCORE = 900;
    private static final int TAG_TOKEN_SCORE = 350;
    private static final int CATEGORY_TOKEN_SCORE = 250;
    private static final int UPLOADER_TOKEN_SCORE = 120;
    private static final int SEARCHABLE_TOKEN_SCORE = 80;

    private PornhubSearchEngine() {
    }

    static SearchPage search(final String query, final int page)
            throws IOException, ExtractionException {
        final String normalizedQuery = PornhubTextNormalizer.normalize(query);
        final List<String> queryTokens = PornhubTextNormalizer.tokens(query);
        if (normalizedQuery.isEmpty() || queryTokens.isEmpty()) {
            return new SearchPage(new ArrayList<>(), null);
        }

        final String url = PornhubParsingHelper.searchUrl(query, page);
        final Document document = PornhubParsingHelper.fetchDocument(url);
        final List<PornhubSearchResult> rawResults =
                PornhubParsingHelper.extractVideoCards(document, RAW_ITEMS_PER_PAGE);

        final Map<String, PornhubSearchResult> ranked = new LinkedHashMap<>();
        for (final PornhubSearchResult result : rawResults) {
            final int score = score(result, normalizedQuery, queryTokens);
            if (score <= 0) {
                continue;
            }
            final PornhubSearchResult scored = new PornhubSearchResult(
                    result.id, result.url, result.title, result.thumbnail, result.duration,
                    result.uploaderName, result.uploaderUrl, result.tags, result.categories,
                    result.searchableText, score);
            final PornhubSearchResult existing = ranked.get(result.id);
            if (existing == null || scored.relevanceScore > existing.relevanceScore) {
                ranked.put(result.id, scored);
            }
        }

        final List<PornhubSearchResult> results = new ArrayList<>(ranked.values());
        results.sort(Comparator
                .comparingInt((PornhubSearchResult result) -> result.relevanceScore).reversed()
                .thenComparing(result -> PornhubTextNormalizer.normalize(result.title)));

        final boolean hasPotentialNextPage = rawResults.size() >= PAGE_SIZE && page < MAX_PAGES;
        final Page nextPage = hasPotentialNextPage
                ? new Page(PornhubParsingHelper.searchUrl(query, page + 1), String.valueOf(page + 1))
                : null;
        return new SearchPage(results.subList(0, Math.min(results.size(), PAGE_SIZE)), nextPage);
    }

    static int score(final PornhubSearchResult result,
                     final String normalizedQuery,
                     final List<String> queryTokens) {
        final String title = PornhubTextNormalizer.normalize(result.title);
        final String uploader = PornhubTextNormalizer.normalize(result.uploaderName);
        final String tags = PornhubTextNormalizer.normalize(String.join(" ", result.tags));
        final String categories = PornhubTextNormalizer.normalize(String.join(" ", result.categories));
        final String searchable = PornhubTextNormalizer.normalize(result.searchableText);

        if (title.isEmpty()) {
            return 0;
        }

        int score = 0;
        if (title.equals(normalizedQuery)) {
            score += EXACT_TITLE_SCORE;
        } else if (title.contains(normalizedQuery)) {
            score += TITLE_PHRASE_SCORE;
        }

        int matchedTokens = 0;
        for (final String token : queryTokens) {
            final boolean titleMatch = title.contains(token);
            final boolean tagMatch = tags.contains(token);
            final boolean categoryMatch = categories.contains(token);
            final boolean uploaderMatch = uploader.contains(token);
            final boolean genericMatch = searchable.contains(token);
            if (!(titleMatch || tagMatch || categoryMatch || uploaderMatch || genericMatch)) {
                continue;
            }
            matchedTokens++;
            if (titleMatch) {
                score += TITLE_TOKEN_SCORE;
            }
            if (tagMatch) {
                score += TAG_TOKEN_SCORE;
            }
            if (categoryMatch) {
                score += CATEGORY_TOKEN_SCORE;
            }
            if (uploaderMatch) {
                score += UPLOADER_TOKEN_SCORE;
            }
            if (genericMatch) {
                score += SEARCHABLE_TOKEN_SCORE;
            }
        }

        if (matchedTokens == 0) {
            return 0;
        }
        if (queryTokens.size() > 1 && matchedTokens < queryTokens.size()
                && !title.contains(normalizedQuery) && !searchable.contains(normalizedQuery)) {
            return 0;
        }
        return score + matchedTokens;
    }

    static final class SearchPage {
        final List<PornhubSearchResult> results;
        final Page nextPage;

        SearchPage(final List<PornhubSearchResult> results, final Page nextPage) {
            this.results = results;
            this.nextPage = nextPage;
        }
    }
}
