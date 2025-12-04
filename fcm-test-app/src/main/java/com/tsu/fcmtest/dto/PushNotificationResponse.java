package com.tsu.fcmtest.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for push notification operations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PushNotificationResponse {

    /**
     * Success status
     */
    private boolean success;

    /**
     * FCM message ID (if successful)
     */
    private String messageId;

    /**
     * Error message (if failed)
     */
    private String error;

    /**
     * Error code (if failed)
     */
    private String errorCode;

    public static PushNotificationResponse success(String messageId) {
        return new PushNotificationResponse(true, messageId, null, null);
    }

    public static PushNotificationResponse failure(String error, String errorCode) {
        return new PushNotificationResponse(false, null, error, errorCode);
    }
}
