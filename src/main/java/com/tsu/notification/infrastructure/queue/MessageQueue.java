package com.tsu.notification.infrastructure.queue;

import java.util.Map;

/**
 * Abstraction for message queue operations
 * Supports multiple implementations (SQS, RabbitMQ, etc.)
 */
public interface MessageQueue {

    /**
     * Send a message to the queue
     *
     * @param queueName the name/URL of the queue
     * @param messageBody the message body
     * @param attributes optional message attributes
     * @return message ID assigned by the queue
     */
    String sendMessage(String queueName, String messageBody, Map<String, String> attributes);

    /**
     * Send a message with delay
     *
     * @param queueName the name/URL of the queue
     * @param messageBody the message body
     * @param delaySeconds delay in seconds before message becomes available
     * @param attributes optional message attributes
     * @return message ID assigned by the queue
     */
    String sendMessageWithDelay(String queueName, String messageBody, int delaySeconds, Map<String, String> attributes);

    /**
     * Check if queue is available/healthy
     *
     * @param queueName the name/URL of the queue
     * @return true if queue is accessible
     */
    boolean isHealthy(String queueName);
}
