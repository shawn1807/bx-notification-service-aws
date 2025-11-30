package com.tsu.notification.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AWS SNS implementation for sending SMS
 *
 * Features:
 * - Transactional and promotional SMS
 * - SMS attributes (sender ID, max price, etc.)
 * - Delivery status tracking
 * - Support for different message types
 */
@Component
@ConditionalOnProperty(name = "notification.channels.sms.provider", havingValue = "AWS_SNS")
@RequiredArgsConstructor
@Slf4j
public class AwsSnsSenderAdapter implements SmsSenderAdapter {

    private final SnsClient snsClient;

    @Value("${notification.channels.sms.sender-id:#{null}}")
    private String senderId;

    @Value("${notification.channels.sms.sms-type:Transactional}")
    private String smsType; // Transactional or Promotional

    @Value("${notification.channels.sms.max-price:1.00}")
    private String maxPrice;

    @Override
    public SendResult sendSms(String phoneNumber, String message, Map<String, Object> metadata) {
        log.info("Sending SMS via AWS SNS to: {}", phoneNumber);

        // Validate phone number
        if (!isValidPhoneNumber(phoneNumber)) {
            log.error("Invalid phone number format: {}", phoneNumber);
            return SendResult.failure(
                "Invalid phone number format. Expected E.164 format (e.g., +1234567890)",
                "INVALID_PHONE_NUMBER"
            );
        }

        try {
            // Build SMS attributes
            Map<String, MessageAttributeValue> attributes = buildSmsAttributes(metadata);

            // Build request
            PublishRequest request = PublishRequest.builder()
                .message(message)
                .phoneNumber(phoneNumber)
                .messageAttributes(attributes)
                .build();

            // Send SMS
            PublishResponse response = snsClient.publish(request);
            String messageId = response.messageId();

            log.info("SMS sent successfully via AWS SNS: messageId={}, phoneNumber={}",
                messageId, phoneNumber);

            return SendResult.success(messageId, "AWS_SNS");

        } catch (InvalidParameterException e) {
            log.error("AWS SNS invalid parameter: phoneNumber={}, error={}",
                phoneNumber, e.awsErrorDetails().errorMessage());
            return SendResult.failure(
                "Invalid SMS parameter: " + e.awsErrorDetails().errorMessage(),
                "SNS_INVALID_PARAMETER"
            );

        } catch (SnsException e) {
            log.error("AWS SNS error: {}", e.awsErrorDetails().errorMessage(), e);

            // Check for specific error codes
            String errorCode = e.awsErrorDetails().errorCode();
            if ("Throttling".equals(errorCode) || "TooManyRequestsException".equals(errorCode)) {
                return SendResult.failure(
                    "SNS rate limit exceeded",
                    "SNS_RATE_LIMIT"
                );
            }

            return SendResult.failure(
                "SNS error: " + e.awsErrorDetails().errorMessage(),
                "SNS_ERROR"
            );

        } catch (Exception e) {
            log.error("Unexpected error sending SMS via SNS", e);
            return SendResult.failure(e.getMessage(), "SMS_SEND_ERROR");
        }
    }

    /**
     * Build SMS attributes for AWS SNS
     */
    private Map<String, MessageAttributeValue> buildSmsAttributes(Map<String, Object> metadata) {
        Map<String, MessageAttributeValue> attributes = new HashMap<>();

        // SMS Type: Transactional (higher priority, better delivery) or Promotional
        attributes.put("AWS.SNS.SMS.SMSType", MessageAttributeValue.builder()
            .dataType("String")
            .stringValue(smsType)
            .build());

        // Sender ID (not supported in all countries, e.g., USA)
        if (senderId != null && !senderId.isBlank()) {
            attributes.put("AWS.SNS.SMS.SenderID", MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(senderId)
                .build());
        }

        // Max price per SMS (to prevent unexpected costs)
        attributes.put("AWS.SNS.SMS.MaxPrice", MessageAttributeValue.builder()
            .dataType("Number")
            .stringValue(maxPrice)
            .build());

        // Custom sender ID from metadata (overrides default)
        if (metadata != null && metadata.containsKey("senderId")) {
            attributes.put("AWS.SNS.SMS.SenderID", MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(metadata.get("senderId").toString())
                .build());
        }

        // Custom SMS type from metadata
        if (metadata != null && metadata.containsKey("smsType")) {
            attributes.put("AWS.SNS.SMS.SMSType", MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(metadata.get("smsType").toString())
                .build());
        }

        return attributes;
    }




    /**
     * Check if SNS is healthy
     */
    public boolean isHealthy() {
        try {
            // Try to get SMS attributes to verify connection
            snsClient.getSMSAttributes();
            return true;
        } catch (Exception e) {
            log.error("SNS health check failed", e);
            return false;
        }
    }
}
