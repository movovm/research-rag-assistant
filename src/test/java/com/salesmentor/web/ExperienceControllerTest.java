package com.salesmentor.web;

import com.salesmentor.experience.application.ExperiencePublishApplicationService;
import com.salesmentor.experience.application.ExperienceReviewApplicationService;
import com.salesmentor.experience.application.ExperienceSearchApplicationService;
import com.salesmentor.experience.application.ExperienceQuery;
import com.salesmentor.experience.domain.ExperienceIndexingUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExperienceController.class)
@Import(ApiExceptionHandler.class)
class ExperienceControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private ExperiencePublishApplicationService publishService;

    @MockBean
    private ExperienceReviewApplicationService reviewService;

    @MockBean
    private ExperienceSearchApplicationService searchService;

    @Test
    void mapsIndexingUnavailableTo503() throws Exception {
        long id = 42L;
        when(publishService.publish(id)).thenThrow(new ExperienceIndexingUnavailableException());

        mvc.perform(post("/api/v1/experiences/{id}/publish", id))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EXPERIENCE_INDEXING_UNAVAILABLE"));

        verify(publishService).publish(id);
    }

    @Test
    void mapsSearchRequestAndDefaultsTopK() throws Exception {
        when(searchService.search(any())).thenReturn(java.util.List.of());
        mvc.perform(post("/api/v1/experiences/search").contentType("application/json")
                        .content("{\"queryText\":\"price\",\"scenarioType\":\"OBJECTION_HANDLING\","
                                + "\"objectionType\":\"PRICE\",\"salesStage\":\"NEGOTIATION\","
                                + "\"customerRole\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.empty()));

        org.mockito.ArgumentCaptor<ExperienceQuery> captor =
                org.mockito.ArgumentCaptor.forClass(ExperienceQuery.class);
        verify(searchService).search(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().queryText()).isEqualTo("price");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().topK()).isEqualTo(5);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().customerRole()).isNull();
        org.assertj.core.api.Assertions.assertThat(captor.getValue().scenarioType())
                .isEqualTo(com.salesmentor.experience.domain.ExperienceUnit.ScenarioType.OBJECTION_HANDLING);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().objectionType())
                .isEqualTo(com.salesmentor.experience.domain.ExperienceUnit.ObjectionType.PRICE);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().salesStage())
                .isEqualTo(com.salesmentor.salescase.domain.SalesCase.SalesStage.NEGOTIATION);
    }

    @Test
    void rejectsBlankQueryAndOutOfRangeTopK() throws Exception {
        mvc.perform(post("/api/v1/experiences/search").contentType("application/json")
                        .content("{\"queryText\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/experiences/search").contentType("application/json")
                        .content("{\"queryText\":\"q\",\"topK\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/experiences/search").contentType("application/json")
                        .content("{\"queryText\":\"q\",\"topK\":21}"))
                .andExpect(status().isBadRequest());
    }
}
