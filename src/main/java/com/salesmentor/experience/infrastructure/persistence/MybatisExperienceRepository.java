package com.salesmentor.experience.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.salesmentor.experience.domain.ExperienceRepository;
import com.salesmentor.experience.domain.ExperienceUnit;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MybatisExperienceRepository implements ExperienceRepository {
    private final ExperienceMapper mapper;

    public MybatisExperienceRepository(ExperienceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ExperienceUnit save(ExperienceUnit value) {
        LocalDateTime now = LocalDateTime.now();
        ExperienceUnit prepared = new ExperienceUnit(value.id(), value.caseId(), value.scenarioType(),
                value.objectionType(), value.salesStage(), value.customerRole(), value.triggerText(),
                value.strategySummary(), value.recommendedQuestion(), value.evidenceQuote(), value.evidenceStart(),
                value.evidenceEnd(), value.applicability(), value.contentHash(), value.reviewStatus(),
                value.indexStatus(), value.vectorRef(), value.extractionModel(), value.promptVersion(),
                value.reviewedBy(), value.reviewedAt(), value.version(),
                value.createdAt() == null ? now : value.createdAt(), now);
        ExperienceEntity entity = ExperienceEntity.fromDomain(prepared);
        if (prepared.id() == null) mapper.insert(entity);
        else mapper.updateById(entity);
        return entity.toDomain();
    }

    @Override
    public Optional<ExperienceUnit> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(ExperienceEntity::toDomain);
    }

    @Override
    public List<ExperienceUnit> findByCaseId(Long caseId) {
        QueryWrapper<ExperienceEntity> query = new QueryWrapper<>();
        query.eq("case_id", caseId).orderByAsc("id");
        return mapper.selectList(query).stream().map(ExperienceEntity::toDomain).toList();
    }

    @Override
    public boolean compareAndSetReviewStatus(Long id, ExperienceUnit.ReviewStatus expected,
                                             ExperienceUnit.ReviewStatus target, int version) {
        UpdateWrapper<ExperienceEntity> update = new UpdateWrapper<>();
        update.eq("id", id).eq("review_status", expected.name()).eq("version", version)
                .set("review_status", target.name())
                .set("updated_at", LocalDateTime.now())
                .setSql("version = version + 1");
        return mapper.update(null, update) == 1;
    }
}
