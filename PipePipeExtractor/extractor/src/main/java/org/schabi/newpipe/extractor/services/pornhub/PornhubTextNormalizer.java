package org.schabi.newpipe.extractor.services.pornhub;

import org.jsoup.parser.Parser;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class PornhubTextNormalizer {
    private static final Pattern MARK_PATTERN = Pattern.compile("\\p{M}+");
    private static final Pattern SEPARATOR_PATTERN =
            Pattern.compile("[\\p{Z}\\s\\p{Punct}＿ー－―‐]+");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");

    private PornhubTextNormalizer() {
    }

    static String normalize(final String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String normalized = Parser.unescapeEntities(value, true);
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFKC);
        normalized = normalized.toLowerCase(Locale.ROOT);
        normalized = MARK_PATTERN.matcher(normalized).replaceAll("");
        normalized = normalized.replace('\u3000', ' ');
        normalized = SEPARATOR_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = MULTI_SPACE_PATTERN.matcher(normalized).replaceAll(" ").trim();
        return normalized;
    }

    static List<String> tokens(final String value) {
        final String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return new ArrayList<>();
        }
        final Set<String> tokens = new LinkedHashSet<>();
        for (final String token : normalized.split(" ")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        if (tokens.isEmpty() && normalized.length() == 1) {
            tokens.add(normalized);
        }
        return new ArrayList<>(tokens);
    }
}
