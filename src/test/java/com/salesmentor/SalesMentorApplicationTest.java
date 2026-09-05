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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(experiences.completeReview(experience.id(), ExperienceUnit.ReviewStatus.VERIFIED,
                1L, now, 0)).isTrue();
        assertThat(experiences.completeReview(experience.id(), ExperienceUnit.ReviewStatus.REJECTED,
                2L, now, 0)).isFalse();

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

    @Test
    void experienceStateCasPersistsReviewAndPublishFields() {
        ExperienceUnit generated = saveGeneratedExperience("experience-state-" + System.nanoTime());
        LocalDateTime reviewedAt = LocalDateTime.now().withNano(0);

        assertThatThrownBy(() -> experiences.completeReview(generated.id(), ExperienceUnit.ReviewStatus.VERIFIED,
                null, reviewedAt, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> experiences.completeReview(generated.id(), ExperienceUnit.ReviewStatus.VERIFIED,
                42L, null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(experiences.findById(generated.id())).get()
                .extracting(ExperienceUnit::reviewStatus, ExperienceUnit::version)
                .containsExactly(ExperienceUnit.ReviewStatus.GENERATED, 0);

        assertThat(experiences.completePublishing(generated.id(), "vector-before-claim", 0)).isFalse();
        assertThat(experiences.findById(generated.id())).get()
                .extracting(ExperienceUnit::reviewStatus, ExperienceUnit::indexStatus, ExperienceUnit::version)
                .containsExactly(ExperienceUnit.ReviewStatus.GENERATED, ExperienceUnit.IndexStatus.NOT_INDEXED, 0);

        assertThat(experiences.completeReview(generated.id(), ExperienceUnit.ReviewStatus.VERIFIED,
                42L, reviewedAt, 0)).isTrue();
        ExperienceUnit verified = experiences.findById(generated.id()).orElseThrow();
        assertThat(verified.reviewStatus()).isEqualTo(ExperienceUnit.ReviewStatus.VERIFIED);
        assertThat(verified.reviewedBy()).isEqualTo(42L);
        assertThat(verified.reviewedAt()).isEqualTo(reviewedAt);
        assertThat(verified.version()).isEqualTo(1);
        assertThat(experiences.completeReview(generated.id(), ExperienceUnit.ReviewStatus.REJECTED,
                43L, reviewedAt, 1)).isFalse();

        assertThat(experiences.claimIndexing(generated.id(), 1)).isTrue();
        assertThat(experiences.markIndexFailed(generated.id(), 2)).isTrue();
        assertThat(experiences.claimIndexing(generated.id(), 3)).isTrue();
        assertThatThrownBy(() -> experiences.completePublishing(generated.id(), null, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> experiences.completePublishing(generated.id(), "", 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> experiences.completePublishing(generated.id(), "   ", 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(experiences.findById(generated.id())).get()
                .extracting(ExperienceUnit::reviewStatus, ExperienceUnit::indexStatus,
                        ExperienceUnit::vectorRef, ExperienceUnit::version)
                .containsExactly(ExperienceUnit.ReviewStatus.VERIFIED, ExperienceUnit.IndexStatus.INDEXING,
                        null, 4);
        assertThat(experiences.completePublishing(generated.id(), "experience-vector-" + generated.id(), 4)).isTrue();

        ExperienceUnit published = experiences.findById(generated.id()).orElseThrow();
        assertThat(published.reviewStatus()).isEqualTo(ExperienceUnit.ReviewStatus.PUBLISHED);
        assertThat(published.indexStatus()).isEqualTo(ExperienceUnit.IndexStatus.INDEXED);
        assertThat(published.vectorRef()).isEqualTo("experience-vector-" + generated.id());
        assertThat(published.version()).isEqualTo(5);
        assertThat(experiences.markIndexFailed(generated.id(), 5)).isFalse();

        ExperienceUnit reviewable = saveGeneratedExperience("experience-reject-" + System.nanoTime());
        assertThat(experiences.completeReview(reviewable.id(), ExperienceUnit.ReviewStatus.REJECTED,
                43L, reviewedAt, 0)).isTrue();
        assertThat(experiences.findById(reviewable.id())).get()
                .extracting(ExperienceUnit::reviewStatus, ExperienceUnit::reviewedBy,
                        ExperienceUnit::reviewedAt, ExperienceUnit::version)
                .containsExactly(ExperienceUnit.ReviewStatus.REJECTED, 43L, reviewedAt, 1);
        assertThat(experiences.claimIndexing(reviewable.id(), 1)).isFalse();
    }

    @Test
    void concurrentExperienceIndexClaimHasOneWinner() throws Exception {
        ExperienceUnit generated = saveGeneratedExperience("experience-index-cas-" + System.nanoTime());
        assertThat(experiences.completeReview(generated.id(), ExperienceUnit.ReviewStatus.VERIFIED,
                42L, LocalDateTime.now().withNano(0), 0)).isTrue();

        ExecutorService callers = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(3);
        try {
            Callable<Boolean> claim = () -> {
                barrier.await();
                return experiences.claimIndexing(generated.id(), 1);
            };
            Future<Boolean> first = callers.submit(claim);
            Future<Boolean> second = callers.submit(claim);
            barrier.await();

            assertThat(List.of(first.get(1, TimeUnit.SECONDS), second.get(1, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(experiences.findById(generated.id())).get()
                    .extracting(ExperienceUnit::reviewStatus, ExperienceUnit::indexStatus, ExperienceUnit::version)
                    .containsExactly(ExperienceUnit.ReviewStatus.VERIFIED, ExperienceUnit.IndexStatus.INDEXING, 2);
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    void concurrentExperienceReviewHasOneWinner() throws Exception {
        ExperienceUnit generated = saveGeneratedExperience("review-cas-" + System.nanoTime());
        ExecutorService callers = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(3);
        try {
            Callable<Boolean> verify = () -> {
                barrier.await();
                return experiences.completeReview(generated.id(), ExperienceUnit.ReviewStatus.VERIFIED,
                        42L, LocalDateTime.now().withNano(0), 0);
            };
            Callable<Boolean> reject = () -> {
                barrier.await();
                return experiences.completeReview(generated.id(), ExperienceUnit.ReviewStatus.REJECTED,
                        43L, LocalDateTime.now().withNano(0), 0);
            };
            Future<Boolean> verified = callers.submit(verify);
            Future<Boolean> rejected = callers.submit(reject);
            barrier.await();

            assertThat(List.of(verified.get(1, TimeUnit.SECONDS), rejected.get(1, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            ExperienceUnit reviewed = experiences.findById(generated.id()).orElseThrow();
            assertThat(reviewed.reviewStatus()).isIn(ExperienceUnit.ReviewStatus.VERIFIED,
                    ExperienceUnit.ReviewStatus.REJECTED);
            assertThat(reviewed.reviewedBy()).isEqualTo(
                    reviewed.reviewStatus() == ExperienceUnit.ReviewStatus.VERIFIED ? 42L : 43L);
            assertThat(reviewed.reviewedAt()).isNotNull();
            assertThat(reviewed.indexStatus()).isEqualTo(ExperienceUnit.IndexStatus.NOT_INDEXED);
            assertThat(reviewed.version()).isEqualTo(1);
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    void verifiesGeneratedExperienceThroughApi() {
        ExperienceUnit generated = saveGeneratedExperience("review-verify-" + System.nanoTime());

        ResponseEntity<ExperienceUnit> response = rest.postForEntity(
                "/api/v1/experiences/{id}/review:verify", reviewRequest("{\"reviewedBy\":42}"),
                ExperienceUnit.class, generated.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().reviewStatus()).isEqualTo(ExperienceUnit.ReviewStatus.VERIFIED);
        assertThat(response.getBody().reviewedBy()).isEqualTo(42L);
        assertThat(response.getBody().reviewedAt()).isNotNull();
        assertThat(response.getBody().indexStatus()).isEqualTo(ExperienceUnit.IndexStatus.NOT_INDEXED);
    }

    @Test
    void rejectsGeneratedExperienceThroughApi() {
        ExperienceUnit generated = saveGeneratedExperience("review-reject-" + System.nanoTime());

        ResponseEntity<ExperienceUnit> response = rest.postForEntity(
                "/api/v1/experiences/{id}/review:reject", reviewRequest("{\"reviewedBy\":43}"),
                ExperienceUnit.class, generated.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().reviewStatus()).isEqualTo(ExperienceUnit.ReviewStatus.REJECTED);
        assertThat(response.getBody().reviewedBy()).isEqualTo(43L);
        assertThat(response.getBody().reviewedAt()).isNotNull();
        assertThat(response.getBody().indexStatus()).isEqualTo(ExperienceUnit.IndexStatus.NOT_INDEXED);
    }

    @Test
    void rejectsRepeatExperienceReviewThroughApiWithoutOverwritingMetadata() {
        ExperienceUnit generated = saveGeneratedExperience("review-conflict-" + System.nanoTime());
        ResponseEntity<ExperienceUnit> verified = rest.postForEntity(
                "/api/v1/experiences/{id}/review:verify", reviewRequest("{\"reviewedBy\":42}"),
                ExperienceUnit.class, generated.id());
        ExperienceUnit before = verified.getBody();

        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/experiences/{id}/review:reject", reviewRequest("{\"reviewedBy\":43}"),
                String.class, generated.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("EXPERIENCE_STATE_CONFLICT");
        assertThat(before).isNotNull();
        assertThat(experiences.findById(generated.id())).get()
                .extracting(ExperienceUnit::reviewStatus, ExperienceUnit::reviewedBy,
                        ExperienceUnit::reviewedAt, ExperienceUnit::version)
                .containsExactly(before.reviewStatus(), before.reviewedBy(), before.reviewedAt(), before.version());
    }

    @Test
    void returnsNotFoundForUnknownExperienceReview() {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/experiences/{id}/review:verify", reviewRequest("{\"reviewedBy\":42}"),
                String.class, Long.MAX_VALUE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("EXPERIENCE_NOT_FOUND");
    }

    @Test
    void rejectsInvalidReviewedByThroughApiWithoutChangingExperience() {
        for (String request : List.of("{}", "{\"reviewedBy\":0}", "{\"reviewedBy\":-1}")) {
            ExperienceUnit generated = saveGeneratedExperience("review-invalid-" + System.nanoTime());

            ResponseEntity<String> response = rest.postForEntity(
                    "/api/v1/experiences/{id}/review:verify", reviewRequest(request),
                    String.class, generated.id());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(experiences.findById(generated.id())).get()
                    .extracting(ExperienceUnit::reviewStatus, ExperienceUnit::reviewedBy,
                            ExperienceUnit::reviewedAt, ExperienceUnit::indexStatus, ExperienceUnit::version)
                    .containsExactly(ExperienceUnit.ReviewStatus.GENERATED, null, null,
                            ExperienceUnit.IndexStatus.NOT_INDEXED, 0);
        }
    }

    private HttpEntity<String> reviewRequest(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private ExperienceUnit saveGeneratedExperience(String externalKey) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        SalesCase salesCase = salesCases.save(new SalesCase(null, externalKey, "experience-state-case",
                SalesCase.SourceType.SYNTHETIC, null, "MANUFACTURING", SalesCase.SalesStage.NEGOTIATION,
                "PURCHASING_MANAGER", "customer needs", SalesCase.Status.EXTRACTED, null, 0, now, now));
        return experiences.save(new ExperienceUnit(null, salesCase.id(),
                ExperienceUnit.ScenarioType.OBJECTION_HANDLING, ExperienceUnit.ObjectionType.PRICE,
                SalesCase.SalesStage.NEGOTIATION, "PURCHASING_MANAGER", "price concern", "compare cost",
                "what matters", "customer needs", 0, 14, "sales",
                String.format("%064x", Integer.toUnsignedLong(externalKey.hashCode())),
                ExperienceUnit.ReviewStatus.GENERATED, ExperienceUnit.IndexStatus.NOT_INDEXED, null,
                "local", "experience-extract-v1", null, null, 0, now, now));
    }
}
