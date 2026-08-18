package io.github.portfolio.rag;

import io.github.portfolio.rag.config.RagProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RagProperties.class)
public class ResearchRagApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResearchRagApplication.class, args);
    }
}
