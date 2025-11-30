package com.tsu.notification.infrastructure.adapter;

import com.tsu.notification.entities.DevicePushTokenTb;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter for sending push notifications via FCM and APNs
 * This is a placeholder implementation - integrate with actual push providers
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PushSenderAdapter {

    /**
     * Send push notification to a device
     *
     * @param deviceToken Device push token
     * @param title       Notification title
     * @param body        Notification body
     * @param metadata    Additional data payload
     * @return SendResult with provider message ID
     */
    public SendResult sendPush(
        DevicePushTokenTb deviceToken,
        String title,
        String body,
        Map<String, Object> metadata
    ) {
        log.info("Sending push notification: platform={}, deviceId={}",
            deviceToken.getPlatform(), deviceToken.getDeviceId());

        try {
            return switch (deviceToken.getPlatform()) {
                case FCM -> sendFcmPush(deviceToken.getToken(), title, body, metadata);
                case APNS -> sendApnsPush(deviceToken.getToken(), title, body, metadata);
            };

        } catch (Exception e) {
            log.error("Failed to send push notification", e);
            return SendResult.failure(e.getMessage(), "PUSH_SEND_ERROR");
        }
    }

    /**
     * Send push via Firebase Cloud Messaging (Android)
     */
    private SendResult sendFcmPush(String token, String title, String body, Map<String, Object> metadata) {
        log.info("Sending FCM push");

        try {
            // TODO: Integrate with Firebase Admin SDK
            // Dependency: com.google.firebase:firebase-admin

            /*
            Example FCM HTTP v1 implementation:

            FirebaseApp firebaseApp = FirebaseApp.getInstance();

            Message message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build())
                .putAllData(convertMetadataToStringMap(metadata))
                .setAndroidConfig(AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .build())
                .build();

            String messageId = FirebaseMessaging.getInstance(firebaseApp)
                .send(message);

            return SendResult.success(messageId, "FCM");
            */

            // Simulate invalid token (for testing)
            if (token.contains("invalid")) {
                return SendResult.failure("Invalid registration token", "INVALID_TOKEN");
            }

            // Simulate successful send
            String messageId = "fcm-" + UUID.randomUUID();
            log.info("FCM push sent successfully: messageId={}", messageId);

            return SendResult.success(messageId, "MOCK_FCM");

        } catch (Exception e) {
            log.error("FCM send failed", e);
            return SendResult.failure(e.getMessage(), "FCM_ERROR");
        }
    }

    /**
     * Send push via Apple Push Notification Service (iOS)
     */
    private SendResult sendApnsPush(String token, String title, String body, Map<String, Object> metadata) {
        log.info("Sending APNs push");

        try {
            // TODO: Integrate with APNs
            // Library: com.eatthepath:pushy

            /*
            Example APNs implementation using Pushy:

            ApnsClient apnsClient = new ApnsClientBuilder()
                .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                .setSigningKey(ApnsSigningKey.loadFromPkcs8File(
                    new File(keyPath), teamId, keyId))
                .build();

            SimpleApnsPushNotification pushNotification =
                new SimpleApnsPushNotification(
                    token,
                    "com.yourcompany.app",
                    buildApnsPayload(title, body, metadata)
                );

            PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>>
                sendNotificationFuture = apnsClient.sendNotification(pushNotification);

            PushNotificationResponse<SimpleApnsPushNotification> response =
                sendNotificationFuture.get();

            if (response.isAccepted()) {
                return SendResult.success(response.getApnsId().toString(), "APNS");
            } else {
                return SendResult.failure(
                    response.getRejectionReason(),
                    "APNS_REJECTED"
                );
            }
            */

            // Simulate successful send
            String messageId = "apns-" + UUID.randomUUID();
            log.info("APNs push sent successfully: messageId={}", messageId);

            return SendResult.success(messageId, "MOCK_APNS");

        } catch (Exception e) {
            log.error("APNs send failed", e);
            return SendResult.failure(e.getMessage(), "APNS_ERROR");
        }
    }

    /**
     * Build FCM payload
     */
    private Map<String, Object> buildFcmPayload(String title, String body, Map<String, Object> metadata) {
        Map<String, Object> payload = new HashMap<>();

        Map<String, String> notification = new HashMap<>();
        notification.put("title", title);
        notification.put("body", body);
        payload.put("notification", notification);

        if (metadata != null && !metadata.isEmpty()) {
            payload.put("data", metadata);
        }

        return payload;
    }

    /**
     * Build APNs payload
     */
    private String buildApnsPayload(String title, String body, Map<String, Object> metadata) {
        // APNs uses JSON format
        StringBuilder json = new StringBuilder("{");
        json.append("\"aps\":{");
        json.append("\"alert\":{");
        json.append("\"title\":\"").append(escapeJson(title)).append("\",");
        json.append("\"body\":\"").append(escapeJson(body)).append("\"");
        json.append("},");
        json.append("\"sound\":\"default\",");
        json.append("\"badge\":1");
        json.append("}");

        if (metadata != null && !metadata.isEmpty()) {
            // Add custom data
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                json.append(",\"").append(entry.getKey()).append("\":\"")
                    .append(entry.getValue()).append("\"");
            }
        }

        json.append("}");
        return json.toString();
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    private Map<String, String> convertMetadataToStringMap(Map<String, Object> metadata) {
        if (metadata == null) return Map.of();

        Map<String, String> result = new HashMap<>();
        metadata.forEach((key, value) ->
            result.put(key, value != null ? value.toString() : "")
        );
        return result;
    }
}
