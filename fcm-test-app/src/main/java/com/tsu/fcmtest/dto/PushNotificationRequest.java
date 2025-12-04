package com.tsu.fcmtest.dto;

import lombok.Data;

import java.util.Map;

/**
 * Request DTO for sending push notifications
 */
@Data
public class PushNotificationRequest {

    /**
     * FCM device token
     */
    private String token;

    /**
     * Notification title
     */
    private String title;

    /**
     * Notification body/message
     */
    private String body;

    /**
     * Additional data payload (optional)
     */
    private Map<String, String> data;

    /**
     * Image URL for rich notifications (optional)
     */
    private String imageUrl;
}
