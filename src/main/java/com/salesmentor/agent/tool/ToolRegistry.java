package com.salesmentor.agent.tool;

import com.salesmentor.agent.model.ReviewToolName;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public final class ToolRegistry {
    private final Map<ReviewToolName, ReviewReadOnlyTool> tools;

    public ToolRegistry(Collection<? extends ReviewReadOnlyTool> tools) {
        if (tools == null) {
            throw new IllegalArgumentException("tools are required");
        }
        EnumMap<ReviewToolName, ReviewReadOnlyTool> registered = new EnumMap<>(ReviewToolName.class);
        for (ReviewReadOnlyTool tool : tools) {
            if (tool == null) {
                throw new IllegalArgumentException("tool and tool name are required");
            }
            ReviewToolName toolName = tool.name();
            if (toolName == null) {
                throw new IllegalArgumentException("tool and tool name are required");
            }
            if (toolName != ReviewToolName.EXPERIENCE_SEARCH
                    && toolName != ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH) {
                throw new IllegalArgumentException("unsupported review tool");
            }
            if (registered.putIfAbsent(toolName, tool) != null) {
                throw new IllegalArgumentException("duplicate review tool");
            }
        }
        if (!registered.keySet().containsAll(Set.of(ReviewToolName.EXPERIENCE_SEARCH,
                ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH))) {
            throw new IllegalArgumentException("both review tools are required");
        }
        this.tools = Map.copyOf(registered);
    }

    public ReviewReadOnlyTool require(ReviewToolName name) {
        if (name == null) {
            throw new IllegalArgumentException("tool name is required");
        }
        ReviewReadOnlyTool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("tool is not registered");
        }
        return tool;
    }

    public Set<ReviewToolName> registeredToolNames() {
        return tools.keySet();
    }
}
