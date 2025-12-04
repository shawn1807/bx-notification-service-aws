package com.tsu.fcmtest.service;

import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.*;
import com.tsu.fcmtest.dto.PushNotificationRequest;
import com.tsu.fcmtest.dto.PushNotificationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for sending FCM push notifications
 */
@Service
@Slf4j
public class FcmService {

    /**
     * Send a push notification via FCM
     *
     * @param request Push notification request
     * @return Response with message ID or error
     */
    public PushNotificationResponse sendNotification(PushNotificationRequest request) {
        log.info("Sending FCM notification to token: {}", maskToken(request.getToken()));

        try {
            // Build notification
            Notification.Builder notificationBuilder = Notification.builder()
                    .setTitle(request.getTitle())
                    .setBody(request.getBody());

            // Add image if provided
            if (request.getImageUrl() != null && !request.getImageUrl().isEmpty()) {
                notificationBuilder.setImage(request.getImageUrl());
            }

            // Build message
            Message.Builder messageBuilder = Message.builder()
                    .setToken(request.getToken())
                    .setNotification(notificationBuilder.build());

            // Add data payload if provided
            if (request.getData() != null && !request.getData().isEmpty()) {
                messageBuilder.putAllData(request.getData());
            }

            // Configure Android-specific options
            messageBuilder.setAndroidConfig(
                    AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setSound("default")
                                    .setColor("#0080FF")
                                    .build())
                            .build()
            );

            // Configure iOS-specific options
            messageBuilder.setApnsConfig(
                    ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setSound("default")
                                    .setBadge(1)
                                    .build())
                            .build()
            );

            Message message = messageBuilder.build();

            // Send message
            String messageId = FirebaseMessaging.getInstance().send(message);
            log.info("Successfully sent FCM message with ID: {}", messageId);

            return PushNotificationResponse.success(messageId);

        } catch (FirebaseMessagingException e) {
            log.error("Failed to send FCM notification", e);
            ErrorCode errorCode = e.getErrorCode();
            String errorMessage = e.getMessage();

            // Check if it's a permanent error (invalid token)
            boolean isPermanentError = isPermanentError(errorCode);
            if (isPermanentError) {
                log.warn("Permanent FCM error - token should be removed: {}", errorCode);
            }

            return PushNotificationResponse.failure(errorMessage, errorCode.name());
        } catch (Exception e) {
            log.error("Unexpected error sending FCM notification", e);
            return PushNotificationResponse.failure(e.getMessage(), "INTERNAL_ERROR");
        }
    }

    /**
     * Send a simple notification (convenience method)
     */
    public PushNotificationResponse sendSimpleNotification(String token, String title, String body) {
        PushNotificationRequest request = new PushNotificationRequest();
        request.setToken(token);
        request.setTitle(title);
        request.setBody(body);
        return sendNotification(request);
    }

    /**
     * Send notification with custom data
     */
    public PushNotificationResponse sendNotificationWithData(
            String token,
            String title,
            String body,
            Map<String, String> data
    ) {
        PushNotificationRequest request = new PushNotificationRequest();
        request.setToken(token);
        request.setTitle(title);
        request.setBody(body);
        request.setData(data);
        return sendNotification(request);
    }

    /**
     * Check if error is permanent (token should be removed)
     */
    private boolean isPermanentError(ErrorCode errorCode) {
        return errorCode == ErrorCode.INVALID_ARGUMENT ||
                errorCode == ErrorCode.NOT_FOUND ||
                errorCode == ErrorCode.UNAVAILABLE; // UNREGISTERED maps to UNAVAILABLE
    }

    /**
     * Mask token for logging (show first 10 and last 4 characters)
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 20) {
            return "***";
        }
        return token.substring(0, 10) + "..." + token.substring(token.length() - 4);
    }
}
