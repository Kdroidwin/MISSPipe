package org.schabi.newpipe.extractor.services.javsb;

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public final class JavSbSuggestionExtractor extends SuggestionExtractor {
    public JavSbSuggestionExtractor(final StreamingService service) {
        super(service);
    }

    @Override
    public List<String> suggestionList(final String query)
            throws IOException, ExtractionException {
        return Collections.emptyList();
    }
}

