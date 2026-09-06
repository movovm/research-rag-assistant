package com.salesmentor.web;

import com.salesmentor.review.application.ReviewTaskApplicationService;
import com.salesmentor.review.application.ReviewTaskEventSubscriptionService;
import com.salesmentor.review.application.ReviewTaskSubmissionService;
import com.salesmentor.review.domain.ReviewTaskNotFoundException;
import com.salesmentor.trace.domain.AgentTraceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
@Import(ApiExceptionHandler.class)
class ReviewControllerSseTest {
    @Autowired private MockMvc mvc;
    @MockBean private ReviewTaskApplicationService tasks;
    @MockBean private ReviewTaskSubmissionService submissions;
    @MockBean private AgentTraceRepository traces;
    @MockBean private ReviewTaskEventSubscriptionService events;

    @Test
    void exposesTaskStatusEventRouteWithoutStartingExecution() throws Exception {
        when(events.subscribe(7L)).thenReturn(new SseEmitter());
        mvc.perform(get("/api/reviews/7/events")).andExpect(status().isOk());
        verify(events).subscribe(7L);
        verifyNoInteractions(tasks, submissions);
    }

    @Test
    void mapsMissingTaskBeforeSseCreationTo404() throws Exception {
        when(events.subscribe(8L)).thenThrow(new ReviewTaskNotFoundException());
        mvc.perform(get("/api/reviews/8/events")).andExpect(status().isNotFound());
    }
}
