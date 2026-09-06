package com.salesmentor.experience.application;

import com.salesmentor.core.LexicalIndex;
import com.salesmentor.domain.ChunkVector;
import com.salesmentor.domain.DocumentChunk;
import com.salesmentor.experience.domain.ExperienceIndexingUnavailableException;
import com.salesmentor.experience.domain.ExperienceNotFoundException;
import com.salesmentor.experience.domain.ExperienceRepository;
import com.salesmentor.experience.domain.ExperienceStateConflictException;
import com.salesmentor.experience.domain.ExperienceUnit;
import com.salesmentor.port.EmbeddingProvider;
import com.salesmentor.port.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Logger;

@Service
public class ExperiencePublishApplicationService {
    private static final Logger LOG = Logger.getLogger(ExperiencePublishApplicationService.class.getName());
    private static final String SOURCE = "salesmentor";
    private static final String DOCUMENT_TYPE = "SALES_EXPERIENCE";
    private static final String PROJECT = "salesmentor";

    private final ExperienceRepository experiences;
    private final EmbeddingProvider embeddings;
    private final VectorStore vectorStore;
    private final LexicalIndex lexicalIndex;
    private final Executor executor;

    public ExperiencePublishApplicationService(ExperienceRepository experiences, EmbeddingProvider embeddings,
                                               VectorStore vectorStore, LexicalIndex lexicalIndex,
                                               @Qualifier("experienceExecutor") Executor executor) {
        this.experiences = experiences;
        this.embeddings = embeddings;
        this.vectorStore = vectorStore;
        this.lexicalIndex = lexicalIndex;
        this.executor = executor;
    }

    public ExperienceUnit publish(Long id) {
        ExperienceUnit existing = experiences.findById(id).orElseThrow(() -> new ExperienceNotFoundException(id));
        if (existing.reviewStatus() == ExperienceUnit.ReviewStatus.PUBLISHED
                && existing.indexStatus() == ExperienceUnit.IndexStatus.INDEXED) {
            return existing;
        }
        if (existing.reviewStatus() != ExperienceUnit.ReviewStatus.VERIFIED
                || (existing.indexStatus() != ExperienceUnit.IndexStatus.NOT_INDEXED
                && existing.indexStatus() != ExperienceUnit.IndexStatus.FAILED)) {
            throw new ExperienceStateConflictException("Experience state has changed");
        }
        if (!experiences.claimIndexing(id, existing.version())) {
            throw new ExperienceStateConflictException("Experience state has changed");
        }
        ExperienceUnit claimed = experiences.findById(id).orElseThrow(() -> new ExperienceNotFoundException(id));
        try {
            executor.execute(() -> index(claimed));
        } catch (RejectedExecutionException exception) {
            experiences.markIndexFailed(id, claimed.version());
            throw new ExperienceIndexingUnavailableException();
        }
        return claimed;
    }

    DocumentChunk buildChunk(ExperienceUnit experience) {
        String id = chunkId(experience.id());
        String content = String.join("\n",
                "scenarioType: " + text(experience.scenarioType()),
                "objectionType: " + text(experience.objectionType()),
                "triggerText: " + text(experience.triggerText()),
                "strategySummary: " + text(experience.strategySummary()),
                "recommendedQuestion: " + text(experience.recommendedQuestion()),
                "applicability: " + text(experience.applicability()),
                "evidenceQuote: " + text(experience.evidenceQuote()));
        Map<String, String> metadata = Map.ofEntries(
                Map.entry("experienceId", experience.id().toString()),
                Map.entry("caseId", experience.caseId().toString()),
                Map.entry("scenarioType", text(experience.scenarioType())),
                Map.entry("objectionType", text(experience.objectionType())),
                Map.entry("salesStage", text(experience.salesStage())),
                Map.entry("customerRole", text(experience.customerRole())),
                Map.entry("reviewedBy", text(experience.reviewedBy())),
                Map.entry("promptVersion", text(experience.promptVersion())),
                Map.entry("sourceEvidenceQuote", text(experience.evidenceQuote())),
                Map.entry("evidenceStart", Integer.toString(experience.evidenceStart())),
                Map.entry("evidenceEnd", Integer.toString(experience.evidenceEnd())));
        return new DocumentChunk(id, id, SOURCE, DOCUMENT_TYPE, PROJECT, content, metadata);
    }

    static String chunkId(Long experienceId) {
        return "experience-" + experienceId;
    }

    private static String text(Object value) {
        return String.valueOf(value);
    }

    private void index(ExperienceUnit claimed) {
        try {
            DocumentChunk chunk = buildChunk(claimed);
            float[] vector = embeddings.embed(chunk.content(), EmbeddingProvider.InputType.DOCUMENT);
            vectorStore.upsert(List.of(new ChunkVector(chunk, vector)));
            lexicalIndex.upsert(List.of(chunk));
            if (!experiences.completePublishing(claimed.id(), chunk.id(), claimed.version())) {
                LOG.warning("Experience publishing CAS lost for " + claimed.id());
            }
        } catch (Exception exception) {
            experiences.markIndexFailed(claimed.id(), claimed.version());
            LOG.warning("Experience indexing failed for " + claimed.id());
        }
    }
}
