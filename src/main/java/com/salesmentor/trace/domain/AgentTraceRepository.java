package com.salesmentor.trace.domain;

import java.util.List;

public interface AgentTraceRepository {
    AgentTrace append(AgentTrace trace);

    List<AgentTrace> findByTaskId(Long taskId);
}
