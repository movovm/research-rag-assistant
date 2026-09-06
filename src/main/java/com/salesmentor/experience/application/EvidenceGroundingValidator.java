package com.salesmentor.experience.application;
import org.springframework.stereotype.Component;
@Component public class EvidenceGroundingValidator { public Evidence evidence(String c,String q){if(c==null||q==null||q.isBlank())throw new IllegalArgumentException("证据引用为空");int s=c.indexOf(q);if(s<0)throw new IllegalArgumentException("证据引用不在案例原文中");return new Evidence(s,s+q.length());} public record Evidence(int start,int end){} }
