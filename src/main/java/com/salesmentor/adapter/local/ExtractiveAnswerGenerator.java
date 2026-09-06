package com.salesmentor.adapter.local;

import com.salesmentor.core.TextTokenizer;
import com.salesmentor.domain.ScoredChunk;
import com.salesmentor.port.AnswerGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "app.rag", name = "mode", havingValue = "local", matchIfMissing = true)
public class ExtractiveAnswerGenerator implements AnswerGenerator {
    private final TextTokenizer tokenizer;

    public ExtractiveAnswerGenerator(TextTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    @Override
    public String generate(String prompt, String question, List<ScoredChunk> evidence) {
        if (evidence.isEmpty()) return "当前知识库中没有找到足够相关的资料。请补充文档，或换一种更具体的问法。";
        Set<String> queryTokens = new HashSet<>(tokenizer.tokenize(question));
        List<String> sentences = List.of(evidence.get(0).chunk().content().split("(?<=[。！？；])|\\n+"))
                .stream().map(String::trim).filter(value -> !value.isBlank()).toList();
        int anchor = 0;
        double anchorScore = -1;
        for (int i = 0; i < sentences.size(); i++) {
            double score = overlap(sentences.get(i), queryTokens);
            if (score > anchorScore) {
                anchor = i;
                anchorScore = score;
            }
        }
        List<String> selected = new ArrayList<>();
        for (int i = anchor; i < Math.min(sentences.size(), anchor + 4); i++) {
            String sentence = sentences.get(i);
            if (sentence.length() >= 12) selected.add(sentence);
        }
        if (selected.isEmpty()) selected.add(evidence.get(0).chunk().content());

        StringBuilder answer = new StringBuilder("根据知识库中的资料，可以这样处理：\n\n");
        int index = 1;
        for (String sentence : selected) {
            answer.append(index++).append(". ").append(sentence).append('\n');
        }
        answer.append("\n检索依据：");
        evidence.stream().limit(2).map(item -> item.chunk().source()).distinct()
                .forEach(source -> answer.append("《").append(source).append("》"));
        answer.append("。本地演示模式采用可解释的抽取式回答；切换 cloud 模式后由 Qwen 基于同一批证据生成答案。");
        return answer.toString();
    }

    private double overlap(String sentence, Set<String> queryTokens) {
        List<String> sentenceTokens = tokenizer.tokenize(sentence);
        long matches = sentenceTokens.stream().filter(queryTokens::contains).count();
        return matches + Math.min(sentence.length(), 120) / 1000.0;
    }
}
