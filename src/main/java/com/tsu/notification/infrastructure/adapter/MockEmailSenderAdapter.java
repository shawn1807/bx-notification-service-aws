package com.tsu.notification.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Mock implementation for email sending (for testing/development)
 */
@Component
@ConditionalOnProperty(name = "notification.channels.email.provider", havingValue = "MOCK", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class MockEmailSenderAdapter implements EmailSenderAdapter {

    @Override
    public SendResult sendEmail(String recipient, String subject, String body, Map<String, Object> metadata) {
        log.info("MOCK: Sending email to: {}, subject: {}", recipient, subject);

        try {
            // Simulate successful send
            String messageId = "mock-email-" + UUID.randomUUID();
            log.info("MOCK: Email sent successfully: messageId={}", messageId);

            return SendResult.success(messageId, "MOCK_EMAIL_PROVIDER");

        } catch (Exception e) {
            log.error("MOCK: Failed to send email", e);
            return SendResult.failure(e.getMessage(), "EMAIL_SEND_ERROR");
        }
    }

    @Override
    public SendResult sendTemplatedEmail(
        String recipient,
        String templateId,
        Map<String, Object> templateData
    ) {
        log.info("MOCK: Sending templated email: recipient={}, templateId={}", recipient, templateId);

        try {
            String messageId = "mock-template-email-" + UUID.randomUUID();
            return SendResult.success(messageId, "MOCK_TEMPLATE_EMAIL_PROVIDER");

        } catch (Exception e) {
            log.error("MOCK: Failed to send templated email", e);
            return SendResult.failure(e.getMessage(), "TEMPLATE_EMAIL_ERROR");
        }
    }
}
