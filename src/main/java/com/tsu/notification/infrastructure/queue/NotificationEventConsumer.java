package com.tsu.notification.infrastructure.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tsu.notification.infrastructure.dispatcher.OutboxEventMessageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;

/**
 * Consumer that polls notification events from SQS queue and processes them
 * <p>
 * Separation of concerns:
 * - OutboxDispatcher: Polls DB and publishes to queue (producer)
 * - NotificationEventConsumer: Consumes from queue and routes to handlers (consumer)
 * <p>
 * Benefits:
 * - Decouples outbox processing from event handling
 * - Can scale independently (more consumers if needed)
 * - Queue provides buffering and reliability
 * - Can leverage queue features (retries, DLQ, etc.)
 */
@Component
@ConditionalOnProperty(name = "queue.consumer.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {

    private final SqsClient sqsClient;
    private final OutboxEventMessageHandler outboxEventHandler;
    private final ObjectMapper objectMapper;

    @Value("${queue.notification-events.queue-url}")
    private String queueUrl;

    @Value("${queue.notification-events.max-messages:10}")
    private int maxMessages;

    @Value("${queue.notification-events.wait-time-seconds:20}")
    private int waitTimeSeconds;

    @Value("${queue.notification-events.visibility-timeout:30}")
    private int visibilityTimeout;

    /**
     * Poll and process messages from queue every 5 seconds
     * Uses long polling for efficiency
     */
    @Scheduled(fixedDelay = 5000)
    public void pollAndProcess() {
        try {
            List<Message> messages = receiveMessages();

            if (messages.isEmpty()) {
                log.trace("No messages in queue");
                return;
            }

            log.info("Received {} messages from queue", messages.size());

            for (Message message : messages) {
                processMessage(message);
            }

        } catch (Exception e) {
            log.error("Error polling queue", e);
        }
    }

    /**
     * Receive messages from SQS queue
     */
    private List<Message> receiveMessages() {
        try {
            ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(maxMessages)
                    .waitTimeSeconds(waitTimeSeconds)
                    .visibilityTimeout(visibilityTimeout)
                    .messageAttributeNames("All")
                    .build();

            ReceiveMessageResponse response = sqsClient.receiveMessage(request);
            return response.messages();

        } catch (SqsException e) {
            log.error("Failed to receive messages from SQS: error={}",
                    e.awsErrorDetails().errorMessage(), e);
            return List.of();
        }
    }

    /**
     * Process a single message
     */
    private void processMessage(Message message) {
        try {
            log.debug("Processing message: messageId={}", message.messageId());

            // Parse queue message
            QueueMessage<OutboxEventMessage> queueMessage = objectMapper.readValue(
                    message.body(),
                    objectMapper.getTypeFactory().constructParametricType(
                            QueueMessage.class,
                            OutboxEventMessage.class
                    )
            );

            OutboxEventMessage eventMessage = queueMessage.getPayload();


            // Route to handler
            outboxEventHandler.handle(eventMessage);

            // Delete message from queue on success
            deleteMessage(message.receiptHandle());

            log.info("Message processed successfully: messageId={}, eventType={}",
                    message.messageId(), eventMessage.getEventType());

        } catch (Exception e) {
            log.error("Failed to process message: messageId={}", message.messageId(), e);
            // Message will become visible again after visibility timeout
            // SQS will retry automatically based on queue configuration
            // Consider implementing dead letter queue (DLQ) for failed messages
        }
    }


    /**
     * Delete message from queue
     */
    private void deleteMessage(String receiptHandle) {
        try {
            DeleteMessageRequest request = DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(receiptHandle)
                    .build();

            sqsClient.deleteMessage(request);

        } catch (SqsException e) {
            log.error("Failed to delete message from SQS: error={}",
                    e.awsErrorDetails().errorMessage(), e);
        }
    }
}
