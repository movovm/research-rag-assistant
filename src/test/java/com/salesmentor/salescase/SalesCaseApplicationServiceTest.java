package com.salesmentor.salescase;

import com.salesmentor.experience.application.*;
import com.salesmentor.experience.domain.*;
import com.salesmentor.salescase.application.SalesCaseApplicationService;
import com.salesmentor.salescase.domain.SalesCase;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SalesCaseApplicationServiceTest {
    @Test void rejectsOnlyInvalidDraftAndPersistsValidDraft() {
        var cases = mock(com.salesmentor.salescase.domain.SalesCaseRepository.class);
        var experiences = mock(ExperienceRepository.class);
        var extractor = mock(ExperienceExtractor.class);
        var saved = new SalesCase(101L, "u", "title", SalesCase.SourceType.SYNTHETIC, null, null, null, null,
                "客户：价格高。销售：先比较成本。", SalesCase.Status.IMPORTED, null, 0, LocalDateTime.now(), LocalDateTime.now());
        when(cases.save(any())).thenReturn(saved); when(cases.findById(101L)).thenReturn(Optional.of(saved));
        when(cases.compareAndSetStatus(101L, SalesCase.Status.IMPORTED, SalesCase.Status.EXTRACTING, null)).thenReturn(true);
        var valid = new ExperienceExtractor.ExperienceDraft(ExperienceUnit.ScenarioType.OBJECTION_HANDLING,
                ExperienceUnit.ObjectionType.PRICE, null, null, "客户价格异议", "先比较成本", null, "客户：价格高", null);
        var invalid = new ExperienceExtractor.ExperienceDraft(null, null, null, null, "", "摘要", null, "不存在", null);
        when(extractor.extract(any())).thenReturn(new ExperienceExtractor.ExtractionBatch(List.of(valid, invalid)));
        var service = new SalesCaseApplicationService(cases, experiences, extractor, new ExperienceSchemaValidator(),
                new EvidenceGroundingValidator(), (Executor) Runnable::run);
        service.importCase(saved);
        var capture = org.mockito.ArgumentCaptor.forClass(ExperienceUnit.class);
        verify(experiences).save(capture.capture());
        assertThat(capture.getValue().reviewStatus()).isEqualTo(ExperienceUnit.ReviewStatus.GENERATED);
        assertThat(capture.getValue().indexStatus()).isEqualTo(ExperienceUnit.IndexStatus.NOT_INDEXED);
        assertThat(capture.getValue().promptVersion()).isEqualTo("experience-extract-v1");
        verify(cases).compareAndSetStatus(101L, SalesCase.Status.EXTRACTING, SalesCase.Status.EXTRACTED,
                "rejected=1; schema=1; evidence=0");
    }

    @Test void rejectsBatchOverFiveAsTaskFailure() {
        var cases = mock(com.salesmentor.salescase.domain.SalesCaseRepository.class); var experiences = mock(ExperienceRepository.class); var extractor = mock(ExperienceExtractor.class);
        var saved = new SalesCase(102L, "u2", "title", SalesCase.SourceType.SYNTHETIC, null, null, null, null, "content", SalesCase.Status.IMPORTED, null, 0, LocalDateTime.now(), LocalDateTime.now());
        when(cases.save(any())).thenReturn(saved); when(cases.findById(102L)).thenReturn(Optional.of(saved)); when(cases.compareAndSetStatus(102L, SalesCase.Status.IMPORTED, SalesCase.Status.EXTRACTING, null)).thenReturn(true);
        var d = new ExperienceExtractor.ExperienceDraft(ExperienceUnit.ScenarioType.DISCOVERY, null, null, null, "t", "s", null, "content", null);
        when(extractor.extract(any())).thenReturn(new ExperienceExtractor.ExtractionBatch(Collections.nCopies(6, d)));
        new SalesCaseApplicationService(cases, experiences, extractor, new ExperienceSchemaValidator(), new EvidenceGroundingValidator(), (Executor) Runnable::run).importCase(saved);
        verify(cases).compareAndSetStatus(eq(102L), eq(SalesCase.Status.EXTRACTING), eq(SalesCase.Status.EXTRACT_FAILED), contains("5"));
        verifyNoInteractions(experiences);
    }

    @Test void persistenceFailureMarksExtractionAsFailed() {
        var cases = mock(com.salesmentor.salescase.domain.SalesCaseRepository.class);
        var experiences = mock(ExperienceRepository.class);
        var extractor = mock(ExperienceExtractor.class);
        var saved = new SalesCase(103L, "u3", "title", SalesCase.SourceType.SYNTHETIC, null, null, null, null,
                "客户：价格高。销售：先比较成本。", SalesCase.Status.IMPORTED, null, 0, LocalDateTime.now(), LocalDateTime.now());
        when(cases.save(any())).thenReturn(saved);
        when(cases.findById(103L)).thenReturn(Optional.of(saved));
        when(cases.compareAndSetStatus(103L, SalesCase.Status.IMPORTED, SalesCase.Status.EXTRACTING, null)).thenReturn(true);
        var valid = new ExperienceExtractor.ExperienceDraft(ExperienceUnit.ScenarioType.OBJECTION_HANDLING,
                ExperienceUnit.ObjectionType.PRICE, null, null, "客户价格异议", "先比较成本", null, "客户：价格高", null);
        when(extractor.extract(any())).thenReturn(new ExperienceExtractor.ExtractionBatch(List.of(valid)));
        doThrow(new IllegalStateException("persistence failed")).when(experiences).save(any());

        new SalesCaseApplicationService(cases, experiences, extractor, new ExperienceSchemaValidator(),
                new EvidenceGroundingValidator(), (Executor) Runnable::run).importCase(saved);

        verify(cases).compareAndSetStatus(eq(103L), eq(SalesCase.Status.EXTRACTING),
                eq(SalesCase.Status.EXTRACT_FAILED), contains("persistence failed"));
        verify(cases, never()).compareAndSetStatus(eq(103L), eq(SalesCase.Status.EXTRACTING),
                eq(SalesCase.Status.EXTRACTED), any());
    }
}
