package com.salesmentor.review.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReviewTaskMapper extends BaseMapper<ReviewTaskEntity> {
}
