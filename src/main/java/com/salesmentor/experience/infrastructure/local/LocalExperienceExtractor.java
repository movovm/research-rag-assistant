package com.salesmentor.experience.infrastructure.local;

import com.salesmentor.experience.application.ExperienceExtractor;
import com.salesmentor.experience.domain.ExperienceUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.rag", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalExperienceExtractor implements ExperienceExtractor {
    @Override public ExtractionBatch extract(ExtractionCommand command) {
        String content = command.content();
        String quote = firstEvidence(content);
        if (quote == null) return new ExtractionBatch(List.of());
        return new ExtractionBatch(List.of(new ExperienceDraft(
                ExperienceUnit.ScenarioType.OBJECTION_HANDLING, inferObjection(content), command.salesStage(),
                command.customerRole(), "案例中出现客户异议或销售回应", "记录案例中可观察的回应动作",
                "您最关注哪一项具体约束？", quote, "仅表示该动作出现在案例原文中")));
    }
    private String firstEvidence(String content) {
        for (String part : content.split("[。！？\\n]")) if (!part.isBlank() && (part.contains("客户") || part.contains("销售"))) return part.trim();
        return content == null || content.isBlank() ? null : content.substring(0, Math.min(content.length(), 80));
    }
    private ExperienceUnit.ObjectionType inferObjection(String content) {
        if (content.contains("价格") || content.contains("成本")) return ExperienceUnit.ObjectionType.PRICE;
        if (content.contains("竞品") || content.contains("竞争")) return ExperienceUnit.ObjectionType.COMPETITOR;
        if (content.contains("部署") || content.contains("实施")) return ExperienceUnit.ObjectionType.IMPLEMENTATION;
        if (content.contains("时间") || content.contains("推迟")) return ExperienceUnit.ObjectionType.TIMING;
        return ExperienceUnit.ObjectionType.OTHER;
    }
}
