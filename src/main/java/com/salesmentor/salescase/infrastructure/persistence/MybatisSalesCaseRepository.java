package com.salesmentor.salescase.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.salesmentor.salescase.domain.SalesCase;
import com.salesmentor.salescase.domain.SalesCaseRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class MybatisSalesCaseRepository implements SalesCaseRepository {
    private final SalesCaseMapper mapper;

    public MybatisSalesCaseRepository(SalesCaseMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public SalesCase save(SalesCase value) {
        LocalDateTime now = LocalDateTime.now();
        SalesCase prepared = new SalesCase(value.id(), value.externalKey(), value.title(), value.sourceType(),
                value.sourceUri(), value.industry(), value.salesStage(), value.customerRole(), value.content(),
                value.status(), value.extractError(), value.version(),
                value.createdAt() == null ? now : value.createdAt(), now);
        SalesCaseEntity entity = SalesCaseEntity.fromDomain(prepared);
        if (prepared.id() == null) mapper.insert(entity);
        else mapper.updateById(entity);
        return entity.toDomain();
    }

    @Override
    public Optional<SalesCase> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(SalesCaseEntity::toDomain);
    }

    @Override
    public boolean compareAndSetStatus(Long id, SalesCase.Status expected, SalesCase.Status target,
                                       String extractError) {
        UpdateWrapper<SalesCaseEntity> update = new UpdateWrapper<>();
        update.eq("id", id).eq("status", expected.name())
                .set("status", target.name())
                .set("extract_error", extractError)
                .set("updated_at", LocalDateTime.now())
                .setSql("version = version + 1");
        return mapper.update(null, update) == 1;
    }
}
