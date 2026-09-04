package com.salesmentor;

import com.salesmentor.core.ChatService;
import com.salesmentor.core.DocumentIngestionService;
import com.salesmentor.domain.ChatRequest;
import com.salesmentor.domain.ChatResult;
import com.salesmentor.experience.domain.ExperienceRepository;
import com.salesmentor.experience.domain.ExperienceUnit;
import com.salesmentor.knowledge.domain.KnowledgeDocument;
import com.salesmentor.knowledge.domain.KnowledgeRepository;
import com.salesmentor.review.domain.ReviewTask;
import com.salesmentor.review.domain.ReviewTaskRepository;
import com.salesmentor.salescase.domain.SalesCase;
import com.salesmentor.salescase.domain.SalesCaseRepository;
import com.salesmentor.trace.domain.AgentTrace;
import com.salesmentor.trace.domain.AgentTraceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class SalesMentorApplicationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("salesmentor")
            .withUsername("salesmentor")
            .withPassword("salesmentor");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private ChatService chatService;

    @Autowired
    private DocumentIngestionService ingestion;

    @Autowired
    private SalesCaseRepository salesCases;

    @Autowired
    private ExperienceRepository experiences;

    @Autowired
    private KnowledgeRepository knowledge;

    @Autowired
    private ReviewTaskRepository reviewTasks;

    @Autowired
    private AgentTraceRepository traces;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void completesEndToEndLocalRagFlow() {
        ingestion.ingestText("test-guide.md", "研发规范", "demo",
                "缓存穿透是请求不存在的数据。可以缓存空值，并给空值设置较短 TTL。布隆过滤器适用于大规模主键集合。");

        ChatResult result = chatService.chat(new ChatRequest("test-session", "test-user", "缓存穿透怎么处理？"));

        assertThat(result.answer()).contains("缓存");
        assertThat(result.evidence()).isNotEmpty();
        assertThat(result.stages()).contains("Query Rewrite", "BM25 + Dense 混合检索");
    }

    @Test
    void createsFiveBusinessTablesAndPersistsSalesCase() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name REGEXP '^sm_'
                """, Integer.class);
        assertThat(tableCount).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sm_sales_case", Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sm_experience_unit", Integer.class)).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sm_knowledge_document", Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sm_experience_unit experience
                JOIN sm_sales_case sales_case ON sales_case.id = experience.case_id
                WHERE SUBSTRING(sales_case.content, experience.evidence_start + 1,
                                experience.evidence_end - experience.evidence_start) <> experience.evidence_quote
                """, Integer.class)).isZero();

        LocalDateTime now = LocalDateTime.now().withNano(0);
        String conversation = "客户：价格太高。销售：我们先比较三年的总成本。";
        String quote = "我们先比较三年的总成本";
        SalesCase saved = salesCases.save(new SalesCase(null, "day1-case", "Day 1 migration case",
                SalesCase.SourceType.SYNTHETIC, null, "MANUFACTURING", SalesCase.SalesStage.NEGOTIATION,
                "PURCHASING_MANAGER", conversation,
                SalesCase.Status.IMPORTED, null, 0, now, now));

        assertThat(saved.id()).isNotNull();
        assertThat(salesCases.findById(saved.id()))
                .get()
                .extracting(SalesCase::externalKey, SalesCase::status)
                .containsExactly("day1-case", SalesCase.Status.IMPORTED);
        assertThat(salesCases.compareAndSetStatus(saved.id(), SalesCase.Status.IMPORTED,
                SalesCase.Status.EXTRACTING, null)).isTrue();

        ExperienceUnit experience = experiences.save(new ExperienceUnit(null, saved.id(),
                ExperienceUnit.ScenarioType.OBJECTION_HANDLING, ExperienceUnit.ObjectionType.PRICE,
                SalesCase.SalesStage.NEGOTIATION, "PURCHASING_MANAGER", "客户认为价格高",
                "先比较总成本", "当前三年总成本是多少？", quote, conversation.indexOf(quote),
                conversation.indexOf(quote) + quote.length(), "价格异议", "a".repeat(64),
                ExperienceUnit.ReviewStatus.GENERATED, ExperienceUnit.IndexStatus.NOT_INDEXED,
                null, "local", "experience-extract-v1", null, null, 0, now, now));
        assertThat(experiences.findByCaseId(saved.id())).extracting(ExperienceUnit::id).contains(experience.id());

        KnowledgeDocument document = knowledge.save(new KnowledgeDocument(null, "产品说明",
                KnowledgeDocument.DocumentType.PRODUCT_OVERVIEW, "test.md", "产品测试内容", "b".repeat(64),
                KnowledgeDocument.Status.DRAFT, KnowledgeDocument.IndexStatus.NOT_INDEXED,
                "product", now, now));
        assertThat(knowledge.findById(document.id())).isPresent();

        ReviewTask task = reviewTasks.save(new ReviewTask(null, "day1-request", null, "day1-session",
                "MANUFACTURING", SalesCase.SalesStage.NEGOTIATION, "PURCHASING_MANAGER", conversation,
                "验证持久化骨架", ReviewTask.Status.PENDING, null, null, null, null, null, now, now));
        assertThat(reviewTasks.compareAndSetStatus(task.id(), ReviewTask.Status.PENDING,
                ReviewTask.Status.RUNNING)).isTrue();

        AgentTrace trace = traces.save(new AgentTrace(null, task.id(), 1, AgentTrace.StepType.TASK,
                null, "{}", "task started", "[]", 0, AgentTrace.Status.SUCCEEDED, null, now));
        assertThat(traces.findByTaskId(task.id())).extracting(AgentTrace::id).containsExactly(trace.id());
    }
}
