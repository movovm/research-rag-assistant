package com.salesmentor.experience.application;

import com.salesmentor.core.LexicalIndex;
import com.salesmentor.domain.ChunkVector;
import com.salesmentor.domain.DocumentChunk;
import com.salesmentor.experience.domain.ExperienceIndexingUnavailableException;
import com.salesmentor.experience.domain.ExperienceRepository;
import com.salesmentor.experience.domain.ExperienceStateConflictException;
import com.salesmentor.experience.domain.ExperienceUnit;
import com.salesmentor.port.EmbeddingProvider;
import com.salesmentor.port.VectorStore;
import com.salesmentor.salescase.domain.SalesCase;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ExperiencePublishApplicationServiceTest {
    @Test
    void buildsDeterministicDocumentAndUsesDocumentEmbedding() {
        ExperienceRepository repository = mock(ExperienceRepository.class);
        EmbeddingProvider embeddings = mock(EmbeddingProvider.class);
        VectorStore vectors = mock(VectorStore.class);
        LexicalIndex lexical = mock(LexicalIndex.class);
        ExperiencePublishApplicationService service = service(repository, embeddings, vectors, lexical, Runnable::run);
        ExperienceUnit experience = experience(7L, ExperienceUnit.ReviewStatus.VERIFIED,
                ExperienceUnit.IndexStatus.NOT_INDEXED, 0);

        DocumentChunk first = service.buildChunk(experience);
        DocumentChunk second = service.buildChunk(experience);

        assertThat(second).isEqualTo(first);
        when(embeddings.embed(first.content(), EmbeddingProvider.InputType.DOCUMENT)).thenReturn(new float[]{1, 2});
        ExperienceUnit claimed = experience(7L, ExperienceUnit.ReviewStatus.VERIFIED,
                ExperienceUnit.IndexStatus.INDEXING, 1);
        when(repository.findById(7L)).thenReturn(Optional.of(experience), Optional.of(claimed));
        when(repository.claimIndexing(7L, 0)).thenReturn(true);
        when(repository.completePublishing(7L, first.id(), 1)).thenReturn(true);
        service.publish(7L);
        verify(embeddings).embed(first.content(), EmbeddingProvider.InputType.DOCUMENT);
        verify(vectors).upsert(argThat(value -> value.size() == 1 && value.get(0).chunk().id().equals(first.id())));
        verify(lexical).upsert(argThat(value -> value.size() == 1 && value.get(0).id().equals(first.id())));
    }

    @Test
    void vectorFailureSkipsLexicalAndMarksIndexFailed() {
        ExperienceRepository repository = mock(ExperienceRepository.class);
        EmbeddingProvider embeddings = mock(EmbeddingProvider.class);
        VectorStore vectors = mock(VectorStore.class);
        LexicalIndex lexical = mock(LexicalIndex.class);
        ExperienceUnit verified = experience(8L, ExperienceUnit.ReviewStatus.VERIFIED,
                ExperienceUnit.IndexStatus.NOT_INDEXED, 0);
        ExperienceUnit claimed = experience(8L, ExperienceUnit.ReviewStatus.VERIFIED,
                ExperienceUnit.IndexStatus.INDEXING, 1);
        when(repository.findById(8L)).thenReturn(Optional.of(verified), Optional.of(claimed));
        when(repository.claimIndexing(8L, 0)).thenReturn(true);
        when(embeddings.embed(any(), eq(EmbeddingProvider.InputType.DOCUMENT))).thenReturn(new float[]{1});
        doThrow(new IllegalStateException("vector unavailable")).when(vectors).upsert(any());

        service(repository, embeddings, vectors, lexical, Runnable::run).publish(8L);

        verify(repository).markIndexFailed(8L, 1);
        verifyNoInteractions(lexical);
        verify(repository, never()).completePublishing(any(), any(), anyInt());
    }

    @Test
    void lexicalFailureMarksFailedWithoutPublishing() {
        ExperienceRepository repository = mock(ExperienceRepository.class);
        EmbeddingProvider embeddings = mock(EmbeddingProvider.class);
        VectorStore vectors = mock(VectorStore.class);
        LexicalIndex lexical = mock(LexicalIndex.class);
        ExperienceUnit verified = experience(9L, ExperienceUnit.ReviewStatus.VERIFIED,
                ExperienceUnit.IndexStatus.FAILED, 4);
        ExperienceUnit claimed = experience(9L, ExperienceUnit.ReviewStatus.VERIFIED,
                ExperienceUnit.IndexStatus.INDEXING, 5);
        when(repository.findById(9L)).thenReturn(Optional.of(verified), Optional.of(claimed));
        when(repository.claimIndexing(9L, 4)).thenReturn(true);
        when(embeddings.embed(any(), eq(EmbeddingProvider.InputType.DOCUMENT))).thenReturn(new float[]{1});
        doThrow(new IllegalStateException("lexical unavailable")).when(lexical).upsert(any());

        service(repository, embeddings, vectors, lexical, Runnable::run).publish(9L);

        verify(repository).markIndexFailed(9L, 5);
        verify(repository, never()).completePublishing(any(), any(), anyInt());
    }

    @Test
    void publishedExperienceIsIdempotentAndDoesNotWriteIndexes() {
        ExperienceRepository repository = mock(ExperienceRepository.class);
        ExperienceUnit published = experience(10L, ExperienceUnit.ReviewStatus.PUBLISHED,
                ExperienceUnit.IndexStatus.INDEXED, 6);
        when(repository.findById(10L)).thenReturn(Optional.of(published));
        EmbeddingProvider embeddings = mock(EmbeddingProvider.class);
        VectorStore vectors = mock(VectorStore.class);
        LexicalIndex lexical = mock(LexicalIndex.class);
        ExperiencePublishApplicationService service = service(repository, embeddings, vectors, lexical, Runnable::run);

        assertThat(service.publish(10L)).isEqualTo(published);
        verify(repository, never()).claimIndexing(any(), anyInt());
        verify(repository).findById(10L);
        verifyNoInteractions(embeddings, vectors, lexical);
    }

    @Test
    void invalidStatesAreRejected() {
        for (ExperienceUnit.ReviewStatus review : List.of(ExperienceUnit.ReviewStatus.GENERATED,
                ExperienceUnit.ReviewStatus.REJECTED)) {
            ExperienceRepository repository = mock(ExperienceRepository.class);
            ExperienceUnit value = experience(11L, review, ExperienceUnit.IndexStatus.NOT_INDEXED, 0);
            when(repository.findById(11L)).thenReturn(Optional.of(value));
            ExperiencePublishApplicationService service = service(repository, mock(EmbeddingProvider.class),
                    mock(VectorStore.class), mock(LexicalIndex.class), Runnable::run);
            assertThatThrownBy(() -> service.publish(11L)).isInstanceOf(ExperienceStateConflictException.class);
        }
    }

    @Test
    void failedExperienceCanBeExplicitlyRetriedWithSameChunkId() {
        ExperienceRepository repository = mock(ExperienceRepository.class);
        EmbeddingProvider embeddings = mock(EmbeddingProvider.class);
        VectorStore vectors = mock(VectorStore.class);
        LexicalIndex lexical = mock(LexicalIndex.class);
        ExperienceUnit failed = experience(12L, ExperienceUnit.ReviewStatus.VERIFIED,
                ExperienceUnit.IndexStatus.FAILED, 2);
        ExperienceUnit claimed = experience(12L, ExperienceUnit.ReviewStatus.VERIFIED,
                ExperienceUnit.IndexStatus.INDEXING, 3);
        when(repository.findById(12L)).thenReturn(Optional.of(failed), Optional.of(claimed));
        when(repository.claimIndexing(12L, 2)).thenReturn(true);
        when(embeddings.embed(any(), eq(EmbeddingProvider.InputType.DOCUMENT))).thenReturn(new float[]{1});
        when(repository.completePublishing(12L, "experience-12", 3)).thenReturn(true);

        service(repository, embeddings, vectors, lexical, Runnable::run).publish(12L);

        verify(vectors).upsert(argThat(value -> value.get(0).chunk().id().equals("experience-12")));
        verify(lexical).upsert(argThat(value -> value.get(0).id().equals("experience-12")));
        verify(repository).completePublishing(12L, "experience-12", 3);
    }

    @Test
    void rejectedExecutorConvergesToFailedAndExposesBusyError() {
        ExperienceRepository repository = mock(ExperienceRepository.class);
        ExperienceUnit verified = experience(13L, ExperienceUnit.ReviewStatus.VERIFIED,
                ExperienceUnit.IndexStatus.NOT_INDEXED, 0);
        ExperienceUnit claimed = experience(13L, ExperienceUnit.ReviewStatus.VERIFIED,
                ExperienceUnit.IndexStatus.INDEXING, 1);
        when(repository.findById(13L)).thenReturn(Optional.of(verified), Optional.of(claimed));
        when(repository.claimIndexing(13L, 0)).thenReturn(true);

        ExperiencePublishApplicationService service = service(repository, mock(EmbeddingProvider.class),
                mock(VectorStore.class), mock(LexicalIndex.class), command -> {
                    throw new RejectedExecutionException();
                });

        assertThatThrownBy(() -> service.publish(13L))
                .isInstanceOf(ExperienceIndexingUnavailableException.class);
        verify(repository).markIndexFailed(13L, 1);
        verify(repository, never()).completePublishing(any(), any(), anyInt());
    }

    @Test
    void completingCasLossDoesNotMarkPublishedOrFailed() {
        ExperienceRepository repository = mock(ExperienceRepository.class);
        EmbeddingProvider embeddings = mock(EmbeddingProvider.class);
        VectorStore vectors = mock(VectorStore.class);
        LexicalIndex lexical = mock(LexicalIndex.class);
        ExperienceUnit verified = experience(14L, ExperienceUnit.ReviewStatus.VERIFIED,
                ExperienceUnit.IndexStatus.NOT_INDEXED, 0);
        ExperienceUnit claimed = experience(14L, ExperienceUnit.ReviewStatus.VERIFIED,
                ExperienceUnit.IndexStatus.INDEXING, 1);
        when(repository.findById(14L)).thenReturn(Optional.of(verified), Optional.of(claimed));
        when(repository.claimIndexing(14L, 0)).thenReturn(true);
        when(embeddings.embed(any(), eq(EmbeddingProvider.InputType.DOCUMENT))).thenReturn(new float[]{1});
        when(repository.completePublishing(14L, "experience-14", 1)).thenReturn(false);

        service(repository, embeddings, vectors, lexical, Runnable::run).publish(14L);

        verify(repository, never()).markIndexFailed(any(), anyInt());
    }

    private ExperiencePublishApplicationService service(ExperienceRepository repository, EmbeddingProvider embeddings,
                                                          VectorStore vectors, LexicalIndex lexical, java.util.concurrent.Executor executor) {
        return new ExperiencePublishApplicationService(repository, embeddings, vectors, lexical, executor);
    }

    private ExperienceUnit experience(long id, ExperienceUnit.ReviewStatus review, ExperienceUnit.IndexStatus index,
                                      int version) {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        return new ExperienceUnit(id, 99L, ExperienceUnit.ScenarioType.OBJECTION_HANDLING,
                ExperienceUnit.ObjectionType.PRICE, SalesCase.SalesStage.NEGOTIATION, "buyer",
                "price concern", "compare total cost", "what matters most?", "price is high", 0, 13,
                "enterprise sales", "hash", review, index, null, "local", "experience-extract-v1",
                42L, now, version, now, now);
    }
}
