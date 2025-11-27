package com.tsu.notification.lambda;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Spring Configuration for AWS Lambda
 *
 * Initializes only the necessary beans for Lambda execution
 * to minimize cold start time and memory usage
 */
@Configuration
@ComponentScan(basePackages = {
    "com.tsu.notification.application",
    "com.tsu.notification.domain",
    "com.tsu.notification.infrastructure"
})
@PropertySource("classpath:application-lambda.properties")
public class LambdaConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    // Note: Database, AWS clients, and other beans are automatically
    // configured through component scanning and Spring Boot auto-configuration
}
