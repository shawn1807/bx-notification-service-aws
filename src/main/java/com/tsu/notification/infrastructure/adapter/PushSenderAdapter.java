package com.tsu.notification.infrastructure.adapter;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.concurrent.PushNotificationFuture;
import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.tsu.notification.entities.DevicePushTokenTb;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

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
            FirebaseMessaging messaging = FirebaseMessaging.getInstance();
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putAllData(convertMetadataToStringMap(metadata))
                    .build();
            String messageId = messaging.send(message);
            return SendResult.success(messageId, "FCM");
        } catch (FirebaseMessagingException e) {
            log.error("FCM send failed", e);
            ErrorCode error = e.getErrorCode();  // ex: "UNREGISTERED", "INVALID_ARGUMENT"
            log.warn("FCM error {} for token {}", error, token);
            boolean isPermanent = false;
            switch (error) {
                case INVALID_ARGUMENT:
                case NOT_FOUND:
                    isPermanent = true;
                default:
            }
            return SendResult.failure(e.getMessage(), error.name(), isPermanent);
        }
    }

    private boolean isFCMPermanentError(String error) {
        return "UNREGISTERED".equals(error) ||
                "INVALID_ARGUMENT".equals(error) ||
                "REGISTRATION_TOKEN_NOT_REGISTERED".equals(error); // Android/FCM variant
    }

    /**
     * Send push via Apple Push Notification Service (iOS)
     */
    private SendResult sendApnsPush(String token, String title, String body, Map<String, Object> metadata) {
        log.info("Sending APNs push");

        try {
            String teamId = "YOUR_TEAM_ID";
            String keyId = "YOUR_KEY_ID";
            File p8KeyFile = new File("AuthKey_YOUR_KEY_ID.p8");
            String bundleId = "com.yourapp.mobile"; // apns-topic

            ApnsSigningKey signingKey = ApnsSigningKey.loadFromPkcs8File(
                    p8KeyFile, teamId, keyId);
            ApnsClient apnsClient = new ApnsClientBuilder()
                    .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                    .setSigningKey(signingKey)
                    .build();
            String payload = new SimpleApnsPayloadBuilder()
                    .setAlertTitle(title)
                    .setAlertBody(body)
                    .setSound("default")
                    .build();

            SimpleApnsPushNotification pushNotification =
                    new SimpleApnsPushNotification(token, bundleId, payload);

            final PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>> future =
                    apnsClient.sendNotification(pushNotification);

            // Handle async response
            final PushNotificationResponse<SimpleApnsPushNotification> response = future.get();

            if (response.isAccepted()) {
                System.out.println("APNS accepted push");
            } else {
                System.out.println("APNS rejected push: " + response.getRejectionReason());

                if (response.getTokenInvalidationTimestamp().isPresent()) {
                    // <<< THIS IS IMPORTANT >>>
                    // You can mark token as invalid in DB
                    System.out.println("Token is invalid -> deactivate");
                }
            }
            return SendResult.success(response.getApnsId().toString(), "MOCK_APNS");

        } catch (Exception e) {
            log.error("APNs send failed", e);
            return SendResult.failure(e.getMessage(), "APNS_ERROR");
        }
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
