package io.github.portfolio.rag.web;

import io.github.portfolio.rag.core.ChatService;
import io.github.portfolio.rag.domain.ChatRequest;
import io.github.portfolio.rag.domain.ChatResult;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResult chat(@Valid @RequestBody ChatRequest request) {
        return chatService.chat(request);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(@Valid @RequestBody ChatRequest request) {
        return Flux.defer(() -> {
            ChatResult result = chatService.chat(request);
            ServerSentEvent<Object> context = ServerSentEvent.builder((Object) Map.of(
                    "originalQuestion", result.originalQuestion(),
                    "rewrittenQuery", result.rewrittenQuery(),
                    "memorySummary", result.memorySummary(),
                    "longTermMemories", result.longTermMemories(),
                    "evidence", result.evidence(),
                    "stages", result.stages()
            )).event("context").build();
            Flux<ServerSentEvent<Object>> tokens = Flux.fromIterable(chunks(result.answer(), 8))
                    .delayElements(Duration.ofMillis(28))
                    .map(token -> ServerSentEvent.builder((Object) Map.of("token", token)).event("token").build());
            ServerSentEvent<Object> done = ServerSentEvent.builder((Object) Map.of("answer", result.answer())).event("done").build();
            return Flux.concat(Flux.just(context), tokens, Flux.just(done));
        });
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
