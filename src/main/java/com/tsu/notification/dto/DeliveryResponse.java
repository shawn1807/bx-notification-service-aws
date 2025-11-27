package com.tsu.notification.dto;

import com.tsu.notification.enums.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryResponse {

    private UUID id;
    private DeliveryChannel channel;
    private DeliveryStatus status;
    private String recipient;
    private String providerId;
    private String providerName;
    private Integer attemptCount;
    private Integer maxAttempts;
    private Instant nextAttemptAt;
    private String lastError;
    private Instant sentAt;
    private Instant deliveredAt;
    private Instant failedAt;
    private Instant createdAt;
}
