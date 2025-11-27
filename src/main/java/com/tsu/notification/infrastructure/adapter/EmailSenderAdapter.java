package com.tsu.notification.infrastructure.adapter;

import java.util.Map;

/**
 * Interface for email sending implementations
 * Allows switching between different providers (AWS SES, SendGrid, SMTP, etc.)
 */
public interface EmailSenderAdapter {

    /**
     * Send email via provider
     *
     * @param recipient Email address
     * @param subject   Email subject
     * @param body      Email body (HTML or plain text)
     * @param metadata  Additional metadata (template variables, reply-to, etc.)
     * @return SendResult with provider ID for idempotency
     */
    SendResult sendEmail(String recipient, String subject, String body, Map<String, Object> metadata);

    /**
     * Send templated email
     *
     * @param recipient    Email address
     * @param templateId   Template identifier
     * @param templateData Template variables
     * @return SendResult with provider ID
     */
    SendResult sendTemplatedEmail(
        String recipient,
        String templateId,
        Map<String, Object> templateData
    );
}
