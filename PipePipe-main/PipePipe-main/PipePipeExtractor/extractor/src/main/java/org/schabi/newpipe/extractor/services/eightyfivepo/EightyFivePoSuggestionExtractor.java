package org.schabi.newpipe.extractor.services.eightyfivepo;

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;

import java.util.Collections;
import java.util.List;

public final class EightyFivePoSuggestionExtractor extends SuggestionExtractor {
    public EightyFivePoSuggestionExtractor(final StreamingService service) {
        super(service);
    }

    @Override
    public List<String> suggestionList(final String query) {
        return Collections.emptyList();
    }
}
