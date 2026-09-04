package com.salesmentor.salescase.domain;

import java.util.Optional;

public interface SalesCaseRepository {
    SalesCase save(SalesCase salesCase);

    Optional<SalesCase> findById(Long id);

    boolean compareAndSetStatus(Long id, SalesCase.Status expected, SalesCase.Status target, String extractError);
}
