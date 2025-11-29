package com.tsu.notification.infrastructure.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tsu.notification.entities.OutboxMessageTb;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for publishing messages to queue
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueuePublisher {

    private final MessageQueue messageQueue;
    private final ObjectMapper objectMapper;

    @Value("${queue.notification-events.queue-url}")
    private String notificationEventsQueueUrl;

    /**
     * Publish outbox event to notification events queue
     *
     * @param event the outbox event to publish
     * @return message ID from the queue
     */
    public String publishOutboxEvent(OutboxMessageTb event) {
        try {
            // Convert outbox event to message payload
            OutboxEventMessage payload = OutboxEventMessage.builder()
                .eventId(event.getId())
                .messageType(event.getMessageType())
                .messageId(event.getMessageId())
                .eventType(event.getEventType())
                .partitionKey(event.getPartitionKey())
                .build();

            // Wrap in queue message
            QueueMessage<OutboxEventMessage> queueMessage = QueueMessage.create(
                event.getEventType(),
                payload
            );

            // Serialize to JSON
            String messageBody = objectMapper.writeValueAsString(queueMessage);

            // Add message attributes
            Map<String, String> attributes = new HashMap<>();
            attributes.put("eventType", event.getEventType());
            attributes.put("messageType", event.getMessageType().name());
            attributes.put("messageId", event.getMessageId().toString());

            // Send to queue
            String messageId = messageQueue.sendMessage(
                notificationEventsQueueUrl,
                messageBody,
                attributes
            );

            log.info("Published outbox event to queue: eventId={}, queueMessageId={}, eventType={}",
                event.getId(), messageId, event.getEventType());

            return messageId;

        } catch (Exception e) {
            log.error("Failed to publish outbox event to queue: eventId={}", event.getId(), e);
            throw new QueueException("Failed to publish outbox event to queue", e);
        }
    }

}
