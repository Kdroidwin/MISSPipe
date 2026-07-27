package org.schabi.newpipe.extractor.services.pornhub;

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;

import java.util.Collections;
import java.util.List;

public final class PornhubSuggestionExtractor extends SuggestionExtractor {
    public PornhubSuggestionExtractor(final StreamingService service) {
        super(service);
    }

    @Override
    public List<String> suggestionList(final String query) {
        return Collections.emptyList();
    }
}
