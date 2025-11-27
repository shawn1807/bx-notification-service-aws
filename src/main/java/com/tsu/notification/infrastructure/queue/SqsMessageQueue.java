package com.tsu.notification.infrastructure.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AWS SQS implementation of MessageQueue
 */
@Component
@ConditionalOnProperty(name = "queue.provider", havingValue = "sqs", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class SqsMessageQueue implements MessageQueue {

    private final SqsClient sqsClient;

    @Override
    public String sendMessage(String queueUrl, String messageBody, Map<String, String> attributes) {
        return sendMessageWithDelay(queueUrl, messageBody, 0, attributes);
    }

    @Override
    public String sendMessageWithDelay(String queueUrl, String messageBody, int delaySeconds, Map<String, String> attributes) {
        try {
            var requestBuilder = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .delaySeconds(delaySeconds);

            // Add message attributes if provided
            if (attributes != null && !attributes.isEmpty()) {
                Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
                attributes.forEach((key, value) ->
                    messageAttributes.put(key, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(value)
                        .build())
                );
                requestBuilder.messageAttributes(messageAttributes);
            }

            SendMessageResponse response = sqsClient.sendMessage(requestBuilder.build());

            log.debug("Message sent to SQS: messageId={}, queueUrl={}",
                response.messageId(), queueUrl);

            return response.messageId();

        } catch (SqsException e) {
            log.error("Failed to send message to SQS: queueUrl={}, error={}",
                queueUrl, e.awsErrorDetails().errorMessage(), e);
            throw new QueueException("Failed to send message to SQS", e);
        } catch (Exception e) {
            log.error("Unexpected error sending message to SQS: queueUrl={}", queueUrl, e);
            throw new QueueException("Unexpected error sending message to SQS", e);
        }
    }

    @Override
    public boolean isHealthy(String queueUrl) {
        try {
            GetQueueAttributesRequest request = GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                .build();

            sqsClient.getQueueAttributes(request);
            return true;

        } catch (SqsException e) {
            log.warn("SQS health check failed: queueUrl={}, error={}",
                queueUrl, e.awsErrorDetails().errorMessage());
            return false;
        } catch (Exception e) {
            log.warn("SQS health check failed: queueUrl={}", queueUrl, e);
            return false;
        }
    }
}
