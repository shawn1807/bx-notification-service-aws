package com.tsu.notification.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Mock implementation for SMS sending (for testing/development)
 */
@Component
@ConditionalOnProperty(name = "notification.channels.sms.provider", havingValue = "MOCK", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class MockSmsSenderAdapter implements SmsSenderAdapter {

    @Override
    public SendResult sendSms(String phoneNumber, String message, Map<String, Object> metadata) {
        log.info("MOCK: Sending SMS to: {}, message length: {}", phoneNumber, message.length());

        try {
            // Validate phone number
            if (!isValidPhoneNumber(phoneNumber)) {
                log.error("MOCK: Invalid phone number format: {}", phoneNumber);
                return SendResult.failure(
                    "Invalid phone number format",
                    "INVALID_PHONE_NUMBER"
                );
            }

            // Simulate rate limiting (for testing retry logic)
            if (Math.random() < 0.05) {
                log.warn("MOCK: Simulating rate limit");
                return SendResult.failure("Rate limit exceeded", "RATE_LIMIT");
            }

            // Simulate successful send
            String messageId = "mock-sms-" + UUID.randomUUID();
            log.info("MOCK: SMS sent successfully: messageId={}", messageId);

            return SendResult.success(messageId, "MOCK_SMS_PROVIDER");

        } catch (Exception e) {
            log.error("MOCK: Failed to send SMS", e);
            return SendResult.failure(e.getMessage(), "SMS_SEND_ERROR");
        }
    }
}
