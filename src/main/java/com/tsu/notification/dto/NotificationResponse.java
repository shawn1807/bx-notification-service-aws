package com.tsu.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private UUID id;
    private NotificationType type;
    private NotificationPriority priority;
    private String userId;
    private String recipientEmail;
    private String recipientPhone;
    private String subject;
    private String body;
    private Map<String, Object> metadata;
    private Instant scheduleAt;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;
    private String idempotencyKey;
    private List<DeliveryResponse> deliveries;
}
