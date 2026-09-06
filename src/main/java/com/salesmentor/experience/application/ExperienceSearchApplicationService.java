package com.salesmentor.experience.application;

import com.salesmentor.core.LexicalIndex;
import com.salesmentor.domain.ScoredChunk;
import com.salesmentor.experience.domain.ExperienceRepository;
import com.salesmentor.experience.domain.ExperienceUnit;
import com.salesmentor.port.EmbeddingProvider;
import com.salesmentor.port.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ExperienceSearchApplicationService {
    private static final int RRF_K = 60;
    private static final double DENSE_WEIGHT = 0.58;
    private static final double LEXICAL_WEIGHT = 0.42;
    private static final Pattern CHUNK_ID = Pattern.compile("experience-([1-9][0-9]*)");

    private final ExperienceRepository experiences;
    private final EmbeddingProvider embeddings;
    private final VectorStore vectorStore;
    private final LexicalIndex lexicalIndex;

    public ExperienceSearchApplicationService(ExperienceRepository experiences,
                                               EmbeddingProvider embeddings,
                                               VectorStore vectorStore,
                                               LexicalIndex lexicalIndex) {
        this.experiences = experiences;
        this.embeddings = embeddings;
        this.vectorStore = vectorStore;
        this.lexicalIndex = lexicalIndex;
    }

    public List<ExperienceSearchResult> search(ExperienceQuery query) {
        int candidateSize = Math.max(query.topK() * 4, 20);
        List<ScoredChunk> dense = denseCandidates(query.queryText(), candidateSize);
        List<ScoredChunk> lexical = lexicalCandidates(query.queryText(), candidateSize);

        Map<Long, Ranks> ranks = new LinkedHashMap<>();
        collectRanks(dense, true, ranks);
        collectRanks(lexical, false, ranks);
        if (ranks.isEmpty()) return List.of();

        List<ExperienceUnit> admitted = experiences.findPublishedIndexedByIds(ranks.keySet());
        Map<Long, ExperienceUnit> byId = new HashMap<>();
        admitted.forEach(value -> byId.put(value.id(), value));

        List<ExperienceSearchResult> results = ranks.entrySet().stream()
                .filter(entry -> byId.containsKey(entry.getKey()))
                .map(entry -> Map.entry(byId.get(entry.getKey()), entry.getValue()))
                .filter(entry -> matches(entry.getKey(), query))
                .map(entry -> result(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingDouble(ExperienceSearchResult::retrievalScore).reversed()
                        .thenComparing(ExperienceSearchResult::experienceId))
                .limit(query.topK())
                .toList();
        List<ExperienceSearchResult> ranked = new ArrayList<>(results.size());
        for (int index = 0; index < results.size(); index++) {
            ExperienceSearchResult value = results.get(index);
            ranked.add(new ExperienceSearchResult(value.experienceId(), value.caseId(), value.scenarioType(),
                    value.objectionType(), value.salesStage(), value.customerRole(), value.triggerText(),
                    value.strategySummary(), value.recommendedQuestion(), value.evidenceQuote(), value.evidenceStart(),
                    value.evidenceEnd(), value.retrievalScore(), index + 1));
        }
        return ranked;
    }

    private List<ScoredChunk> denseCandidates(String text, int size) {
        try {
            return vectorStore.search(embeddings.embed(text, EmbeddingProvider.InputType.QUERY), size);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private List<ScoredChunk> lexicalCandidates(String text, int size) {
        try {
            return lexicalIndex.search(text, size);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private void collectRanks(List<ScoredChunk> candidates, boolean dense, Map<Long, Ranks> ranks) {
        for (int index = 0; index < candidates.size(); index++) {
            Long id = experienceId(candidates.get(index));
            if (id == null) continue;
            Ranks value = ranks.computeIfAbsent(id, ignored -> new Ranks());
            int rank = index + 1;
            if (dense) value.denseRank = Math.min(value.denseRank, rank);
            else value.lexicalRank = Math.min(value.lexicalRank, rank);
        }
    }

    private Long experienceId(ScoredChunk scored) {
        String metadataId = scored.chunk().metadata() == null ? null : scored.chunk().metadata().get("experienceId");
        Long parsed = parsePositive(metadataId);
        if (parsed != null) return parsed;
        if (metadataId != null && !metadataId.isBlank()) return null;
        Matcher matcher = CHUNK_ID.matcher(scored.chunk().id() == null ? "" : scored.chunk().id());
        return matcher.matches() ? parsePositive(matcher.group(1)) : null;
    }

    private Long parsePositive(String value) {
        if (value == null || !value.matches("[1-9][0-9]*")) return null;
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean matches(ExperienceUnit value, ExperienceQuery query) {
        return (query.scenarioType() == null || query.scenarioType() == value.scenarioType())
                && (query.objectionType() == null || query.objectionType() == value.objectionType())
                && (query.salesStage() == null || query.salesStage() == value.salesStage())
                && (query.customerRole() == null || query.customerRole().equals(value.customerRole()));
    }

    private ExperienceSearchResult result(ExperienceUnit value, Ranks ranks) {
        double score = (ranks.denseRank == Integer.MAX_VALUE ? 0 : DENSE_WEIGHT / (RRF_K + ranks.denseRank))
                + (ranks.lexicalRank == Integer.MAX_VALUE ? 0 : LEXICAL_WEIGHT / (RRF_K + ranks.lexicalRank));
        return new ExperienceSearchResult(value.id(), value.caseId(), value.scenarioType(), value.objectionType(),
                value.salesStage(), value.customerRole(), value.triggerText(), value.strategySummary(),
                value.recommendedQuestion(), value.evidenceQuote(), value.evidenceStart(), value.evidenceEnd(),
                score, 0);
    }

    private static final class Ranks {
        private int denseRank = Integer.MAX_VALUE;
        private int lexicalRank = Integer.MAX_VALUE;
    }
}
