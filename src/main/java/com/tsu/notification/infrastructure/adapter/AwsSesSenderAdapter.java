package com.tsu.notification.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.util.Map;

/**
 * AWS SES implementation for sending emails
 * <p>
 * Features:
 * - Send simple emails (HTML/Plain text)
 * - Send templated emails
 * - Configuration sets for tracking
 * - Reply-to addresses
 */
@Component
@ConditionalOnProperty(name = "notification.channels.email.provider", havingValue = "AWS_SES")
@RequiredArgsConstructor
@Slf4j
public class AwsSesSenderAdapter implements EmailSenderAdapter {

    private final SesClient sesClient;

    @Value("${notification.channels.email.from-address:noreply@example.com}")
    private String fromAddress;

    @Value("${notification.channels.email.from-name:Notification Service}")
    private String fromName;

    @Value("${notification.channels.email.configuration-set:#{null}}")
    private String configurationSet;

    @Override
    public SendResult sendEmail(String to, String cc, String subject, String body, Map<String, Object> metadata) {
        log.info("Sending email via AWS SES to: {} cc: {}, subject: {}", to, cc, subject);

        try {
            // Build message
            Message message = Message.builder()
                    .subject(Content.builder()
                            .data(subject)
                            .charset("UTF-8")
                            .build())
                    .body(Body.builder()
                            .html(Content.builder()
                                    .data(body)
                                    .charset("UTF-8")
                                    .build())
                            .build())
                    .build();

            // Build destination
            Destination destination = Destination.builder()
                    .toAddresses(to)
                    .ccAddresses(cc)
                    .build();

            // Build request
            var requestBuilder = SendEmailRequest.builder()
                    .source(formatFromAddress())
                    .destination(destination)
                    .message(message);

            // Add configuration set if configured (for tracking opens/clicks)
            if (configurationSet != null && !configurationSet.isBlank()) {
                requestBuilder.configurationSetName(configurationSet);
            }

            // Add reply-to if specified in metadata
            if (metadata != null && metadata.containsKey("replyTo")) {
                requestBuilder.replyToAddresses(metadata.get("replyTo").toString());
            }

            // Send email
            SendEmailResponse response = sesClient.sendEmail(requestBuilder.build());
            String messageId = response.messageId();

            log.info("Email sent successfully via AWS SES: messageId={}, to={}, cc={}",
                    messageId, to, cc);

            return SendResult.success(messageId, "AWS_SES");

        } catch (MessageRejectedException e) {
            log.error("AWS SES rejected email: to={}, cc={}, reason={}",
                    to, cc, e.awsErrorDetails().errorMessage());
            return SendResult.failure(
                    "Email rejected: " + e.awsErrorDetails().errorMessage(),
                    "SES_REJECTED"
            );

        } catch (MailFromDomainNotVerifiedException e) {
            log.error("AWS SES domain not verified: {}", fromAddress);
            return SendResult.failure(
                    "Domain not verified in SES",
                    "SES_DOMAIN_NOT_VERIFIED"
            );

        } catch (AccountSendingPausedException e) {
            log.error("AWS SES account sending paused");
            return SendResult.failure(
                    "SES account sending is paused",
                    "SES_ACCOUNT_PAUSED"
            );

        } catch (SesException e) {
            log.error("AWS SES error: {}", e.awsErrorDetails().errorMessage(), e);
            return SendResult.failure(
                    "SES error: " + e.awsErrorDetails().errorMessage(),
                    "SES_ERROR"
            );

        } catch (Exception e) {
            log.error("Unexpected error sending email via SES", e);
            return SendResult.failure(e.getMessage(), "EMAIL_SEND_ERROR");
        }
    }

    @Override
    public SendResult sendTemplatedEmail(
            String recipient,
            String templateId,
            Map<String, Object> templateData
    ) {
        log.info("Sending templated email via AWS SES: recipient={}, template={}",
                recipient, templateId);

        try {
            // Build template data JSON
            String templateDataJson = buildTemplateDataJson(templateData);

            // Build request
            var requestBuilder = SendTemplatedEmailRequest.builder()
                    .source(formatFromAddress())
                    .destination(Destination.builder()
                            .toAddresses(recipient)
                            .build())
                    .template(templateId)
                    .templateData(templateDataJson);

            // Add configuration set if configured
            if (configurationSet != null && !configurationSet.isBlank()) {
                requestBuilder.configurationSetName(configurationSet);
            }

            // Send templated email
            SendTemplatedEmailResponse response = sesClient.sendTemplatedEmail(
                    requestBuilder.build()
            );
            String messageId = response.messageId();

            log.info("Templated email sent successfully via AWS SES: messageId={}, template={}",
                    messageId, templateId);

            return SendResult.success(messageId, "AWS_SES_TEMPLATE");

        } catch (TemplateDoesNotExistException e) {
            log.error("AWS SES template does not exist: {}", templateId);
            return SendResult.failure(
                    "Template not found: " + templateId,
                    "SES_TEMPLATE_NOT_FOUND"
            );

        } catch (SesException e) {
            log.error("AWS SES error: {}", e.awsErrorDetails().errorMessage(), e);
            return SendResult.failure(
                    "SES error: " + e.awsErrorDetails().errorMessage(),
                    "SES_ERROR"
            );

        } catch (Exception e) {
            log.error("Unexpected error sending templated email via SES", e);
            return SendResult.failure(e.getMessage(), "TEMPLATE_EMAIL_ERROR");
        }
    }

    /**
     * Format from address with name
     */
    private String formatFromAddress() {
        if (fromName != null && !fromName.isBlank()) {
            return String.format("%s <%s>", fromName, fromAddress);
        }
        return fromAddress;
    }

    /**
     * Build template data as JSON string
     * AWS SES expects template data in JSON format
     */
    private String buildTemplateDataJson(Map<String, Object> templateData) {
        if (templateData == null || templateData.isEmpty()) {
            return "{}";
        }

        StringBuilder json = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<String, Object> entry : templateData.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":");

            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append("\"").append(escapeJson(value.toString())).append("\"");
            }

            first = false;
        }
        json.append("}");

        return json.toString();
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Check if SES is healthy by getting send quota
     */
    public boolean isHealthy() {
        try {
            GetSendQuotaResponse response = sesClient.getSendQuota();
            log.debug("SES send quota: max24HourSend={}, sentLast24Hours={}",
                    response.max24HourSend(), response.sentLast24Hours());
            return true;
        } catch (Exception e) {
            log.error("SES health check failed", e);
            return false;
        }
    }
}
