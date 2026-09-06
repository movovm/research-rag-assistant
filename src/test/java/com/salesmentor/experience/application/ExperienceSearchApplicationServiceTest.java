package com.salesmentor.experience.application;

import com.salesmentor.core.LexicalIndex;
import com.salesmentor.domain.DocumentChunk;
import com.salesmentor.domain.ScoredChunk;
import com.salesmentor.experience.domain.ExperienceRepository;
import com.salesmentor.experience.domain.ExperienceUnit;
import com.salesmentor.port.EmbeddingProvider;
import com.salesmentor.port.VectorStore;
import com.salesmentor.salescase.domain.SalesCase;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ExperienceSearchApplicationServiceTest {
    @Test
    void queriesDenseEmbeddingWithQueryInputType() {
        Fixture fixture = new Fixture();
        String queryText = "price objection";
        ExperienceUnit admitted = fixture.experience(15L);
        when(fixture.embeddings.embed(queryText, EmbeddingProvider.InputType.QUERY))
                .thenReturn(new float[]{0.25f, 0.75f});
        when(fixture.vectors.search(any(), eq(20))).thenReturn(List.of(fixture.candidate(15L)));
        when(fixture.lexical.search(queryText, 20)).thenReturn(List.of());
        when(fixture.repository.findPublishedIndexedByIds(any())).thenReturn(List.of(admitted));

        assertThat(fixture.service.search(new ExperienceQuery(queryText, null, null, null, null, 5)))
                .extracting(ExperienceSearchResult::experienceId).containsExactly(15L);

        verify(fixture.embeddings).embed(queryText, EmbeddingProvider.InputType.QUERY);
        verify(fixture.embeddings, times(1)).embed(any(), eq(EmbeddingProvider.InputType.QUERY));
    }

    @Test
    void onlyMysqlAdmittedExperiencesAreReturned() {
        Fixture fixture = new Fixture();
        ExperienceUnit published = fixture.experience(1L, ExperienceUnit.ScenarioType.OBJECTION_HANDLING,
                ExperienceUnit.ObjectionType.PRICE, SalesCase.SalesStage.NEGOTIATION, "buyer");
        when(fixture.repository.findPublishedIndexedByIds(any())).thenReturn(List.of(published));
        when(fixture.vectors.search(any(), eq(20))).thenReturn(List.of(fixture.candidate(1L), fixture.candidate(2L)));
        when(fixture.lexical.search("price", 20)).thenReturn(List.of(fixture.candidate(2L), fixture.candidate(1L)));

        List<ExperienceSearchResult> results = fixture.service.search(new ExperienceQuery("price", null, null, null, null, 5));

        assertThat(results).extracting(ExperienceSearchResult::experienceId).containsExactly(1L);
        verify(fixture.repository).findPublishedIndexedByIds(argThat(ids -> ids.containsAll(List.of(1L, 2L))));
    }

    @Test
    void appliesAllBusinessFiltersAgainstMysqlUnits() {
        Fixture fixture = new Fixture();
        ExperienceUnit match = fixture.experience(3L, ExperienceUnit.ScenarioType.DISCOVERY,
                ExperienceUnit.ObjectionType.NEED, SalesCase.SalesStage.PROPOSAL, "buyer");
        ExperienceUnit mismatch = fixture.experience(4L, ExperienceUnit.ScenarioType.DISCOVERY,
                ExperienceUnit.ObjectionType.PRICE, SalesCase.SalesStage.PROPOSAL, "buyer");
        when(fixture.repository.findPublishedIndexedByIds(any())).thenReturn(List.of(match, mismatch));
        when(fixture.vectors.search(any(), eq(20))).thenReturn(List.of(fixture.candidate(3L), fixture.candidate(4L)));
        when(fixture.lexical.search(any(), eq(20))).thenReturn(List.of());

        ExperienceQuery query = new ExperienceQuery("q", ExperienceUnit.ScenarioType.DISCOVERY,
                ExperienceUnit.ObjectionType.NEED, SalesCase.SalesStage.PROPOSAL, "buyer", 5);
        assertThat(fixture.service.search(query)).extracting(ExperienceSearchResult::experienceId).containsExactly(3L);
    }

    @Test
    void mergesDenseAndLexicalUsingWeightedRrfAndStableTieBreak() {
        Fixture fixture = new Fixture();
        List<ExperienceUnit> units = List.of(fixture.experience(1L), fixture.experience(2L), fixture.experience(3L));
        when(fixture.repository.findPublishedIndexedByIds(any())).thenReturn(units);
        when(fixture.vectors.search(any(), eq(20))).thenReturn(List.of(fixture.candidate(2L), fixture.candidate(1L)));
        when(fixture.lexical.search(any(), eq(20))).thenReturn(List.of(fixture.candidate(1L), fixture.candidate(3L)));

        List<ExperienceSearchResult> results = fixture.service.search(new ExperienceQuery("q", null, null, null, null, 3));
        assertThat(results).extracting(ExperienceSearchResult::experienceId).containsExactly(1L, 2L, 3L);
        assertThat(results).extracting(ExperienceSearchResult::rank).containsExactly(1, 2, 3);
        assertThat(results.get(0).retrievalScore()).isEqualTo(0.58 / 62 + 0.42 / 61);
    }

    @Test
    void supportsEmptySingleRouteAndRouteFailures() {
        Fixture fixture = new Fixture();
        ExperienceUnit unit = fixture.experience(5L);
        when(fixture.repository.findPublishedIndexedByIds(any())).thenReturn(List.of(unit));
        when(fixture.vectors.search(any(), eq(20))).thenReturn(List.of(fixture.candidate(5L)));
        when(fixture.lexical.search(any(), eq(20))).thenThrow(new IllegalStateException());
        assertThat(fixture.service.search(new ExperienceQuery("q", null, null, null, null, 5))).hasSize(1);

        when(fixture.vectors.search(any(), eq(20))).thenThrow(new IllegalStateException());
        when(fixture.lexical.search(any(), eq(20))).thenReturn(List.of(fixture.candidate(5L)));
        assertThat(fixture.service.search(new ExperienceQuery("q", null, null, null, null, 5))).hasSize(1);

        when(fixture.lexical.search(any(), eq(20))).thenReturn(List.of());
        assertThat(fixture.service.search(new ExperienceQuery("q", null, null, null, null, 5))).isEmpty();
    }

    @Test
    void parsesMetadataFirstAndFallsBackToDeterministicChunkId() {
        Fixture fixture = new Fixture();
        ExperienceUnit unit = fixture.experience(6L);
        when(fixture.repository.findPublishedIndexedByIds(any())).thenReturn(List.of(unit));
        when(fixture.vectors.search(any(), eq(20))).thenReturn(List.of(
                new ScoredChunk(chunk("experience-6", Map.of()), 0, 1, 0, 0),
                new ScoredChunk(chunk("experience-7", Map.of("experienceId", "bad")), 0, 1, 0, 0),
                new ScoredChunk(chunk("other", Map.of()), 0, 1, 0, 0)));
        when(fixture.lexical.search(any(), eq(20))).thenReturn(List.of());
        assertThat(fixture.service.search(new ExperienceQuery("q", null, null, null, null, 5)))
                .extracting(ExperienceSearchResult::experienceId).containsExactly(6L);
    }

    @Test
    void validatesQueryTextAndTopK() {
        assertThatThrownBy(() -> new ExperienceQuery(" ", null, null, null, null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExperienceQuery("q", null, null, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExperienceQuery("q", null, null, null, null, 21))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static DocumentChunk chunk(String id, Map<String, String> metadata) {
        return new DocumentChunk(id, id, "salesmentor", "SALES_EXPERIENCE", "salesmentor", "content", metadata);
    }

    private static class Fixture {
        private final ExperienceRepository repository = mock(ExperienceRepository.class);
        private final EmbeddingProvider embeddings = mock(EmbeddingProvider.class);
        private final VectorStore vectors = mock(VectorStore.class);
        private final LexicalIndex lexical = mock(LexicalIndex.class);
        private final ExperienceSearchApplicationService service =
                new ExperienceSearchApplicationService(repository, embeddings, vectors, lexical);

        private ScoredChunk candidate(long id) {
            return new ScoredChunk(chunk("experience-" + id, Map.of("experienceId", Long.toString(id))), 0, 1, 0, 0);
        }

        private ExperienceUnit experience(long id) {
            return experience(id, ExperienceUnit.ScenarioType.OBJECTION_HANDLING,
                    ExperienceUnit.ObjectionType.PRICE, SalesCase.SalesStage.NEGOTIATION, "buyer");
        }

        private ExperienceUnit experience(long id, ExperienceUnit.ScenarioType scenario,
                                           ExperienceUnit.ObjectionType objection, SalesCase.SalesStage stage,
                                           String role) {
            LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
            return new ExperienceUnit(id, 10L, scenario, objection, stage, role, "trigger", "strategy",
                    "question", "evidence", 0, 8, "applicable", "hash", ExperienceUnit.ReviewStatus.PUBLISHED,
                    ExperienceUnit.IndexStatus.INDEXED, "experience-" + id, "local", "v1", 42L, now, 2, now, now);
        }
    }
}
