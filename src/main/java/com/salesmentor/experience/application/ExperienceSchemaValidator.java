package com.salesmentor.experience.application;
import org.springframework.stereotype.Component;
@Component public class ExperienceSchemaValidator {
 public void validate(ExperienceExtractor.ExtractionBatch b){if(b==null||b.drafts().size()>5)throw new IllegalArgumentException("抽取结果最多包含 5 个经验单元");for(var d:b.drafts()){if(d==null||d.scenarioType()==null||blank(d.triggerText())||blank(d.strategySummary())||blank(d.evidenceQuote()))throw new IllegalArgumentException("经验单元缺少必填字段");check(d.triggerText(),500);check(d.strategySummary(),1000);check(d.recommendedQuestion(),500);check(d.evidenceQuote(),2000);check(d.applicability(),1000);check(d.customerRole(),64);}}
 private boolean blank(String s){return s==null||s.isBlank();} private void check(String s,int n){if(s!=null&&s.length()>n)throw new IllegalArgumentException("经验字段超过长度限制");}
}
