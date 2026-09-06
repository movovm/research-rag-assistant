package com.salesmentor.web;

import com.salesmentor.core.ChatService;
import com.salesmentor.domain.ChatRequest;
import com.salesmentor.domain.ChatResult;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;
    private final Executor reviewExecutor;

    public ChatController(ChatService chatService, @Qualifier("reviewExecutor") Executor reviewExecutor) {
        this.chatService = chatService;
        this.reviewExecutor = reviewExecutor;
    }

    @PostMapping
    public ChatResult chat(@Valid @RequestBody ChatRequest request) {
        return chatService.chat(request);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(30_000L);
        reviewExecutor.execute(() -> sendStream(request, emitter));
        return emitter;
    }

    private void sendStream(ChatRequest request, SseEmitter emitter) {
        try {
            ChatResult result = chatService.chat(request);
            emitter.send(SseEmitter.event().name("context").data(Map.of(
                    "originalQuestion", result.originalQuestion(),
                    "rewrittenQuery", result.rewrittenQuery(),
                    "memorySummary", result.memorySummary(),
                    "longTermMemories", result.longTermMemories(),
                    "evidence", result.evidence(),
                    "stages", result.stages()
            )));
            for (String token : chunks(result.answer(), 8)) {
                emitter.send(SseEmitter.event().name("token").data(Map.of("token", token)));
            }
            emitter.send(SseEmitter.event().name("done").data(Map.of("answer", result.answer())));
            emitter.complete();
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        } catch (RuntimeException exception) {
            emitter.completeWithError(exception);
        }
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Map<String, Boolean> clear(@PathVariable String sessionId) {
        chatService.clear(sessionId);
        return Map.of("cleared", true);
    }

    private List<String> chunks(String value, int size) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < value.length(); index += size) {
            result.add(value.substring(index, Math.min(value.length(), index + size)));
        }
        return result;
    }
}
