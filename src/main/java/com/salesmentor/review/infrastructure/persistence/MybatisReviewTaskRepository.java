package com.salesmentor.review.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.salesmentor.review.domain.ReviewTask;
import com.salesmentor.review.domain.ReviewTaskRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class MybatisReviewTaskRepository implements ReviewTaskRepository {
    private final ReviewTaskMapper mapper;

    public MybatisReviewTaskRepository(ReviewTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ReviewTask save(ReviewTask value) {
        LocalDateTime now = LocalDateTime.now();
        ReviewTask prepared = new ReviewTask(value.id(), value.requestId(), value.userId(), value.sessionId(),
                value.industry(), value.salesStage(), value.customerRole(), value.conversationContent(),
                value.reviewGoal(), value.status(), value.planJson(), value.reportJson(), value.partialReason(),
                value.startedAt(), value.finishedAt(), value.createdAt() == null ? now : value.createdAt(), now);
        ReviewTaskEntity entity = ReviewTaskEntity.fromDomain(prepared);
        if (prepared.id() == null) mapper.insert(entity);
        else mapper.updateById(entity);
        return entity.toDomain();
    }

    @Override
    public Optional<ReviewTask> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(ReviewTaskEntity::toDomain);
    }

    @Override
    public Optional<ReviewTask> findByRequestId(String requestId) {
        QueryWrapper<ReviewTaskEntity> query = new QueryWrapper<>();
        query.eq("request_id", requestId).last("LIMIT 1");
        return Optional.ofNullable(mapper.selectOne(query)).map(ReviewTaskEntity::toDomain);
    }

    @Override
    public boolean compareAndSetStatus(Long id, ReviewTask.Status expected, ReviewTask.Status target) {
        UpdateWrapper<ReviewTaskEntity> update = new UpdateWrapper<>();
        update.eq("id", id).eq("status", expected.name())
                .set("status", target.name())
                .set("updated_at", LocalDateTime.now());
        if (target == ReviewTask.Status.RUNNING) update.set("started_at", LocalDateTime.now());
        if (target == ReviewTask.Status.SUCCEEDED || target == ReviewTask.Status.PARTIAL_SUCCEEDED
                || target == ReviewTask.Status.FAILED) {
            update.set("finished_at", LocalDateTime.now());
        }
        return mapper.update(null, update) == 1;
    }
}
