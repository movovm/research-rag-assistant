package com.salesmentor.review.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.salesmentor.review.domain.ReviewTask;
import com.salesmentor.review.domain.ReviewTaskRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Repository
public class MybatisReviewTaskRepository implements ReviewTaskRepository {
    private final ReviewTaskMapper mapper;

    public MybatisReviewTaskRepository(ReviewTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ReviewTask save(ReviewTask value) {
        if (value == null) throw new IllegalArgumentException("task is required");
        if (value.id() != null) throw new IllegalArgumentException("existing tasks require a CAS operation");
        if (value.status() != ReviewTask.Status.PENDING || value.version() != 0) {
            throw new IllegalArgumentException("new tasks must be PENDING with version 0");
        }
        LocalDateTime now = LocalDateTime.now();
        ReviewTask prepared = new ReviewTask(value.id(), value.requestId(), value.userId(), value.sessionId(),
                value.industry(), value.salesStage(), value.customerRole(), value.conversationContent(),
                value.reviewGoal(), value.status(), value.planJson(), value.reportJson(), value.partialReason(),
                value.version(), value.failureCode(), value.failureReason(),
                value.startedAt(), value.finishedAt(), value.createdAt() == null ? now : value.createdAt(), now);
        ReviewTaskEntity entity = ReviewTaskEntity.fromDomain(prepared);
        mapper.insert(entity);
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
    public boolean start(Long id, long expectedVersion) {
        if (id == null || expectedVersion < 0) throw new IllegalArgumentException("invalid CAS arguments");
        UpdateWrapper<ReviewTaskEntity> update = new UpdateWrapper<>();
        LocalDateTime now = LocalDateTime.now();
        update.eq("id", id).eq("status", ReviewTask.Status.PENDING.name()).eq("version", expectedVersion)
                .set("status", ReviewTask.Status.RUNNING.name())
                .set("started_at", now)
                .set("updated_at", LocalDateTime.now());
        update.setSql("version = version + 1");
        return mapper.update(null, update) == 1;
    }

    @Override
    public boolean succeed(Long id, long expectedVersion, String reportJson) {
        requireNonBlank(reportJson, "reportJson");
        if (id == null || expectedVersion < 0) throw new IllegalArgumentException("invalid CAS arguments");
        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<ReviewTaskEntity> update = new UpdateWrapper<>();
        update.eq("id", id).eq("status", ReviewTask.Status.RUNNING.name()).eq("version", expectedVersion)
                .set("status", ReviewTask.Status.SUCCEEDED.name())
                .set("report_json", reportJson)
                .set("finished_at", now)
                .set("updated_at", now)
                .setSql("version = version + 1");
        return mapper.update(null, update) == 1;
    }

    @Override
    public boolean fail(Long id, long expectedVersion, String failureCode, String failureReason) {
        requireSafe(failureCode, 64, "failureCode");
        requireSafe(failureReason, 500, "failureReason");
        if (id == null || expectedVersion < 0) throw new IllegalArgumentException("invalid CAS arguments");
        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<ReviewTaskEntity> update = new UpdateWrapper<>();
        update.eq("id", id).eq("status", ReviewTask.Status.RUNNING.name()).eq("version", expectedVersion)
                .set("status", ReviewTask.Status.FAILED.name())
                .set("failure_code", failureCode)
                .set("failure_reason", failureReason)
                .set("finished_at", now)
                .set("updated_at", now)
                .setSql("version = version + 1");
        return mapper.update(null, update) == 1;
    }

    @Override
    public List<ReviewTask> findExpiredRunning(LocalDateTime deadline, int limit) {
        if (deadline == null || limit <= 0) throw new IllegalArgumentException("invalid expiry query");
        QueryWrapper<ReviewTaskEntity> query = new QueryWrapper<>();
        query.eq("status", ReviewTask.Status.RUNNING.name())
                .isNotNull("started_at")
                .le("started_at", deadline)
                .orderByAsc("started_at", "id")
                .last("LIMIT " + Math.min(limit, 1000));
        return mapper.selectList(query).stream().map(ReviewTaskEntity::toDomain).toList();
    }

    @Override
    public boolean timeout(Long id, long expectedVersion, LocalDateTime deadline) {
        if (id == null || expectedVersion < 0 || deadline == null) {
            throw new IllegalArgumentException("invalid timeout CAS arguments");
        }
        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<ReviewTaskEntity> update = new UpdateWrapper<>();
        update.eq("id", id).eq("status", ReviewTask.Status.RUNNING.name()).eq("version", expectedVersion)
                .isNotNull("started_at").le("started_at", deadline)
                .set("status", ReviewTask.Status.FAILED.name())
                .set("failure_code", "REVIEW_EXECUTION_TIMEOUT")
                .set("failure_reason", "review execution deadline exceeded")
                .set("finished_at", now).set("updated_at", now)
                .setSql("version = version + 1");
        return mapper.update(null, update) == 1;
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }

    private static void requireSafe(String value, int max, String name) {
        requireNonBlank(value, name);
        if (value.length() > max || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }
}
