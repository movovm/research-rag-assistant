package com.salesmentor.web;

import com.salesmentor.experience.application.ExperiencePublishApplicationService;
import com.salesmentor.experience.application.ExperienceReviewApplicationService;
import com.salesmentor.experience.domain.ExperienceIndexingUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

    @Test
    void mapsIndexingUnavailableTo503() throws Exception {
        long id = 42L;
        when(publishService.publish(id)).thenThrow(new ExperienceIndexingUnavailableException());

        mvc.perform(post("/api/v1/experiences/{id}/publish", id))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EXPERIENCE_INDEXING_UNAVAILABLE"));

        verify(publishService).publish(id);
    }
}
