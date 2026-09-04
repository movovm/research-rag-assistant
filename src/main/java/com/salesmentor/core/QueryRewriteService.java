package com.salesmentor.core;

import com.salesmentor.domain.ChatMessage;
import com.salesmentor.domain.MemoryState;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class QueryRewriteService {
    private static final Pattern CONTEXT_DEPENDENT = Pattern.compile("(这个|那个|它|该项目|该方案|上面|刚才|继续|怎么做|有什么风险|优缺点)");

    public String rewrite(String question, MemoryState memory) {
        if (!CONTEXT_DEPENDENT.matcher(question).find()) return question;
        List<ChatMessage> messages = memory.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if ("user".equals(message.role()) && !message.content().equals(question)) {
                return message.content() + "；用户追问：" + question;
            }
        }
        return question;
    }
}
