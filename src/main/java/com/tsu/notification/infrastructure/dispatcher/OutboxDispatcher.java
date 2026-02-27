package com.tsu.notification.infrastructure.dispatcher;

import com.tsu.notification.entities.OutboxMessageTb;
import com.tsu.notification.infrastructure.queue.QueuePublisher;
import com.tsu.notification.repo.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Dispatcher that polls outbox events and publishes them to a message queue
 * Uses pessimistic locking with SKIP LOCKED to prevent concurrent processing
 * <p>
 * Separation of concerns:
 * - OutboxDispatcher: Polls DB and publishes to queue (producer)
 * - QueueConsumer: Consumes from queue and routes to handlers (consumer)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxDispatcher {

    private final OutboxMessageRepository outboxMessageRepository;
    private final QueuePublisher queuePublisher;

    private static final int BATCH_SIZE = 100;

    /**
     * Poll outbox events every 5 seconds
     * Uses FOR UPDATE SKIP LOCKED to handle concurrent dispatchers safely
     */
    @Scheduled(fixedDelay = 5000)
    public void pollAndDispatch() {
        try {
            List<OutboxMessageTb> events = fetchPendingEvents();
            if (events.isEmpty()) {
                log.trace("No pending outbox events");
                return;
            }
            log.info("Processing {} outbox events", events.size());
            events.forEach(this::processEvent);
        } catch (Exception e) {
            log.error("Error in outbox dispatcher", e);
        }
    }

    /**
     * Fetch pending events with pessimistic lock
     */
    @Transactional
    protected List<OutboxMessageTb> fetchPendingEvents() {
        return outboxMessageRepository.findOutboxMessageForUpdate(LocalDateTime.now(), BATCH_SIZE);
    }

    /**
     * Process a single outbox event by publishing to queue
     */
    @Transactional
    protected void processEvent(OutboxMessageTb event) {
        try {
            log.debug("Publishing outbox event to queue: id={}, type={}, aggregateId={}",
                    event.getId(), event.getEventType(), event.getMessageId());

            event.setProcessingStartedDate(Instant.now());
            outboxMessageRepository.save(event);
            // Publish to message queue (decouples from event handler)
            String queueId = queuePublisher.publishOutboxEvent(event);
            log.info("Outbox event published to queue: id={}, queueId={}", event.getId(), queueId);
        } catch (Exception e) {
            log.error("Failed to publish outbox event to queue: id={}", event.getId(), e);
            event.markAsFailed(e.getMessage());
            outboxMessageRepository.save(event);
        }
    }

    /**
     * Cleanup old processed events (run daily)
     */
    @Scheduled(cron = "0 0 2 * * *") // 2 AM daily
    @Transactional
    public void cleanupProcessedEvents() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(7 * 24 * 3600); // 7 days ago
        int deleted = outboxMessageRepository.deleteProcessedMessagesBefore(threshold);
        log.info("Deleted {} old processed outbox events", deleted);
    }

    /**
     * Reset stuck events (run every hour)
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public void resetStuckMessages() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(3600); // 1 hour ago
        List<UUID> stuckMessages = outboxMessageRepository.findStuckMessages(threshold)
                .map(OutboxMessageTb::getId)
                .toList();
        if (!stuckMessages.isEmpty()) {
            int reset = outboxMessageRepository.resetStuckMessages(stuckMessages);
            log.warn("Reset {} stuck outbox events", reset);
        }
    }
}
