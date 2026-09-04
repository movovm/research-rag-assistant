package com.salesmentor.experience;
import com.salesmentor.experience.application.*; import com.salesmentor.experience.domain.ExperienceUnit; import org.junit.jupiter.api.Test; import java.util.*; import static org.assertj.core.api.Assertions.*;
class ExperienceSchemaValidatorTest { private final ExperienceSchemaValidator v=new ExperienceSchemaValidator();
 private ExperienceExtractor.ExperienceDraft draft(){return new ExperienceExtractor.ExperienceDraft(ExperienceUnit.ScenarioType.DISCOVERY,null,null,null,"触发","摘要",null,"证据",null);}
 @Test void acceptsValidAndRejectsMissingOrMoreThanFive(){v.validate(new ExperienceExtractor.ExtractionBatch(List.of(draft())));assertThatThrownBy(()->v.validate(new ExperienceExtractor.ExtractionBatch(Collections.nCopies(6,draft())))).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->v.validate(new ExperienceExtractor.ExtractionBatch(List.of(new ExperienceExtractor.ExperienceDraft(null,null,null,null,"","",null,"",null))))).isInstanceOf(IllegalArgumentException.class);}
}
