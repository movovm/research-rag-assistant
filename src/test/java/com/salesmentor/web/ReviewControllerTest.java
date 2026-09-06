package com.salesmentor.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmentor.agent.report.ReviewReport;
import com.salesmentor.review.application.ReviewTaskApplicationService;
import com.salesmentor.review.application.ReviewTaskSubmissionService;
import com.salesmentor.review.application.ReviewTaskEventSubscriptionService;
import com.salesmentor.review.domain.ReviewTask;
import com.salesmentor.review.domain.ReviewTaskInputConflictException;
import com.salesmentor.review.domain.ReviewTaskNotFoundException;
import com.salesmentor.trace.domain.AgentTrace;
import com.salesmentor.trace.domain.AgentTraceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
@Import(ApiExceptionHandler.class)
class ReviewControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @MockBean private ReviewTaskApplicationService tasks;
    @MockBean private ReviewTaskSubmissionService submissions;
    @MockBean private AgentTraceRepository traces;
    @MockBean private ReviewTaskEventSubscriptionService eventSubscriptions;

    private ReviewTask pending;
    private ReviewTask succeeded;

    @BeforeEach
    void setUp() {
        pending = task(11L, ReviewTask.Status.PENDING, null);
        succeeded = task(12L, ReviewTask.Status.SUCCEEDED,
                "{\"currentObservations:[]\",\"historicalExperiences\":[],\"productFacts\":[],\"recommendations\":[],\"nextQuestions\":[],\"references\":[],\"limitations\":[]}");
    }

    @Test
    void acceptedCreateReturns202AndStatusUrl() throws Exception {
        when(tasks.create(any())).thenReturn(pending);
        when(submissions.submit(11L)).thenReturn(new ReviewTaskSubmissionService.ReviewTaskSubmission(
                11L, ReviewTaskSubmissionService.ReviewTaskSubmission.Status.ACCEPTED, ReviewTask.Status.PENDING));
        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.taskId").value(11))
                .andExpect(jsonPath("$.submissionStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.statusUrl").value("/api/reviews/11"));
        verify(submissions).submit(11L);
    }

    @Test
    void rejectedCreateReturns503AndNotPendingReturns200() throws Exception {
        when(tasks.create(any())).thenReturn(pending);
        when(submissions.submit(11L)).thenReturn(new ReviewTaskSubmissionService.ReviewTaskSubmission(
                11L, ReviewTaskSubmissionService.ReviewTaskSubmission.Status.REJECTED, ReviewTask.Status.PENDING));
        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.errorCode").value("REVIEW_QUEUE_FULL"));
        when(submissions.submit(11L)).thenReturn(new ReviewTaskSubmissionService.ReviewTaskSubmission(
                11L, ReviewTaskSubmissionService.ReviewTaskSubmission.Status.NOT_PENDING, ReviewTask.Status.RUNNING));
        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.taskStatus").value("RUNNING"));
    }

    @Test
    void conflictAndInvalidRequestsUseSafeStatuses() throws Exception {
        AtomicBoolean firstConflict = new AtomicBoolean(true);
        when(tasks.create(any())).thenAnswer(invocation -> firstConflict.getAndSet(false)
                ? throwConflict() : pending);
        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVIEW_INPUT_CONFLICT"));
        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":1"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"r\",\"conversationContent\":\"\",\"reviewGoal\":\"g\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getsStructuredReportAndHidesTaskInternals() throws Exception {
        ReviewReport report = new ReviewReport(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        succeeded = task(12L, ReviewTask.Status.SUCCEEDED, mapper.writeValueAsString(report));
        when(tasks.find(12L)).thenReturn(succeeded);
        mvc.perform(get("/api/reviews/12")).andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(12)).andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.report").exists()).andExpect(jsonPath("$.conversationContent").doesNotExist())
                .andExpect(jsonPath("$.reportJson").doesNotExist());
    }

    @Test
    void nonSuccessHasNoReportAndMalformedSnapshotIs500() throws Exception {
        when(tasks.find(11L)).thenReturn(pending);
        mvc.perform(get("/api/reviews/11")).andExpect(status().isOk()).andExpect(jsonPath("$.report").doesNotExist());
        when(tasks.find(12L)).thenReturn(task(12L, ReviewTask.Status.SUCCEEDED, "not-json"));
        mvc.perform(get("/api/reviews/12")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("服务暂时不可用，请查看服务日志"));
        when(tasks.find(99L)).thenThrow(new ReviewTaskNotFoundException());
        mvc.perform(get("/api/reviews/99")).andExpect(status().isNotFound());
        mvc.perform(get("/api/reviews/not-a-number")).andExpect(status().isBadRequest());
    }

    @Test
    void tracesAreSortedAndOnlySafeEventsAreExposed() throws Exception {
        when(tasks.find(11L)).thenReturn(pending);
        LocalDateTime now = LocalDateTime.now();
        when(traces.findByTaskId(11L)).thenReturn(List.of(
                new AgentTrace(2L, 11L, 2, AgentTrace.StepType.TASK, null, "secret", "review agent completed", "[\"EXP-1\"]", 3, AgentTrace.Status.SUCCEEDED, null, now),
                new AgentTrace(1L, 11L, 1, AgentTrace.StepType.TOOL, "EXPERIENCE_SEARCH", "secret", "free", "[]", 2, AgentTrace.Status.SUCCEEDED, null, now),
                new AgentTrace(3L, 11L, 3, AgentTrace.StepType.TASK, null, null, "review task succeeded", null, 4, AgentTrace.Status.SUCCEEDED, null, now)));
        mvc.perform(get("/api/reviews/11/traces")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stepNo").value(2)).andExpect(jsonPath("$[0].inputJson").doesNotExist())
                .andExpect(jsonPath("$[0].evidenceIds").doesNotExist()).andExpect(jsonPath("$[1].stepNo").value(3));
        when(traces.findByTaskId(11L)).thenReturn(List.of());
        mvc.perform(get("/api/reviews/11/traces")).andExpect(status().isOk()).andExpect(jsonPath("$", org.hamcrest.Matchers.empty()));
    }

    private String requestJson() {
        return "{\"requestId\":\"req-1\",\"industry\":\"tech\",\"salesStage\":\"NEGOTIATION\",\"customerRole\":\"buyer\",\"conversationContent\":\"customer price objection\",\"reviewGoal\":\"review\"}";
    }

    private ReviewTask throwConflict() {
        throw new ReviewTaskInputConflictException();
    }

    private ReviewTask task(Long id, ReviewTask.Status status, String reportJson) {
        LocalDateTime now = LocalDateTime.now();
        return new ReviewTask(id, "request-" + id, null, null, "industry", null, "buyer", "conversation",
                "review goal", status, null, reportJson, null, status == ReviewTask.Status.PENDING ? 0 : 2,
                null, null, null, null, now, now);
    }
}
