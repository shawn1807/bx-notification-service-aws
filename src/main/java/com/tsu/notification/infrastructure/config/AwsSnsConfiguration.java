package com.tsu.notification.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

import java.net.URI;

/**
 * AWS SNS client configuration
 */
@Configuration
@ConditionalOnProperty(
    value = "notification.channels.sms.provider",
    havingValue = "AWS_SNS"
)
@Slf4j
public class AwsSnsConfiguration {

    @Value("${aws.region:ap-east-2}")
    private String awsRegion;

    @Value("${aws.sns.endpoint:#{null}}")
    private String snsEndpoint;

    @Bean
    public SnsClient snsClient() {
        var builder = SnsClient.builder()
            .region(Region.of(awsRegion))
            .credentialsProvider(DefaultCredentialsProvider.create());

        // For local development with LocalStack
        if (snsEndpoint != null && !snsEndpoint.isBlank()) {
            log.info("Using custom SNS endpoint: {}", snsEndpoint);
            builder.endpointOverride(URI.create(snsEndpoint));
        }

        SnsClient client = builder.build();
        log.info("AWS SNS client initialized for region: {}", awsRegion);

        return client;
    }
}
