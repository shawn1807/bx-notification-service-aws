package com.tsu.notification.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

import java.net.URI;

/**
 * AWS SES client configuration
 */
@Configuration
@ConditionalOnProperty(name = "notification.channels.email.provider", havingValue = "AWS_SES")
@Slf4j
public class AwsSesConfiguration {

    @Value("${aws.region:ap-east-2}")
    private String awsRegion;

    @Value("${aws.ses.endpoint:#{null}}")
    private String sesEndpoint;

    @Bean
    public SesClient sesClient() {
        var builder = SesClient.builder()
            .region(Region.of(awsRegion))
            .credentialsProvider(DefaultCredentialsProvider.create());

        // For local development with LocalStack
        if (sesEndpoint != null && !sesEndpoint.isBlank()) {
            log.info("Using custom SES endpoint: {}", sesEndpoint);
            builder.endpointOverride(URI.create(sesEndpoint));
        }

        SesClient client = builder.build();
        log.info("AWS SES client initialized for region: {}", awsRegion);

        return client;
    }
}
