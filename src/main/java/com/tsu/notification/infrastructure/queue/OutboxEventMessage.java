package com.tsu.notification.infrastructure.queue;

import com.tsu.enums.MessageChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Message payload for outbox events sent to queue
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEventMessage {

    /**
     * ID of the outbox event
     */
    private UUID eventId;

    /**
     * Type of aggregate (e.g., NOTIFICATION)
     */
    private MessageChannel messageType;

    /**
     * ID of the aggregate
     */
    private UUID messageId;

    /**
     * Type of event (e.g., NOTIFICATION_CREATED, DELIVERY_REQUESTED)
     */
    private String eventType;

    /**
     * Event payload/data
     */
    private Map<String, Object> payload;

    /**
     * Partition key for ordering (optional)
     */
    private String partitionKey;
}
