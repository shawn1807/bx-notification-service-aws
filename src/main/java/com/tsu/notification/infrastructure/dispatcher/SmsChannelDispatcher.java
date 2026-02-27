package com.tsu.notification.infrastructure.dispatcher;

import com.tsu.notification.entities.OutboxMessageTb;
import com.tsu.notification.entities.SmsMessageTb;
import com.tsu.notification.enums.MessageStatus;
import com.tsu.notification.enums.OutboxStatus;
import com.tsu.notification.infrastructure.adapter.SendResult;
import com.tsu.notification.infrastructure.adapter.SmsSenderAdapter;
import com.tsu.notification.infrastructure.queue.OutboxEventMessage;
import com.tsu.common.util.BackoffUtils;
import com.tsu.notification.repo.OutboxMessageRepository;
import com.tsu.notification.repo.SmsMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Dispatcher for SMS notifications
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmsChannelDispatcher implements ChannelDispatcher {

    private static final int INITIAL_DELAY = 10;
    private static final int MAX_DELAY = 180;

    private final SmsSenderAdapter smsSenderAdapter;
    private final OutboxMessageRepository outboxMessageRepository;
    private final SmsMessageRepository smsMessageRepository;

    @Override
    @Transactional
    public void dispatch(OutboxEventMessage message) {
        outboxMessageRepository.findById(message.getEventId())
                .ifPresent(outbox -> smsMessageRepository.findById(message.getMessageId())
                        .ifPresentOrElse(tb -> sendSms(outbox, tb),
                                () -> {
                                    outbox.setStatus(OutboxStatus.INVALID);
                                    outbox.setLastError("message not found");
                                    log.warn("Delivery not supported by EmailChannelDispatcher: {} ({})", message.getMessageType(), message.getMessageId());
                                }));
    }

    private void sendSms(OutboxMessageTb outbox, SmsMessageTb sms) {
        if (sms.getStatus() == MessageStatus.sent) {
            log.info("Sms already sent, skipping: message id={}", sms.getId());
            outbox.setStatus(OutboxStatus.PROCESSED);
            outboxMessageRepository.save(outbox);
            return;
        }
        Instant now = Instant.now();
        try {
            // Mark as processing
            sms.setLastAttemptDate(now);
            sms.setStatus(MessageStatus.sending);
            smsMessageRepository.save(sms);
            // Send email
            log.info("Sending sms: {}, to={}", sms.getId(), sms.getPhoneNumber());
            SendResult result = smsSenderAdapter.sendSms(
                    sms.getPhoneNumber(),
                    sms.getBody(),
                    buildMetadata(sms)
            );
            if (result.isSuccess()) {
                // Mark as sent
                sms.setSentDate(now);
                sms.setStatus(MessageStatus.sent);
                smsMessageRepository.save(sms);

                outbox.setStatus(OutboxStatus.PROCESSED);
                outbox.setProcessedDate(Instant.now());
                outboxMessageRepository.save(outbox);
                log.info("Sms sent successfully: id={}, providerId={}",
                        sms.getId(), result.getProviderId());
            } else {
                // Handle failure with retry
                handleFailure(outbox, sms, result.getErrorMessage(), result.getErrorCode());
            }

        } catch (Exception e) {
            log.error("Error sending sms: id={}", sms.getId(), e);
            handleFailure(outbox, sms, e.getMessage(), "EXCEPTION");
        }
    }

    private Map<String, Object> buildMetadata(SmsMessageTb sms) {
        return new HashMap<>();
    }

    private void handleFailure(OutboxMessageTb outbox, SmsMessageTb sms, String error, String errorCode) {
        String fullError = errorCode + ": " + error;
        outbox.setLastError(fullError);
        outbox.setStatus(OutboxStatus.FAILED);
        outbox.setAttemptCount(outbox.getAttemptCount() + 1);
        outbox.setNextAttemptDate(BackoffUtils.exponential(outbox.getAttemptCount(), INITIAL_DELAY, MAX_DELAY));
        outboxMessageRepository.save(outbox);

        sms.setLastError(fullError);
        sms.setAttempts(sms.getAttempts() + 1);
        sms.setStatus(MessageStatus.failed);
        smsMessageRepository.save(sms);
        log.error("Sms permanently failed after {} attempts: deliveryId={}",
                sms.getAttempts(), sms.getId());
    }


}
