package com.tsu.notification.infrastructure.dispatcher;

import com.tsu.notification.enums.DeliveryStatus;
import com.tsu.notification.entities.DevicePushTokenTb;
import com.tsu.notification.entities.NotificationRecipientTb;
import com.tsu.notification.entities.NotificationTb;
import com.tsu.notification.entities.OutboxMessageTb;
import com.tsu.notification.enums.OutboxStatus;
import com.tsu.notification.infrastructure.adapter.PushSenderAdapter;
import com.tsu.notification.infrastructure.adapter.SendResult;
import com.tsu.notification.infrastructure.queue.OutboxEventMessage;
import com.tsu.notification.repo.DevicePushTokenRepository;
import com.tsu.notification.repo.NotificationRecipientRepository;
import com.tsu.notification.repo.NotificationRepository;
import com.tsu.notification.repo.OutboxMessageRepository;
import com.tsu.util.BackoffUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispatcher for push notifications (FCM & APNs)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PushChannelDispatcher implements ChannelDispatcher {

    private static final int INITIAL_DELAY = 1;
    private static final int MAX_DELAY = 60;
    private final PushSenderAdapter pushSenderAdapter;
    private final NotificationRepository notificationRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final DevicePushTokenRepository deviceRepository;
    private final OutboxMessageRepository outboxMessageRepository;

    @Override
    @Transactional
    public void dispatch(OutboxEventMessage message) {
        outboxMessageRepository.findById(message.getEventId())
                .ifPresent(outbox -> {
                    notificationRepository.findById(message.getMessageId())
                            .ifPresentOrElse(tb -> pushNotifications(outbox, tb),
                                    () -> {
                                        outbox.setStatus(OutboxStatus.INVALID);
                                        outbox.setLastError("message not found");
                                        log.warn("Delivery not supported by EmailChannelDispatcher: {} ({})", message.getMessageType(), message.getMessageId());
                                    });
                });
    }

    private boolean enableNotification(UUID userId) {
        //todo
        return true;
    }

    private void pushNotifications(OutboxMessageTb outbox, NotificationTb notification) {
        recipientRepository.findByNotificationIdAndStatusList(notification.getId(), List.of(DeliveryStatus.queued, DeliveryStatus.failed))
                .forEach(recipient -> {
                    if (recipient.getStatus() == DeliveryStatus.delivered) {
                        log.info("notification already delivered, skipping: message id={}", notification.getId());
                        outbox.setStatus(OutboxStatus.PROCESSED);
                        outboxMessageRepository.save(outbox);
                        return;
                    }
                    Instant now = Instant.now();
                    recipient.setLastAttemptDate(now);
                    if (!enableNotification(recipient.getUserId())) {
                        recipient.setStatus(DeliveryStatus.skipped);
                        recipientRepository.save(recipient);
                        return;
                    }
                    AtomicBoolean anySuccess = new AtomicBoolean(false);
                    StringBuilder lastError = new StringBuilder();
                    List<DevicePushTokenTb> tokens = deviceRepository.findByUserIdAndActiveAndRevokedDateIsNull(recipient.getUserId(), true)
                            .toList();
                    try {
                        // Mark as processing
                        recipient.setStatus(DeliveryStatus.sending);
                        recipientRepository.save(recipient);
                        // Send email
                        Map<String, Object> metadata = buildMetadata(notification);
                        tokens.forEach(token -> {
                            SendResult result = pushSenderAdapter.sendPush(
                                    token,
                                    notification.getTitle(),
                                    notification.getBody(),
                                    metadata
                            );
                            if (result.isSuccess()) {
                                anySuccess.set(true);
                                token.setLastUsedDate(now);
                                deviceRepository.save(token);
                                log.info("Push sent to device: tokenId={}, providerId={}",
                                        token.getId(), result.getProviderId());
                            } else {
                                lastError.append(result.getErrorMessage());
                                log.warn("Failed to send push to device: tokenId={}, error={}",
                                        token.getId(), result.getErrorMessage());
                                // Deactivate token if it's invalid
                                if ("INVALID_TOKEN".equals(result.getErrorCode())) {
                                    token.setRevokedDate(now);
                                    token.setActive(false);
                                    deviceRepository.save(token);
                                }
                            }
                        });
                        if (anySuccess.get()) {
                            notification.setBroadcast(true);
                            notificationRepository.save(notification);
                            recipient.setStatus(DeliveryStatus.delivered);
                            recipient.setDeliveredDate(now);
                            recipientRepository.save(recipient);
                            log.info("Push sent successfully to at least one device: recipientId={}", recipient.getId());
                            outbox.setStatus(OutboxStatus.PROCESSED);
                            outbox.setProcessedDate(Instant.now());
                            outboxMessageRepository.save(outbox);
                        } else {
                            handleFailure(outbox, recipient, !lastError.isEmpty() ? lastError.toString() : "Failed to send to all devices", "PUSH_FAILED");
                        }
                    } catch (Exception e) {
                        log.error("Error sending push token: id={}", recipient.getId(), e);
                        handleFailure(outbox, recipient, e.getMessage(), "EXCEPTION");
                    }
                });

    }

    private Map<String, Object> buildMetadata(NotificationTb notification) {
        return new HashMap<>();
    }

    private void handleFailure(OutboxMessageTb outbox, NotificationRecipientTb recipient, String error, String errorCode) {
        String fullError = errorCode + ": " + error;
        outbox.setLastError(fullError);
        outbox.setStatus(OutboxStatus.FAILED);
        outbox.setAttemptCount(outbox.getAttemptCount() + 1);
        outbox.setNextAttemptDate(BackoffUtils.exponential(outbox.getAttemptCount(), INITIAL_DELAY, MAX_DELAY));
        outboxMessageRepository.save(outbox);

        recipient.setLastError(fullError);
        recipient.setAttempts(recipient.getAttempts() + 1);
        recipient.setStatus(DeliveryStatus.failed);
        recipientRepository.save(recipient);
        log.error("notification failed after {} attempts: deliveryId={}",
                recipient.getAttempts(), recipient.getId());
    }

}
