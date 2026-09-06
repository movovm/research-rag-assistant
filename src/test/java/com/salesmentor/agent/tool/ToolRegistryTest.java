package com.salesmentor.agent.tool;

import com.salesmentor.agent.model.ReviewToolName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolRegistryTest {
    private final FakeTool experience = new FakeTool(ReviewToolName.EXPERIENCE_SEARCH);
    private final FakeTool product = new FakeTool(ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH);

    @Test void registersBothToolsAndResolvesByName() {
        ToolRegistry registry = new ToolRegistry(List.of(experience, product));
        assertThat(registry.require(ReviewToolName.EXPERIENCE_SEARCH)).isSameAs(experience);
        assertThat(registry.require(ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH)).isSameAs(product);
        assertThat(registry.registeredToolNames()).containsExactlyInAnyOrder(ReviewToolName.values());
        assertThat(ReviewToolName.values()).containsExactly(ReviewToolName.EXPERIENCE_SEARCH,
                ReviewToolName.PRODUCT_KNOWLEDGE_SEARCH);
    }

    @Test void rejectsMissingDuplicateAndNullInputs() {
        assertThatThrownBy(() -> new ToolRegistry(List.of(experience))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ToolRegistry(List.of(product))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ToolRegistry(List.of(experience, new FakeTool(ReviewToolName.EXPERIENCE_SEARCH))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ToolRegistry(Arrays.asList(experience, null, product)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ToolRegistry(List.of(new FakeTool(null), product)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ToolRegistry(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsNullLookupAndExposesImmutableNames() {
        ToolRegistry registry = new ToolRegistry(List.of(experience, product));
        assertThatThrownBy(() -> registry.require(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.registeredToolNames().remove(ReviewToolName.EXPERIENCE_SEARCH))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> registry.registeredToolNames().add(ReviewToolName.EXPERIENCE_SEARCH))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(ToolRegistry.class.getDeclaredMethods()).extracting(Method::getName)
                .doesNotContain("register", "unregister");
    }

    private record FakeTool(ReviewToolName name) implements ReviewReadOnlyTool {}
}
