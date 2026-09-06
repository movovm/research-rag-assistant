package com.salesmentor.experience;
import com.salesmentor.experience.application.EvidenceGroundingValidator; import org.junit.jupiter.api.Test; import static org.assertj.core.api.Assertions.*;
class EvidenceGroundingValidatorTest { private final EvidenceGroundingValidator v=new EvidenceGroundingValidator();
 @Test void locatesChineseNewlineAndFirstDuplicate(){String c="客户：价格高\n销售：先比较总成本。客户：价格高";var e=v.evidence(c,"客户：价格高");assertThat(e.start()).isZero();assertThat(c.substring(e.start(),e.end())).isEqualTo("客户：价格高");}
 @Test void rejectsUngroundedQuote(){assertThatThrownBy(()->v.evidence("原文","不存在")).isInstanceOf(IllegalArgumentException.class);}
}
