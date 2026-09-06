package com.salesmentor.agent.tool;

import com.salesmentor.agent.model.ReviewToolName;
import com.salesmentor.experience.application.ExperienceQuery;
import com.salesmentor.experience.application.ExperienceSearchApplicationService;
import com.salesmentor.experience.application.ExperienceSearchResult;

import java.util.List;

public final class ExperienceSearchTool implements ReviewReadOnlyTool {
    private final ExperienceSearchApplicationService searchService;

    public ExperienceSearchTool(ExperienceSearchApplicationService searchService) {
        this.searchService = searchService;
    }

    @Override
    public ReviewToolName name() {
        return ReviewToolName.EXPERIENCE_SEARCH;
    }

    public List<ExperienceSearchResult> search(ExperienceQuery query) {
        if (query == null) throw new IllegalArgumentException("query is required");
        return searchService.search(query);
    }
}
