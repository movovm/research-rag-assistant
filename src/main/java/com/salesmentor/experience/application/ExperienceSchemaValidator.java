package com.salesmentor.experience.application;

import org.springframework.stereotype.Component;

@Component
public class ExperienceSchemaValidator {
    public void validateBatch(ExperienceExtractor.ExtractionBatch batch) {
        if (batch == null || batch.drafts() == null || batch.drafts().size() > 5)
            throw new IllegalArgumentException("抽取结果必须包含 0 到 5 个经验单元");
    }
    public void validateDraft(ExperienceExtractor.ExperienceDraft draft) {
        if (draft == null || draft.scenarioType() == null || blank(draft.triggerText())
                || blank(draft.strategySummary()) || blank(draft.evidenceQuote()))
            throw new IllegalArgumentException("经验单元缺少必填字段");
        check(draft.triggerText(), 500); check(draft.strategySummary(), 1000);
        check(draft.recommendedQuestion(), 500); check(draft.evidenceQuote(), 2000);
        check(draft.applicability(), 1000); check(draft.customerRole(), 64);
    }
    public void validate(ExperienceExtractor.ExtractionBatch batch) {
        validateBatch(batch); batch.drafts().forEach(this::validateDraft);
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private void check(String value, int max) { if (value != null && value.length() > max) throw new IllegalArgumentException("经验字段超过长度限制"); }
}
