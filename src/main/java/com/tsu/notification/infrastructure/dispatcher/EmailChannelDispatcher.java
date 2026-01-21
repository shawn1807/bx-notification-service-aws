package com.tsu.notification.infrastructure.dispatcher;

import com.tsu.notification.enums.MessageStatus;
import com.tsu.notification.entities.EmailMessageTb;
import com.tsu.notification.entities.OutboxMessageTb;
import com.tsu.notification.enums.OutboxStatus;
import com.tsu.notification.infrastructure.adapter.EmailSenderAdapter;
import com.tsu.notification.infrastructure.adapter.SendResult;
import com.tsu.notification.infrastructure.queue.OutboxEventMessage;
import com.tsu.notification.repo.EmailMessageRepository;
import com.tsu.notification.repo.OutboxMessageRepository;
import com.tsu.common.util.BackoffUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Dispatcher for email notifications
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailChannelDispatcher implements ChannelDispatcher {

    private static final int INITIAL_DELAY = 60;
    private static final int MAX_DELAY = 600;
    private final EmailSenderAdapter emailSenderAdapter;
    private final EmailMessageRepository emailMessageRepository;
    private final OutboxMessageRepository outboxMessageRepository;

    @Override
    @Transactional
    public void dispatch(OutboxEventMessage message) {
        outboxMessageRepository.findById(message.getEventId())
                .ifPresent(outbox -> emailMessageRepository.findById(message.getMessageId())
                        .ifPresentOrElse(tb -> sendEmail(outbox, tb),
                                () -> {
                                    outbox.setStatus(OutboxStatus.INVALID);
                                    outbox.setLastError("message not found");
                                    log.warn("Delivery not supported by EmailChannelDispatcher: {} ({})", message.getMessageType(), message.getMessageId());
                                }));
    }

    private void sendEmail(OutboxMessageTb outbox, EmailMessageTb email) {
        if (email.getStatus() == MessageStatus.sent) {
            log.info("Email already sent, skipping: message id={}", email.getId());
            outbox.setStatus(OutboxStatus.PROCESSED);
            outboxMessageRepository.save(outbox);
            return;
        }
        Instant now = Instant.now();
        try {
            // Mark as processing
            email.setLastAttemptDate(now);
            email.setStatus(MessageStatus.sending);
            emailMessageRepository.save(email);
            // Send email
            log.info("Sending email: {}, to={}", email.getId(), email.getToEmail());
            SendResult result = emailSenderAdapter.sendEmail(
                    email.getToEmail(),
                    email.getSubject(),
                    email.getBody(),
                    buildMetadata(email)
            );
            if (result.isSuccess()) {
                // Mark as sent
                email.setSentDate(now);
                email.setStatus(MessageStatus.sent);
                emailMessageRepository.save(email);

                outbox.setStatus(OutboxStatus.PROCESSED);
                outbox.setProcessedDate(Instant.now());
                outboxMessageRepository.save(outbox);
                log.info("Email sent successfully: id={}, providerId={}",
                        email.getId(), result.getProviderId());
            } else {
                // Handle failure with retry
                handleFailure(outbox, email, result.getErrorMessage(), result.getErrorCode());
            }

        } catch (Exception e) {
            log.error("Error sending email: id={}", email.getId(), e);
            handleFailure(outbox, email, e.getMessage(), "EXCEPTION");
        }
    }

    private Map<String, Object> buildMetadata(EmailMessageTb email) {
        return new HashMap<>();
    }

    private void handleFailure(OutboxMessageTb outbox, EmailMessageTb email, String error, String errorCode) {
        String fullError = errorCode + ": " + error;
        outbox.setLastError(fullError);
        outbox.setStatus(OutboxStatus.FAILED);
        outbox.setAttemptCount(outbox.getAttemptCount() + 1);
        outbox.setNextAttemptDate(BackoffUtils.exponential(outbox.getAttemptCount(), INITIAL_DELAY, MAX_DELAY));
        outboxMessageRepository.save(outbox);

        email.setLastError(fullError);
        email.setAttempts(email.getAttempts() + 1);
        email.setStatus(MessageStatus.failed);
        emailMessageRepository.save(email);
        log.error("Email permanently failed after {} attempts: deliveryId={}",
                email.getAttempts(), email.getId());
    }


}
