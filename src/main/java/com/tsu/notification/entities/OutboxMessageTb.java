package com.tsu.notification.entities;

import com.tsu.enums.MessageChannel;
import com.tsu.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transactional outbox pattern for reliable event publishing
 * Ensures events are persisted atomically with business data
 */
@Entity
@Table(name = "outbox_message")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutboxMessageTb {

    @Id
    @Column(name = "id")
    private UUID id;

    @Enumerated(value = EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 100)
    private MessageChannel messageType;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "processed_date")
    private LocalDateTime processedDate;

    @Column(name = "processing_started_date")
    private LocalDateTime processingStartedDate;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 3;

    @Column(name = "next_attempt_date")
    private LocalDateTime nextAttemptDate;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate ;

    @Column(name = "partition_key", length = 50)
    private String partitionKey;

    public void markAsProcessing() {
        this.processingStartedDate = LocalDateTime.now();
    }

    public void markAsProcessed() {
        this.status = OutboxStatus.PROCESSED;
        this.processedDate = LocalDateTime.now();
    }

    public void markAsFailed(String error) {
        this.attemptCount++;
        this.lastError = error;

        if (attemptCount >= maxAttempts) {
            this.status = OutboxStatus.FAILED;
            this.nextAttemptDate = null;
        } else {
            // Exponential backoff: 1min, 5min, 15min
            long delayMinutes = (long) Math.pow(5, attemptCount);
            this.nextAttemptDate = LocalDateTime.now().plusSeconds(delayMinutes * 60);
        }
    }

    public boolean isReadyForProcessing() {
        return status == OutboxStatus.PENDING ||
               (status == OutboxStatus.FAILED &&
                attemptCount < maxAttempts &&
                       nextAttemptDate != null &&
                (nextAttemptDate.isBefore(LocalDateTime.now()) || nextAttemptDate.equals(LocalDateTime.now())));
    }
}
