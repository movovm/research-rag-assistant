package com.salesmentor.salescase.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.salesmentor.salescase.domain.SalesCase;

import java.time.LocalDateTime;

@TableName("sm_sales_case")
public class SalesCaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String externalKey;
    private String title;
    private String sourceType;
    private String sourceUri;
    private String industry;
    private String salesStage;
    private String customerRole;
    private String content;
    private String status;
    private String extractError;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SalesCaseEntity fromDomain(SalesCase value) {
        SalesCaseEntity entity = new SalesCaseEntity();
        entity.id = value.id();
        entity.externalKey = value.externalKey();
        entity.title = value.title();
        entity.sourceType = value.sourceType().name();
        entity.sourceUri = value.sourceUri();
        entity.industry = value.industry();
        entity.salesStage = value.salesStage() == null ? null : value.salesStage().name();
        entity.customerRole = value.customerRole();
        entity.content = value.content();
        entity.status = value.status().name();
        entity.extractError = value.extractError();
        entity.version = value.version();
        entity.createdAt = value.createdAt();
        entity.updatedAt = value.updatedAt();
        return entity;
    }

    public SalesCase toDomain() {
        return new SalesCase(id, externalKey, title, SalesCase.SourceType.valueOf(sourceType), sourceUri, industry,
                salesStage == null ? null : SalesCase.SalesStage.valueOf(salesStage), customerRole, content,
                SalesCase.Status.valueOf(status), extractError, version == null ? 0 : version, createdAt, updatedAt);
    }
}
