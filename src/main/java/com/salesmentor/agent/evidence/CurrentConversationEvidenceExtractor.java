package com.salesmentor.agent.evidence;

import com.salesmentor.agent.report.CurrentConversationEvidence;

import java.util.ArrayList;
import java.util.List;

public final class CurrentConversationEvidenceExtractor {
    public List<CurrentConversationEvidence> extract(String conversationContent) {
        if (conversationContent == null) throw new IllegalArgumentException("conversationContent is required");
        List<CurrentConversationEvidence> evidence = new ArrayList<>();
        int offset = 0;
        int sequence = 1;
        for (String line : conversationContent.split("\\n", -1)) {
            int end = offset + line.length();
            if (!line.isBlank()) {
                evidence.add(new CurrentConversationEvidence("CUR-" + sequence++, line, offset, end));
            }
            offset = end + 1;
        }
        return List.copyOf(evidence);
    }
}
