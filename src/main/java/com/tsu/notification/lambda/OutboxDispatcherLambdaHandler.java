package com.tsu.notification.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.tsu.notification.infrastructure.dispatcher.OutboxDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * AWS Lambda handler for scheduled outbox processing
 *
 * Polls outbox table and publishes events to SQS queue
 *
 * Usage:
 * - Handler: com.tsu.notification.lambda.OutboxDispatcherLambdaHandler::handleRequest
 * - Runtime: java21
 * - Trigger: EventBridge (CloudWatch Events) - Scheduled expression: rate(1 minute)
 * - Timeout: 5 minutes
 * - Memory: 1024 MB
 *
 * Note: This Lambda replaces the Spring @Scheduled dispatcher in serverless deployments
 */
@Slf4j
public class OutboxDispatcherLambdaHandler implements RequestHandler<ScheduledEvent, String> {

    private static ApplicationContext applicationContext;
    private static OutboxDispatcher outboxDispatcher;

    static {
        // Initialize Spring context once (Lambda container reuse)
        try {
            log.info("Initializing Spring Application Context for Outbox Dispatcher Lambda");
            applicationContext = new AnnotationConfigApplicationContext(
                LambdaConfiguration.class
            );
            outboxDispatcher = applicationContext.getBean(OutboxDispatcher.class);
            log.info("Spring Application Context initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Spring Application Context", e);
            throw new RuntimeException("Lambda initialization failed", e);
        }
    }

    @Override
    public String handleRequest(ScheduledEvent event, Context context) {
        log.info("Outbox Dispatcher Lambda invoked: time={}, requestId={}",
            event.getTime(), context.getRequestId());

        long startTime = System.currentTimeMillis();
        int processedCount = 0;

        try {
            // Poll and dispatch outbox events
            outboxDispatcher.pollAndDispatch();

            long duration = System.currentTimeMillis() - startTime;
            String result = String.format(
                "Outbox dispatch completed: duration=%dms, requestId=%s",
                duration, context.getRequestId()
            );

            log.info(result);
            return result;

        } catch (Exception e) {
            log.error("Error in outbox dispatcher", e);
            throw new RuntimeException("Outbox dispatch failed", e);
        }
    }
}
