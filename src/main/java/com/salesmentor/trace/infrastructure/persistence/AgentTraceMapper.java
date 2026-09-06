package com.salesmentor.trace.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentTraceMapper extends BaseMapper<AgentTraceEntity> {
}
