INSERT INTO sm_sales_case (
    id, external_key, title, source_type, source_uri, industry, sales_stage, customer_role,
    content, status, extract_error, version, created_at, updated_at
) VALUES
(1001, 'CASE-PRICE-001', '制造业采购价格异议', 'SYNTHETIC', NULL, 'MANUFACTURING', 'NEGOTIATION',
 'PURCHASING_MANAGER',
 '客户：你们的采购价格比竞品高。销售：如果只比较采购价确实更高，我们可以先比较三年的维护成本和停机损失。客户：我们更担心年度预算。销售：那我先拆分首年投入和后续运维费用。',
 'EXTRACTED', NULL, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(1002, 'CASE-COMPETITOR-001', '软件客户已有竞品方案', 'SYNTHETIC', NULL, 'SOFTWARE', 'DISCOVERY',
 'TECHNICAL_DIRECTOR',
 '客户：我们已经在使用另一家产品，没有更换计划。销售：您最希望现有方案改善的是稳定性、集成成本还是使用效率？客户：主要是接口维护成本。销售：我先了解目前需要维护多少套接口，再判断是否值得迁移。',
 'EXTRACTED', NULL, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(1003, 'CASE-IMPLEMENTATION-001', '客户担心部署风险', 'SYNTHETIC', NULL, 'FINANCE', 'PROPOSAL',
 'IT_MANAGER',
 '客户：上线会不会影响现有系统？销售：我们可以先列出必须保持不变的核心流程。客户：支付和对账不能停。销售：建议先在隔离环境验证接口，再安排低峰期灰度切换。',
 'EXTRACTED', NULL, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(1004, 'CASE-AUTHORITY-001', '沟通对象不是最终决策人', 'SYNTHETIC', NULL, 'RETAIL', 'PROPOSAL',
 'BUSINESS_USER',
 '客户：方案我觉得可以，但还要领导决定。销售：为了让内部评估更顺利，领导最关心预算、风险还是交付周期？客户：预算和回收周期。销售：我整理一页预算与回收周期说明，您也可以邀请领导参加下一次评审。',
 'EXTRACTED', NULL, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(1005, 'CASE-TIMING-001', '客户希望推迟采购', 'SYNTHETIC', NULL, 'LOGISTICS', 'NEGOTIATION',
 'OPERATIONS_MANAGER',
 '客户：这个项目先放到明年再说。销售：是当前优先级不高，还是预算和实施资源暂时不足？客户：主要是实施团队排期满了。销售：我们可以先确认准备清单和最小试点范围，等资源释放后再启动。',
 'EXTRACTED', NULL, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT INTO sm_experience_unit (
    id, case_id, scenario_type, objection_type, sales_stage, customer_role, trigger_text,
    strategy_summary, recommended_question, evidence_quote, evidence_start, evidence_end,
    applicability, content_hash, review_status, index_status, vector_ref, extraction_model,
    prompt_version, reviewed_by, reviewed_at, version, created_at, updated_at
)
SELECT 2001, id, 'OBJECTION_HANDLING', 'PRICE', 'NEGOTIATION', customer_role,
       '客户认为采购价格高于竞品', '先承认采购价差异，再把比较口径扩展到维护成本和停机损失',
       '目前三年的维护成本和停机损失大约是多少？',
       '如果只比较采购价确实更高，我们可以先比较三年的维护成本和停机损失',
       LOCATE('如果只比较采购价确实更高，我们可以先比较三年的维护成本和停机损失', content) - 1,
       LOCATE('如果只比较采购价确实更高，我们可以先比较三年的维护成本和停机损失', content) - 1
           + CHAR_LENGTH('如果只比较采购价确实更高，我们可以先比较三年的维护成本和停机损失'),
       '适用于客户只比较采购价格的场景', SHA2(CONCAT(id, ':price:tco'), 256),
       'PUBLISHED', 'INDEXED', 'experience:2001', 'seed-fixture', 'seed-v1', 0,
       CURRENT_TIMESTAMP(3), 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM sm_sales_case WHERE id = 1001;

INSERT INTO sm_experience_unit (
    id, case_id, scenario_type, objection_type, sales_stage, customer_role, trigger_text,
    strategy_summary, recommended_question, evidence_quote, evidence_start, evidence_end,
    applicability, content_hash, review_status, index_status, vector_ref, extraction_model,
    prompt_version, reviewed_by, reviewed_at, version, created_at, updated_at
)
SELECT 2002, id, 'VALUE_COMMUNICATION', 'PRICE', 'NEGOTIATION', customer_role,
       '客户进一步说明年度预算压力', '把总成本说明拆成首年投入和后续运维费用',
       '您希望首年投入控制在什么范围？', '那我先拆分首年投入和后续运维费用',
       LOCATE('那我先拆分首年投入和后续运维费用', content) - 1,
       LOCATE('那我先拆分首年投入和后续运维费用', content) - 1 + CHAR_LENGTH('那我先拆分首年投入和后续运维费用'),
       '适用于总成本认可但年度预算受限的场景', SHA2(CONCAT(id, ':price:budget'), 256),
       'PUBLISHED', 'INDEXED', 'experience:2002', 'seed-fixture', 'seed-v1', 0,
       CURRENT_TIMESTAMP(3), 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM sm_sales_case WHERE id = 1001;

INSERT INTO sm_experience_unit (
    id, case_id, scenario_type, objection_type, sales_stage, customer_role, trigger_text,
    strategy_summary, recommended_question, evidence_quote, evidence_start, evidence_end,
    applicability, content_hash, review_status, index_status, vector_ref, extraction_model,
    prompt_version, reviewed_by, reviewed_at, version, created_at, updated_at
)
SELECT 2003, id, 'DISCOVERY', 'COMPETITOR', 'DISCOVERY', customer_role,
       '客户已有竞品且没有更换计划', '不立即反驳竞品，先询问现有方案最需要改善的维度',
       '您最希望现有方案改善的是稳定性、集成成本还是使用效率？',
       '您最希望现有方案改善的是稳定性、集成成本还是使用效率？',
       LOCATE('您最希望现有方案改善的是稳定性、集成成本还是使用效率？', content) - 1,
       LOCATE('您最希望现有方案改善的是稳定性、集成成本还是使用效率？', content) - 1
           + CHAR_LENGTH('您最希望现有方案改善的是稳定性、集成成本还是使用效率？'),
       '适用于客户对现有供应商总体满意但可能存在局部痛点的场景', SHA2(CONCAT(id, ':competitor:discovery'), 256),
       'PUBLISHED', 'INDEXED', 'experience:2003', 'seed-fixture', 'seed-v1', 0,
       CURRENT_TIMESTAMP(3), 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM sm_sales_case WHERE id = 1002;

INSERT INTO sm_experience_unit (
    id, case_id, scenario_type, objection_type, sales_stage, customer_role, trigger_text,
    strategy_summary, recommended_question, evidence_quote, evidence_start, evidence_end,
    applicability, content_hash, review_status, index_status, vector_ref, extraction_model,
    prompt_version, reviewed_by, reviewed_at, version, created_at, updated_at
)
SELECT 2004, id, 'NEED_CONFIRMATION', 'COMPETITOR', 'DISCOVERY', customer_role,
       '客户指出接口维护成本问题', '先量化当前接口数量，再判断迁移价值',
       '目前需要维护多少套接口？', '我先了解目前需要维护多少套接口，再判断是否值得迁移',
       LOCATE('我先了解目前需要维护多少套接口，再判断是否值得迁移', content) - 1,
       LOCATE('我先了解目前需要维护多少套接口，再判断是否值得迁移', content) - 1
           + CHAR_LENGTH('我先了解目前需要维护多少套接口，再判断是否值得迁移'),
       '适用于痛点已出现但尚未量化的场景', SHA2(CONCAT(id, ':competitor:quantify'), 256),
       'PUBLISHED', 'INDEXED', 'experience:2004', 'seed-fixture', 'seed-v1', 0,
       CURRENT_TIMESTAMP(3), 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM sm_sales_case WHERE id = 1002;

INSERT INTO sm_experience_unit (
    id, case_id, scenario_type, objection_type, sales_stage, customer_role, trigger_text,
    strategy_summary, recommended_question, evidence_quote, evidence_start, evidence_end,
    applicability, content_hash, review_status, index_status, vector_ref, extraction_model,
    prompt_version, reviewed_by, reviewed_at, version, created_at, updated_at
)
SELECT 2005, id, 'DISCOVERY', 'IMPLEMENTATION', 'PROPOSAL', customer_role,
       '客户担心上线影响现有系统', '先确认上线期间必须保持不变的核心流程',
       '上线期间哪些核心流程绝对不能中断？', '我们可以先列出必须保持不变的核心流程',
       LOCATE('我们可以先列出必须保持不变的核心流程', content) - 1,
       LOCATE('我们可以先列出必须保持不变的核心流程', content) - 1 + CHAR_LENGTH('我们可以先列出必须保持不变的核心流程'),
       '适用于客户提出笼统实施风险的场景', SHA2(CONCAT(id, ':implementation:constraints'), 256),
       'PUBLISHED', 'INDEXED', 'experience:2005', 'seed-fixture', 'seed-v1', 0,
       CURRENT_TIMESTAMP(3), 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM sm_sales_case WHERE id = 1003;

INSERT INTO sm_experience_unit (
    id, case_id, scenario_type, objection_type, sales_stage, customer_role, trigger_text,
    strategy_summary, recommended_question, evidence_quote, evidence_start, evidence_end,
    applicability, content_hash, review_status, index_status, vector_ref, extraction_model,
    prompt_version, reviewed_by, reviewed_at, version, created_at, updated_at
)
SELECT 2006, id, 'VALUE_COMMUNICATION', 'IMPLEMENTATION', 'PROPOSAL', customer_role,
       '客户明确支付和对账不能停', '提出隔离环境验证和低峰期灰度切换方案',
       '可以先选择哪一组非核心接口进行隔离验证？', '建议先在隔离环境验证接口，再安排低峰期灰度切换',
       LOCATE('建议先在隔离环境验证接口，再安排低峰期灰度切换', content) - 1,
       LOCATE('建议先在隔离环境验证接口，再安排低峰期灰度切换', content) - 1
           + CHAR_LENGTH('建议先在隔离环境验证接口，再安排低峰期灰度切换'),
       '适用于已明确不可中断流程后的部署讨论', SHA2(CONCAT(id, ':implementation:pilot'), 256),
       'PUBLISHED', 'INDEXED', 'experience:2006', 'seed-fixture', 'seed-v1', 0,
       CURRENT_TIMESTAMP(3), 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM sm_sales_case WHERE id = 1003;

INSERT INTO sm_experience_unit (
    id, case_id, scenario_type, objection_type, sales_stage, customer_role, trigger_text,
    strategy_summary, recommended_question, evidence_quote, evidence_start, evidence_end,
    applicability, content_hash, review_status, index_status, vector_ref, extraction_model,
    prompt_version, reviewed_by, reviewed_at, version, created_at, updated_at
)
SELECT 2007, id, 'DISCOVERY', 'AUTHORITY', 'PROPOSAL', customer_role,
       '当前沟通对象不是最终决策人', '询问最终决策人关注的评估维度',
       '领导最关心预算、风险还是交付周期？', '领导最关心预算、风险还是交付周期？',
       LOCATE('领导最关心预算、风险还是交付周期？', content) - 1,
       LOCATE('领导最关心预算、风险还是交付周期？', content) - 1 + CHAR_LENGTH('领导最关心预算、风险还是交付周期？'),
       '适用于支持者需要向上汇报的场景', SHA2(CONCAT(id, ':authority:criteria'), 256),
       'PUBLISHED', 'INDEXED', 'experience:2007', 'seed-fixture', 'seed-v1', 0,
       CURRENT_TIMESTAMP(3), 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM sm_sales_case WHERE id = 1004;

INSERT INTO sm_experience_unit (
    id, case_id, scenario_type, objection_type, sales_stage, customer_role, trigger_text,
    strategy_summary, recommended_question, evidence_quote, evidence_start, evidence_end,
    applicability, content_hash, review_status, index_status, vector_ref, extraction_model,
    prompt_version, reviewed_by, reviewed_at, version, created_at, updated_at
)
SELECT 2008, id, 'FOLLOW_UP', 'AUTHORITY', 'PROPOSAL', customer_role,
       '客户说明领导关注预算和回收周期', '准备简短决策材料并建议邀请决策人参加评审',
       '下一次评审是否方便邀请最终决策人参加？',
       '我整理一页预算与回收周期说明，您也可以邀请领导参加下一次评审',
       LOCATE('我整理一页预算与回收周期说明，您也可以邀请领导参加下一次评审', content) - 1,
       LOCATE('我整理一页预算与回收周期说明，您也可以邀请领导参加下一次评审', content) - 1
           + CHAR_LENGTH('我整理一页预算与回收周期说明，您也可以邀请领导参加下一次评审'),
       '适用于已确认决策标准且需要推进多人评审的场景', SHA2(CONCAT(id, ':authority:followup'), 256),
       'PUBLISHED', 'INDEXED', 'experience:2008', 'seed-fixture', 'seed-v1', 0,
       CURRENT_TIMESTAMP(3), 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM sm_sales_case WHERE id = 1004;

INSERT INTO sm_experience_unit (
    id, case_id, scenario_type, objection_type, sales_stage, customer_role, trigger_text,
    strategy_summary, recommended_question, evidence_quote, evidence_start, evidence_end,
    applicability, content_hash, review_status, index_status, vector_ref, extraction_model,
    prompt_version, reviewed_by, reviewed_at, version, created_at, updated_at
)
SELECT 2009, id, 'DISCOVERY', 'TIMING', 'NEGOTIATION', customer_role,
       '客户希望把项目推迟到明年', '区分优先级、预算和实施资源三类原因',
       '是当前优先级不高，还是预算和实施资源暂时不足？',
       '是当前优先级不高，还是预算和实施资源暂时不足？',
       LOCATE('是当前优先级不高，还是预算和实施资源暂时不足？', content) - 1,
       LOCATE('是当前优先级不高，还是预算和实施资源暂时不足？', content) - 1
           + CHAR_LENGTH('是当前优先级不高，还是预算和实施资源暂时不足？'),
       '适用于客户只给出模糊延期理由的场景', SHA2(CONCAT(id, ':timing:reason'), 256),
       'PUBLISHED', 'INDEXED', 'experience:2009', 'seed-fixture', 'seed-v1', 0,
       CURRENT_TIMESTAMP(3), 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM sm_sales_case WHERE id = 1005;

INSERT INTO sm_experience_unit (
    id, case_id, scenario_type, objection_type, sales_stage, customer_role, trigger_text,
    strategy_summary, recommended_question, evidence_quote, evidence_start, evidence_end,
    applicability, content_hash, review_status, index_status, vector_ref, extraction_model,
    prompt_version, reviewed_by, reviewed_at, version, created_at, updated_at
)
SELECT 2010, id, 'FOLLOW_UP', 'TIMING', 'NEGOTIATION', customer_role,
       '客户说明实施团队排期不足', '先确认准备清单和最小试点范围，等待资源窗口',
       '当前可以先确认哪些准备项和最小试点范围？',
       '我们可以先确认准备清单和最小试点范围，等资源释放后再启动',
       LOCATE('我们可以先确认准备清单和最小试点范围，等资源释放后再启动', content) - 1,
       LOCATE('我们可以先确认准备清单和最小试点范围，等资源释放后再启动', content) - 1
           + CHAR_LENGTH('我们可以先确认准备清单和最小试点范围，等资源释放后再启动'),
       '适用于需求存在但实施资源受限的场景', SHA2(CONCAT(id, ':timing:pilot'), 256),
       'PUBLISHED', 'INDEXED', 'experience:2010', 'seed-fixture', 'seed-v1', 0,
       CURRENT_TIMESTAMP(3), 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM sm_sales_case WHERE id = 1005;

INSERT INTO sm_knowledge_document (
    id, title, document_type, source_name, content, content_hash, status, index_status,
    vector_namespace, created_at, updated_at
) VALUES
(3001, 'SalesMentor 产品概览', 'PRODUCT_OVERVIEW', 'synthetic-product-overview.md',
 'SalesMentor 提供销售案例结构化、经验人工核验、业务元数据检索和带证据的沟通复盘。系统不预测成交概率，也不替代销售负责人判断。',
 SHA2('product-overview-v1', 256), 'PUBLISHED', 'INDEXED', 'product', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(3002, 'SalesMentor 价格说明', 'PRICING_NOTE', 'synthetic-pricing-note.md',
 '演示产品采用按用户数订阅的模拟定价。正式报价需要结合用户规模、部署方式和支持范围确认，知识库中的数字不得视为真实商业报价。',
 SHA2('pricing-note-v1', 256), 'PUBLISHED', 'INDEXED', 'product', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(3003, 'SalesMentor 部署说明', 'DEPLOYMENT_GUIDE', 'synthetic-deployment-guide.md',
 '部署建议先在隔离环境完成接口验证，再选择低风险团队灰度使用。上线计划需要明确回滚方式、数据范围和负责人。',
 SHA2('deployment-guide-v1', 256), 'PUBLISHED', 'INDEXED', 'product', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));
