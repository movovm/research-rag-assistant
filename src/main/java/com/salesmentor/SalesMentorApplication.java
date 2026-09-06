package com.salesmentor;

import com.salesmentor.config.RagProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RagProperties.class)
public class SalesMentorApplication {
    public static void main(String[] args) {
        SpringApplication.run(SalesMentorApplication.class, args);
    }
}
