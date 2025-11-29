package com.tsu.notification.infrastructure.dispatcher;

import com.tsu.enums.MessageStatus;
import com.tsu.notification.entities.EmailMessageTb;
import com.tsu.notification.infrastructure.adapter.EmailSenderAdapter;
import com.tsu.notification.infrastructure.adapter.SendResult;
import com.tsu.notification.infrastructure.queue.OutboxEventMessage;
import com.tsu.notification.repo.EmailMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Dispatcher for email notifications
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailChannelDispatcher implements ChannelDispatcher {

    private final EmailSenderAdapter emailSenderAdapter;
    private final EmailMessageRepository emailMessageRepository;

    @Override
    @Transactional
    public void dispatch(OutboxEventMessage message) {
        emailMessageRepository.findById(message.getMessageId())
                .ifPresentOrElse(this::sendEmail, () -> log.warn("Delivery not supported by EmailChannelDispatcher: {} ({})", message.getMessageType(), message.getMessageId()));
        // Idempotency check

    }

    private void sendEmail(EmailMessageTb email) {
        if (email.getStatus() == MessageStatus.sent) {
            log.info("Email already sent, skipping: message id={}", email.getId());
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            // Mark as processing
            email.setLastAttemptDate(now);
            email.setStatus(MessageStatus.sending);
            emailMessageRepository.save(email);

            // Send email
            log.info("Sending email: {}, to={}, cc={}", email.getId(), email.getToEmail(), email.getCcEmail());
            SendResult result = emailSenderAdapter.sendEmail(
                    email.getToEmail(),
                    email.getCcEmail(),
                    email.getSubject(),
                    email.getBody(),
                    buildMetadata(email)
            );

            if (result.isSuccess()) {
                // Mark as sent
                email.setSentDate(now);
                email.setStatus(MessageStatus.sent);
                emailMessageRepository.save(email);
                log.info("Email sent successfully: id={}, providerId={}",
                        email.getId(), result.getProviderId());
            } else {
                // Handle failure with retry
                handleFailure(email, result.getErrorMessage(), result.getErrorCode());
            }

        } catch (Exception e) {
            log.error("Error sending email: id={}", email.getId(), e);
            handleFailure(email, e.getMessage(), "EXCEPTION");
        }
    }

    private Map<String, Object> buildMetadata(EmailMessageTb email) {
        return new HashMap<>();
    }

    private void handleFailure(EmailMessageTb email, String error, String errorCode) {
        email.setLastError(error);
        email.setAttempts(email.getAttempts() + 1);
        email.setStatus(MessageStatus.failed);
        emailMessageRepository.save(email);
        log.error("Email permanently failed after {} attempts: deliveryId={}",
                email.getAttempts(), email.getId());
    }


}
