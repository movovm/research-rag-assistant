package com.salesmentor.agent.tool;

import com.salesmentor.experience.application.ExperienceQuery;
import com.salesmentor.experience.application.ExperienceSearchApplicationService;
import com.salesmentor.experience.application.ExperienceSearchResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ExperienceSearchToolTest {
    @Test void delegatesSameQueryAndKeepsResults() {
        ExperienceSearchApplicationService service = mock(ExperienceSearchApplicationService.class);
        ExperienceSearchTool tool = new ExperienceSearchTool(service);
        ExperienceQuery query = new ExperienceQuery("price", null, null, null, null, 1);
        List<ExperienceSearchResult> expectedResults = new ArrayList<>();
        when(service.search(query)).thenReturn(expectedResults);
        assertThat(tool.name()).isEqualTo(com.salesmentor.agent.model.ReviewToolName.EXPERIENCE_SEARCH);
        assertThat(tool.search(query)).isSameAs(expectedResults);
        verify(service).search(query);
    }

    @Test void rejectsNullQuery() {
        ExperienceSearchApplicationService service = mock(ExperienceSearchApplicationService.class);
        assertThatThrownBy(() -> new ExperienceSearchTool(service).search(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(service);
    }
}
