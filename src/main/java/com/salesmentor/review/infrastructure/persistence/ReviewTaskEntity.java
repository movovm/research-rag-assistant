package com.salesmentor.review.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.salesmentor.review.domain.ReviewTask;
import com.salesmentor.salescase.domain.SalesCase;

import java.time.LocalDateTime;

@TableName("sm_review_task")
public class ReviewTaskEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String requestId;
    private Long userId;
    private String sessionId;
    private String industry;
    private String salesStage;
    private String customerRole;
    private String conversationContent;
    private String reviewGoal;
    private String status;
    private String planJson;
    private String reportJson;
    private String partialReason;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ReviewTaskEntity fromDomain(ReviewTask value) {
        ReviewTaskEntity entity = new ReviewTaskEntity();
        entity.id = value.id();
        entity.requestId = value.requestId();
        entity.userId = value.userId();
        entity.sessionId = value.sessionId();
        entity.industry = value.industry();
        entity.salesStage = value.salesStage() == null ? null : value.salesStage().name();
        entity.customerRole = value.customerRole();
        entity.conversationContent = value.conversationContent();
        entity.reviewGoal = value.reviewGoal();
        entity.status = value.status().name();
        entity.planJson = value.planJson();
        entity.reportJson = value.reportJson();
        entity.partialReason = value.partialReason();
        entity.startedAt = value.startedAt();
        entity.finishedAt = value.finishedAt();
        entity.createdAt = value.createdAt();
        entity.updatedAt = value.updatedAt();
        return entity;
    }

    public ReviewTask toDomain() {
        return new ReviewTask(id, requestId, userId, sessionId, industry,
                salesStage == null ? null : SalesCase.SalesStage.valueOf(salesStage), customerRole,
                conversationContent, reviewGoal, ReviewTask.Status.valueOf(status), planJson, reportJson,
                partialReason, startedAt, finishedAt, createdAt, updatedAt);
    }
}
