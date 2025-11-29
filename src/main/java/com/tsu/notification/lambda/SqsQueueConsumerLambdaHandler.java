package com.tsu.notification.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tsu.notification.dto.OutboxEvent;
import com.tsu.notification.dto.OutboxEventDto;
import com.tsu.notification.entities.OutboxEvent;
import com.tsu.notification.infrastructure.dispatcher.NotificationEventHandler;
import com.tsu.notification.infrastructure.queue.OutboxEventMessage;
import com.tsu.notification.infrastructure.queue.QueueMessage;
import com.tsu.notification.val.OutboxEventVal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * AWS Lambda handler for SQS events
 *
 * Processes notification events from SQS queue
 *
 * Usage:
 * - Handler: com.tsu.notification.lambda.SqsQueueConsumerLambdaHandler::handleRequest
 * - Runtime: java21
 * - Trigger: SQS Queue (notification-events)
 * - Batch size: 10
 * - Batch window: 5 seconds
 */
@Slf4j
public class SqsQueueConsumerLambdaHandler implements RequestHandler<SQSEvent, Void> {

    private static ApplicationContext applicationContext;
    private static NotificationEventHandler eventHandler;
    private static ObjectMapper objectMapper;

    static {
        // Initialize Spring context once (Lambda container reuse)
        try {
            log.info("Initializing Spring Application Context for SQS Lambda");
            applicationContext = new AnnotationConfigApplicationContext(
                LambdaConfiguration.class
            );
            eventHandler = applicationContext.getBean(NotificationEventHandler.class);
            objectMapper = applicationContext.getBean(ObjectMapper.class);
            log.info("Spring Application Context initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Spring Application Context", e);
            throw new RuntimeException("Lambda initialization failed", e);
        }
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        log.info("SQS Lambda invoked: messageCount={}, requestId={}",
            event.getRecords().size(), context.getRequestId());

        for (SQSEvent.SQSMessage message : event.getRecords()) {
            processMessage(message, context);
        }

        return null;
    }

    /**
     * Process a single SQS message
     */
    private void processMessage(SQSEvent.SQSMessage message, Context context) {
        try {
            log.info("Processing SQS message: messageId={}", message.getMessageId());

            // Parse queue message
            QueueMessage<OutboxEventMessage> queueMessage = objectMapper.readValue(
                message.getBody(),
                objectMapper.getTypeFactory().constructParametricType(
                    QueueMessage.class,
                    OutboxEventMessage.class
                )
            );

            OutboxEventMessage eventMessage = queueMessage.getPayload();

            // Convert to OutboxEvent
            OutboxEventVal outboxEvent = new OutboxEventVal(eventMessage.getEventId(),eventMessage.getMessageType(),eventMessage.getMessageId(), eventMessage.getEventType());

            // Process event
            eventHandler.handle(outboxEvent);

            log.info("SQS message processed successfully: messageId={}, eventType={}",
                message.getMessageId(), eventMessage.getEventType());

        } catch (Exception e) {
            log.error("Failed to process SQS message: messageId={}", message.getMessageId(), e);
            // Lambda will retry automatically based on SQS queue configuration
            // or send to DLQ after max retries
            throw new RuntimeException("Failed to process message", e);
        }
    }
}
