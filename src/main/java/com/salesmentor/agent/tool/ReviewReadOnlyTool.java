package com.salesmentor.agent.tool;

import com.salesmentor.agent.model.ReviewToolName;

/** Marker contract for capabilities that may be registered with the review runtime. */
public interface ReviewReadOnlyTool {
    ReviewToolName name();
}
