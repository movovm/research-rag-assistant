package com.salesmentor.trace.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.salesmentor.trace.domain.AgentTrace;

import java.time.LocalDateTime;

@TableName("sm_agent_trace")
public class AgentTraceEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long taskId;
    private Integer stepNo;
    private String stepType;
    private String toolName;
    private String inputJson;
    private String outputSummary;
    private String evidenceIds;
    private Long durationMs;
    private String status;
    private String errorCode;
    private LocalDateTime createdAt;

    public static AgentTraceEntity fromDomain(AgentTrace value) {
        AgentTraceEntity entity = new AgentTraceEntity();
        entity.id = value.id();
        entity.taskId = value.taskId();
        entity.stepNo = value.stepNo();
        entity.stepType = value.stepType().name();
        entity.toolName = value.toolName();
        entity.inputJson = value.inputJson();
        entity.outputSummary = value.outputSummary();
        entity.evidenceIds = value.evidenceIds();
        entity.durationMs = value.durationMs();
        entity.status = value.status().name();
        entity.errorCode = value.errorCode();
        entity.createdAt = value.createdAt();
        return entity;
    }

    public AgentTrace toDomain() {
        return new AgentTrace(id, taskId, stepNo, AgentTrace.StepType.valueOf(stepType), toolName, inputJson,
                outputSummary, evidenceIds, durationMs, AgentTrace.Status.valueOf(status), errorCode, createdAt);
    }
}
