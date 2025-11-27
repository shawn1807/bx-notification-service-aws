package com.tsu.notification.infrastructure.queue;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

/**
 * AWS SQS client configuration
 */
@Configuration
@ConditionalOnProperty(name = "queue.provider", havingValue = "sqs", matchIfMissing = true)
@Slf4j
public class SqsConfiguration {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.sqs.endpoint:#{null}}")
    private String sqsEndpoint;

    @Bean
    public SqsClient sqsClient() {
        var builder = SqsClient.builder()
            .region(Region.of(awsRegion))
            .credentialsProvider(DefaultCredentialsProvider.create());

        // For local development with LocalStack or ElasticMQ
        if (sqsEndpoint != null && !sqsEndpoint.isBlank()) {
            log.info("Using custom SQS endpoint: {}", sqsEndpoint);
            builder.endpointOverride(URI.create(sqsEndpoint));
        }

        SqsClient client = builder.build();
        log.info("SQS client initialized for region: {}", awsRegion);

        return client;
    }
}
