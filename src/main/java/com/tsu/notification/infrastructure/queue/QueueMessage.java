package com.tsu.notification.infrastructure.queue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Generic queue message wrapper
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueMessage<T> {

    /**
     * Unique message ID
     */
    private String messageId;

    /**
     * Message type for routing
     */
    private String messageType;

    /**
     * The actual payload
     */
    private T payload;

    /**
     * Optional metadata/attributes
     */
    private Map<String, String> attributes;

    /**
     * Timestamp when message was created
     */
    private Long timestamp;

    /**
     * Create a new message with generated ID and timestamp
     */
    public static <T> QueueMessage<T> create(String messageType, T payload, Map<String, String> attributes) {
        return QueueMessage.<T>builder()
            .messageId(UUID.randomUUID().toString())
            .messageType(messageType)
            .payload(payload)
            .attributes(attributes)
            .timestamp(System.currentTimeMillis())
            .build();
    }

    /**
     * Create a simple message without attributes
     */
    public static <T> QueueMessage<T> create(String messageType, T payload) {
        return create(messageType, payload, Map.of());
    }
}
