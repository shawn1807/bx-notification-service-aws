package com.tsu.notification.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tsu.enums.PushPlatform;
import com.tsu.notification.val.DevicePushTokenVal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AWS SNS implementation for mobile push notifications
 *
 * AWS SNS supports push notifications through Platform Applications:
 * - FCM (Firebase Cloud Messaging) for Android
 * - APNs (Apple Push Notification Service) for iOS
 *
 * Benefits over direct FCM/APNs:
 * - Unified API for all platforms
 * - Automatic retry and delivery tracking
 * - Topic-based and targeted push
 * - Integration with other AWS services
 *
 * Setup required:
 * 1. Create SNS Platform Applications for each platform (FCM/APNs)
 * 2. Register device endpoints with SNS
 * 3. Use endpoint ARN to send notifications
 */
@Component
@ConditionalOnProperty(name = "notification.channels.push.provider", havingValue = "AWS_SNS")
@RequiredArgsConstructor
@Slf4j
public class AwsSnsPushSenderAdapter {

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;

    @Value("${notification.channels.push.fcm.platform-application-arn:#{null}}")
    private String fcmPlatformArn;

    @Value("${notification.channels.push.apns.platform-application-arn:#{null}}")
    private String apnsPlatformArn;

    /**
     * Send push notification via AWS SNS
     *
     * Note: Device must be registered as SNS endpoint first
     */
    public SendResult sendPush(
        DevicePushTokenVal deviceToken,
        String title,
        String body,
        Map<String, Object> metadata
    ) {
        log.info("Sending push notification via AWS SNS: platform={}, deviceId={}",
            deviceToken.(), deviceToken.getDeviceId());

        try {
            // Get or create SNS endpoint for device
            String endpointArn = getOrCreateEndpoint(deviceToken);

            // Build platform-specific message
            String message = buildPlatformMessage(
                deviceToken.getPlatform(),
                title,
                body,
                metadata
            );

            // Publish to endpoint
            PublishRequest request = PublishRequest.builder()
                .targetArn(endpointArn)
                .message(message)
                .messageStructure("json") // Required for platform-specific messages
                .build();

            PublishResponse response = snsClient.publish(request);
            String messageId = response.messageId();

            log.info("Push notification sent via AWS SNS: messageId={}, endpointArn={}",
                messageId, endpointArn);

            return SendResult.success(messageId, "AWS_SNS_PUSH");

        } catch (EndpointDisabledException e) {
            log.error("SNS endpoint disabled: deviceId={}", deviceToken.deviceId());
            return SendResult.failure("Push endpoint disabled", "SNS_ENDPOINT_DISABLED");

        } catch (InvalidParameterException e) {
            log.error("SNS invalid parameter: {}", e.awsErrorDetails().errorMessage());
            return SendResult.failure(
                "Invalid push parameter: " + e.awsErrorDetails().errorMessage(),
                "SNS_INVALID_PARAMETER"
            );

        } catch (SnsException e) {
            log.error("AWS SNS error: {}", e.awsErrorDetails().errorMessage(), e);
            return SendResult.failure(
                "SNS error: " + e.awsErrorDetails().errorMessage(),
                "SNS_ERROR"
            );

        } catch (Exception e) {
            log.error("Unexpected error sending push via SNS", e);
            return SendResult.failure(e.getMessage(), "PUSH_SEND_ERROR");
        }
    }

    /**
     * Get existing endpoint ARN or create new one
     */
    private String getOrCreateEndpoint(DevicePushToken deviceToken) {
        String platformArn = getPlatformArn(deviceToken.getPlatform());

        try {
            // Create endpoint
            CreatePlatformEndpointRequest request = CreatePlatformEndpointRequest.builder()
                .platformApplicationArn(platformArn)
                .token(deviceToken.getToken())
                .customUserData(deviceToken.getDeviceId()) // Store device ID for reference
                .build();

            CreatePlatformEndpointResponse response = snsClient.createPlatformEndpoint(request);
            String endpointArn = response.endpointArn();

            log.info("Created SNS endpoint: arn={}, deviceId={}",
                endpointArn, deviceToken.getDeviceId());

            return endpointArn;

        } catch (InvalidParameterException e) {
            // Endpoint might already exist, extract from error message
            String errorMessage = e.awsErrorDetails().errorMessage();
            if (errorMessage != null && errorMessage.contains("Endpoint") && errorMessage.contains("already exists")) {
                // Extract endpoint ARN from error message
                // Format: "Endpoint arn:aws:sns:... already exists with the same Token"
                int arnStart = errorMessage.indexOf("arn:aws:sns:");
                if (arnStart > 0) {
                    int arnEnd = errorMessage.indexOf(" ", arnStart);
                    if (arnEnd > arnStart) {
                        String existingArn = errorMessage.substring(arnStart, arnEnd);
                        log.info("Using existing SNS endpoint: {}", existingArn);
                        return existingArn;
                    }
                }
            }
            throw e;
        }
    }

    /**
     * Build platform-specific message payload
     * AWS SNS requires JSON format with platform-specific keys
     */
    private String buildPlatformMessage(
        PushPlatform platform,
        String title,
        String body,
        Map<String, Object> metadata
    ) throws Exception {
        Map<String, Object> messageMap = new HashMap<>();

        switch (platform) {
            case FCM -> {
                // FCM message format
                Map<String, Object> fcmPayload = new HashMap<>();
                Map<String, String> notification = new HashMap<>();
                notification.put("title", title);
                notification.put("body", body);
                fcmPayload.put("notification", notification);

                if (metadata != null && !metadata.isEmpty()) {
                    fcmPayload.put("data", metadata);
                }

                messageMap.put("GCM", objectMapper.writeValueAsString(fcmPayload));
            }
            case APNS -> {
                // APNs message format
                Map<String, Object> apnsPayload = new HashMap<>();
                Map<String, Object> aps = new HashMap<>();
                Map<String, String> alert = new HashMap<>();
                alert.put("title", title);
                alert.put("body", body);
                aps.put("alert", alert);
                aps.put("sound", "default");
                apnsPayload.put("aps", aps);

                if (metadata != null && !metadata.isEmpty()) {
                    apnsPayload.putAll(metadata);
                }

                // APNs requires different keys for sandbox vs production
                String apnsMessage = objectMapper.writeValueAsString(apnsPayload);
                messageMap.put("APNS", apnsMessage);
                messageMap.put("APNS_SANDBOX", apnsMessage);
            }
        }

        // Add default message (fallback)
        messageMap.put("default", title + ": " + body);

        return objectMapper.writeValueAsString(messageMap);
    }

    /**
     * Get platform application ARN based on platform
     */
    private String getPlatformArn(PushPlatform platform) {
        return switch (platform) {
            case FCM -> {
                if (fcmPlatformArn == null || fcmPlatformArn.isBlank()) {
                    throw new IllegalStateException(
                        "FCM platform ARN not configured. Set notification.channels.push.fcm.platform-application-arn"
                    );
                }
                yield fcmPlatformArn;
            }
            case APNS -> {
                if (apnsPlatformArn == null || apnsPlatformArn.isBlank()) {
                    throw new IllegalStateException(
                        "APNs platform ARN not configured. Set notification.channels.push.apns.platform-application-arn"
                    );
                }
                yield apnsPlatformArn;
            }
        };
    }

    /**
     * Delete SNS endpoint (when device is unregistered)
     */
    public void deleteEndpoint(String endpointArn) {
        try {
            DeleteEndpointRequest request = DeleteEndpointRequest.builder()
                .endpointArn(endpointArn)
                .build();

            snsClient.deleteEndpoint(request);
            log.info("Deleted SNS endpoint: {}", endpointArn);

        } catch (Exception e) {
            log.error("Failed to delete SNS endpoint: {}", endpointArn, e);
        }
    }

    /**
     * Check if SNS is healthy
     */
    public boolean isHealthy() {
        try {
            // Try to list platform applications to verify connection
            snsClient.listPlatformApplications();
            return true;
        } catch (Exception e) {
            log.error("SNS health check failed", e);
            return false;
        }
    }
}
