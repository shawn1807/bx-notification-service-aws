package com.tsu.notification.service;

import com.tsu.auth.security.AppSettings;
import com.tsu.enums.MessageChannel;
import com.tsu.notification.entities.OutboxMessageTb;
import com.tsu.enums.OutboxStatus;
import com.tsu.notification.repo.OutboxMessageRepository;
import com.tsu.notification.val.OutboxEventVal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Publisher for outbox events
 * CRITICAL: Must be called within the same transaction as business logic
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxMessageRepository outboxMessageRepository;
    private final AppSettings settings;

    /**
     * Publish event to outbox within existing transaction
     * Uses MANDATORY to ensure it's called within a transaction
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEventVal publish(
            MessageChannel type,
            UUID messageId,
            Enum<?> eventType
    ) {
        log.debug("Publishing outbox event: type={}, aggregateId={}, eventType={}",
                type, messageId, eventType);
        OutboxMessageTb tb = new OutboxMessageTb();
        tb.setId(UUID.randomUUID());
        tb.setMessageType(type);
        tb.setMessageId(messageId);
        tb.setEventType(String.valueOf(eventType));
        tb.setStatus(OutboxStatus.PENDING);
        tb.setAttemptCount(0);
        tb.setMaxAttempts(settings.getOtpMaxAttempts());
        tb.setPartitionKey(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
        tb.setCreatedDate(LocalDateTime.now());
        outboxMessageRepository.save(tb);
        log.info("Outbox event published: id={}, type={}", tb.getId(), eventType);
        return new OutboxEventVal(type, messageId, String.valueOf(eventType), tb.getStatus());
    }



}
