package com.tsu.notification.infrastructure.queue;

import com.tsu.common.enums.MessageChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    private String eventType;


    /**
     * Partition key for ordering (optional)
     */
    private String partitionKey;
}
