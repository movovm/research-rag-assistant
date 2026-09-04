package com.salesmentor.experience.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.salesmentor.experience.domain.ExperienceUnit;
import com.salesmentor.salescase.domain.SalesCase;

import java.time.LocalDateTime;

@TableName("sm_experience_unit")
public class ExperienceEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long caseId;
    private String scenarioType;
    private String objectionType;
    private String salesStage;
    private String customerRole;
    private String triggerText;
    private String strategySummary;
    private String recommendedQuestion;
    private String evidenceQuote;
    private Integer evidenceStart;
    private Integer evidenceEnd;
    private String applicability;
    private String contentHash;
    private String reviewStatus;
    private String indexStatus;
    private String vectorRef;
    private String extractionModel;
    private String promptVersion;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ExperienceEntity fromDomain(ExperienceUnit value) {
        ExperienceEntity entity = new ExperienceEntity();
        entity.id = value.id();
        entity.caseId = value.caseId();
        entity.scenarioType = value.scenarioType().name();
        entity.objectionType = value.objectionType() == null ? null : value.objectionType().name();
        entity.salesStage = value.salesStage() == null ? null : value.salesStage().name();
        entity.customerRole = value.customerRole();
        entity.triggerText = value.triggerText();
        entity.strategySummary = value.strategySummary();
        entity.recommendedQuestion = value.recommendedQuestion();
        entity.evidenceQuote = value.evidenceQuote();
        entity.evidenceStart = value.evidenceStart();
        entity.evidenceEnd = value.evidenceEnd();
        entity.applicability = value.applicability();
        entity.contentHash = value.contentHash();
        entity.reviewStatus = value.reviewStatus().name();
        entity.indexStatus = value.indexStatus().name();
        entity.vectorRef = value.vectorRef();
        entity.extractionModel = value.extractionModel();
        entity.promptVersion = value.promptVersion();
        entity.reviewedBy = value.reviewedBy();
        entity.reviewedAt = value.reviewedAt();
        entity.version = value.version();
        entity.createdAt = value.createdAt();
        entity.updatedAt = value.updatedAt();
        return entity;
    }

    public ExperienceUnit toDomain() {
        return new ExperienceUnit(id, caseId, ExperienceUnit.ScenarioType.valueOf(scenarioType),
                objectionType == null ? null : ExperienceUnit.ObjectionType.valueOf(objectionType),
                salesStage == null ? null : SalesCase.SalesStage.valueOf(salesStage), customerRole, triggerText,
                strategySummary, recommendedQuestion, evidenceQuote, evidenceStart, evidenceEnd, applicability,
                contentHash, ExperienceUnit.ReviewStatus.valueOf(reviewStatus),
                ExperienceUnit.IndexStatus.valueOf(indexStatus), vectorRef, extractionModel, promptVersion,
                reviewedBy, reviewedAt, version == null ? 0 : version, createdAt, updatedAt);
    }
}
