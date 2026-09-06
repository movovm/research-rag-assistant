package com.salesmentor.trace;

import com.salesmentor.trace.application.ReviewTaskTraceRecorder;
import com.salesmentor.trace.domain.AgentTrace;
import com.salesmentor.trace.domain.AgentTraceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ReviewTaskTraceRecorderTest {
    @Test
    void appendsOnlyFixedSafeTaskEvents() {
        AgentTraceRepository repository = mock(AgentTraceRepository.class);
        ReviewTaskTraceRecorder recorder = new ReviewTaskTraceRecorder(repository);

        recorder.claimed(12L, -1);
        recorder.agentCompleted(12L, 4);
        recorder.succeeded(12L, 9);
        recorder.failed(13L, 2, "AGENT_FAILED");

        ArgumentCaptor<AgentTrace> captured = ArgumentCaptor.forClass(AgentTrace.class);
        verify(repository, times(4)).append(captured.capture());
        List<AgentTrace> values = captured.getAllValues();
        assertThat(values).extracting(AgentTrace::stepNo).containsExactly(1, 2, 3, 3);
        assertThat(values).extracting(AgentTrace::outputSummary).containsExactly(
                "review task claimed", "review agent completed", "review task succeeded", "review task failed");
        assertThat(values).allSatisfy(value -> {
            assertThat(value.id()).isNull();
            assertThat(value.stepType()).isEqualTo(AgentTrace.StepType.TASK);
            assertThat(value.toolName()).isNull();
            assertThat(value.inputJson()).isNull();
            assertThat(value.evidenceIds()).isNull();
            assertThat(value.durationMs()).isGreaterThanOrEqualTo(0);
        });
        assertThat(values.get(3).errorCode()).isEqualTo("AGENT_FAILED");
        assertThat(values.subList(0, 3)).extracting(AgentTrace::errorCode).containsOnlyNulls();
    }

    @Test
    void swallowsTraceInfrastructureFailuresButRejectsInvalidRecorderInput() {
        AgentTraceRepository repository = mock(AgentTraceRepository.class);
        doThrow(new IllegalStateException("database unavailable")).when(repository).append(any());
        ReviewTaskTraceRecorder recorder = new ReviewTaskTraceRecorder(repository);

        recorder.claimed(4L, 0);

        assertThatThrownBy(() -> recorder.failed(4L, 0, "unsafe message"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recorder.succeeded(0L, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
