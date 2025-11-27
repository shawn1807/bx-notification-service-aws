package com.tsu.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNotificationRequest {

    @NotNull(message = "Notification type is required")
    private NotificationType type;

    @Builder.Default
    private NotificationPriority priority = NotificationPriority.NORMAL;

    private String userId;

    private String recipientEmail;

    private String recipientPhone;

    private String subject;

    @NotBlank(message = "Notification body is required")
    private String body;

    private Map<String, Object> metadata;

    @NotEmpty(message = "At least one delivery channel is required")
    private List<DeliveryChannel> channels;

    private Instant scheduleAt;

    private Instant expiresAt;

    private String idempotencyKey;

    private String createdBy;

    /**
     * Validate that at least one recipient is provided
     */
    public boolean hasRecipient() {
        return recipientEmail != null || recipientPhone != null || userId != null;
    }
}
