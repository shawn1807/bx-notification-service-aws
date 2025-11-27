package com.tsu.notification.entities;

import com.tsu.enums.DeliveryStatus;
import com.tsu.enums.ReadStatus;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@ToString
@EqualsAndHashCode
@Entity
@Table(name = "notification")
public class NotificationRecipientTb {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status")
    private DeliveryStatus deliveryStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "read_status")
    private ReadStatus readStatus;

    @Column(name = "delivered_date", nullable = false)
    private LocalDateTime deliveredDate;

    @Column(name = "read_date", nullable = false)
    private LocalDateTime readDate;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;

    @Column(name = "next_attempt_date")
    private Instant nextAttemptDate;

    @Column(name = "last_attempted_date")
    private Instant lastAttemptedDate;

    @Column(name = "base_delay_seconds", nullable = false)
    private Integer baseDelaySeconds = 60;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "sent_date")
    private Instant sentDate;

    @Column(name = "failed_date")
    private Instant failedDate;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate = LocalDateTime.now();
}
