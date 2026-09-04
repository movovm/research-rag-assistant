package com.salesmentor.trace.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.salesmentor.trace.domain.AgentTrace;
import com.salesmentor.trace.domain.AgentTraceRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class MybatisAgentTraceRepository implements AgentTraceRepository {
    private final AgentTraceMapper mapper;

    public MybatisAgentTraceRepository(AgentTraceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AgentTrace save(AgentTrace value) {
        AgentTrace prepared = new AgentTrace(value.id(), value.taskId(), value.stepNo(), value.stepType(),
                value.toolName(), value.inputJson(), value.outputSummary(), value.evidenceIds(), value.durationMs(),
                value.status(), value.errorCode(), value.createdAt() == null ? LocalDateTime.now() : value.createdAt());
        AgentTraceEntity entity = AgentTraceEntity.fromDomain(prepared);
        if (prepared.id() == null) mapper.insert(entity);
        else mapper.updateById(entity);
        return entity.toDomain();
    }

    @Override
    public List<AgentTrace> findByTaskId(Long taskId) {
        QueryWrapper<AgentTraceEntity> query = new QueryWrapper<>();
        query.eq("task_id", taskId).orderByAsc("step_no");
        return mapper.selectList(query).stream().map(AgentTraceEntity::toDomain).toList();
    }
}
