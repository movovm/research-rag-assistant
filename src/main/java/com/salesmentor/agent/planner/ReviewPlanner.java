package com.salesmentor.agent.planner;

import com.salesmentor.agent.model.ReviewInput;
import com.salesmentor.agent.model.ReviewPlan;

public interface ReviewPlanner {
    ReviewPlan plan(ReviewInput input);
}
