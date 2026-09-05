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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
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

    @Autowired private TestRestTemplate rest;

    @Test
    void importsAndExtractsGroundedGeneratedExperience() throws Exception {
        String body = "{\"externalKey\":\"day2-api-case\",\"title\":\"价格异议\",\"sourceType\":\"SYNTHETIC\",\"salesStage\":\"NEGOTIATION\",\"content\":\"客户：价格太高。销售：我们先比较三年的总成本。\"}";
        ResponseEntity<String> response = rest.postForEntity("/api/v1/cases", new HttpEntity<>(body,
                new HttpHeaders() {{ setContentType(MediaType.APPLICATION_JSON); }}), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Long id = Long.valueOf(response.getBody().replaceAll(".*\\\"caseId\\\":(\\d+).*", "$1"));
        SalesCase value = null;
        for (int i = 0; i < 100; i++) { value = salesCases.findById(id).orElseThrow(); if (value.status() == SalesCase.Status.EXTRACTED) break; Thread.sleep(25); }
        assertThat(value.status()).isEqualTo(SalesCase.Status.EXTRACTED);
        String content = value.content();
        ResponseEntity<List<ExperienceUnit>> experiencesResponse = rest.exchange(
                "/api/v1/cases/{id}/experiences", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}, id);
        assertThat(experiencesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(experiencesResponse.getBody()).isNotNull();
        assertThat(experiencesResponse.getBody()).isNotEmpty();
        assertThat(experiencesResponse.getBody()).allSatisfy(e -> {
            assertThat(e.caseId()).isEqualTo(id);
            assertThat(e.reviewStatus()).isEqualTo(ExperienceUnit.ReviewStatus.GENERATED);
            assertThat(e.indexStatus()).isEqualTo(ExperienceUnit.IndexStatus.NOT_INDEXED);
            assertThat(content.substring(e.evidenceStart(), e.evidenceEnd())).isEqualTo(e.evidenceQuote());
        });
    }

    @Test
    void getsCaseByIdWithPersistedStatus() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        SalesCase saved = salesCases.save(new SalesCase(null, "get-case-" + System.nanoTime(), "case-query",
                SalesCase.SourceType.SYNTHETIC, null, "MANUFACTURING", SalesCase.SalesStage.DISCOVERY,
                "PURCHASING_MANAGER", "customer needs", SalesCase.Status.IMPORTED, null, 0, now, now));

        ResponseEntity<SalesCase> response = rest.getForEntity("/api/v1/cases/{id}", SalesCase.class, saved.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(saved.id());
        assertThat(response.getBody().status()).isEqualTo(saved.status());
    }

    @Test
    void failedCaseCanBeRetriedThroughApi() throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        SalesCase saved = salesCases.save(new SalesCase(null, "retry-api-" + System.nanoTime(), "retry-case",
                SalesCase.SourceType.SYNTHETIC, null, "MANUFACTURING", SalesCase.SalesStage.NEGOTIATION,
                "PURCHASING_MANAGER", "Customer says price is high. Sales compares total cost.",
                SalesCase.Status.EXTRACT_FAILED, "previous failure", 0, now, now));

        ResponseEntity<Void> retry = rest.postForEntity("/api/v1/cases/{id}/extraction:retry", null,
                Void.class, saved.id());
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        SalesCase value = null;
        for (int i = 0; i < 100; i++) {
            value = salesCases.findById(saved.id()).orElseThrow();
            if (value.status() == SalesCase.Status.EXTRACTED) break;
            Thread.sleep(25);
        }
        assertThat(value.status()).isEqualTo(SalesCase.Status.EXTRACTED);

        ResponseEntity<List<ExperienceUnit>> response = rest.exchange(
                "/api/v1/cases/{id}/experiences", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}, saved.id());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().isNotEmpty().allSatisfy(unit -> {
            assertThat(unit.caseId()).isEqualTo(saved.id());
            assertThat(unit.reviewStatus()).isEqualTo(ExperienceUnit.ReviewStatus.GENERATED);
            assertThat(unit.indexStatus()).isEqualTo(ExperienceUnit.IndexStatus.NOT_INDEXED);
        });
    }

    @Test
    void nonFailedCaseRetryIsRejectedThroughApi() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        SalesCase saved = salesCases.save(new SalesCase(null, "retry-rejected-" + System.nanoTime(), "retry-case",
                SalesCase.SourceType.SYNTHETIC, null, "MANUFACTURING", SalesCase.SalesStage.DISCOVERY,
                "PURCHASING_MANAGER", "customer needs", SalesCase.Status.IMPORTED, null, 0, now, now));

        ResponseEntity<String> response = rest.postForEntity("/api/v1/cases/{id}/extraction:retry", null,
                String.class, saved.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("CASE_STATE_CONFLICT");
        assertThat(salesCases.findById(saved.id())).get()
                .extracting(SalesCase::status).isEqualTo(SalesCase.Status.IMPORTED);
        assertThat(experiences.findByCaseId(saved.id())).isEmpty();
    }

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
        List<String> businessTables = jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name REGEXP '^sm_'
                ORDER BY table_name
                """, String.class);
        assertThat(businessTables).containsExactly(
                "sm_agent_trace",
                "sm_experience_unit",
                "sm_knowledge_document",
                "sm_review_task",
                "sm_sales_case");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sm_sales_case
                WHERE id BETWEEN 1001 AND 1005
                """, Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sm_experience_unit
                WHERE id BETWEEN 2001 AND 2010
                """, Integer.class)).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sm_knowledge_document
                WHERE id BETWEEN 3001 AND 3003
                """, Integer.class)).isEqualTo(3);
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
        assertThat(salesCases.compareAndSetStatus(saved.id(), SalesCase.Status.IMPORTED,
                SalesCase.Status.EXTRACTING, null)).isFalse();
        assertThat(salesCases.findById(saved.id()))
                .get()
                .extracting(SalesCase::status, SalesCase::version)
                .containsExactly(SalesCase.Status.EXTRACTING, 1);

        ExperienceUnit experience = experiences.save(new ExperienceUnit(null, saved.id(),
                ExperienceUnit.ScenarioType.OBJECTION_HANDLING, ExperienceUnit.ObjectionType.PRICE,
                SalesCase.SalesStage.NEGOTIATION, "PURCHASING_MANAGER", "客户认为价格高",
                "先比较总成本", "当前三年总成本是多少？", quote, conversation.indexOf(quote),
                conversation.indexOf(quote) + quote.length(), "价格异议", "a".repeat(64),
                ExperienceUnit.ReviewStatus.GENERATED, ExperienceUnit.IndexStatus.NOT_INDEXED,
                null, "local", "experience-extract-v1", null, null, 0, now, now));
        assertThat(experiences.findByCaseId(saved.id())).extracting(ExperienceUnit::id).contains(experience.id());
        assertThat(experiences.compareAndSetReviewStatus(experience.id(), ExperienceUnit.ReviewStatus.GENERATED,
                ExperienceUnit.ReviewStatus.VERIFIED, 0)).isTrue();
        assertThat(experiences.compareAndSetReviewStatus(experience.id(), ExperienceUnit.ReviewStatus.GENERATED,
                ExperienceUnit.ReviewStatus.PUBLISHED, 0)).isFalse();

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
        assertThat(reviewTasks.compareAndSetStatus(task.id(), ReviewTask.Status.PENDING,
                ReviewTask.Status.RUNNING)).isFalse();

        AgentTrace trace = traces.save(new AgentTrace(null, task.id(), 1, AgentTrace.StepType.TASK,
                null, "{}", "task started", "[]", 0, AgentTrace.Status.SUCCEEDED, null, now));
        assertThat(traces.findByTaskId(task.id())).extracting(AgentTrace::id).containsExactly(trace.id());
    }

    @Test
    void concurrentCasOnImportedCaseHasOneWinner() throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        SalesCase saved = salesCases.save(new SalesCase(null, "cas-concurrency-" + System.nanoTime(), "CAS concurrency",
                SalesCase.SourceType.SYNTHETIC, null, null, null, null, "content",
                SalesCase.Status.IMPORTED, null, 0, now, now));
        ExecutorService callers = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(3);
        try {
            Callable<Boolean> attempt = () -> { barrier.await(); return salesCases.compareAndSetStatus(saved.id(),
                    SalesCase.Status.IMPORTED, SalesCase.Status.EXTRACTING, null); };
            Future<Boolean> first = callers.submit(attempt);
            Future<Boolean> second = callers.submit(attempt);
            barrier.await();
            assertThat(List.of(first.get(1, TimeUnit.SECONDS), second.get(1, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(salesCases.findById(saved.id())).get()
                    .extracting(SalesCase::status).isEqualTo(SalesCase.Status.EXTRACTING);
        } finally {
            callers.shutdownNow();
        }
    }
}
