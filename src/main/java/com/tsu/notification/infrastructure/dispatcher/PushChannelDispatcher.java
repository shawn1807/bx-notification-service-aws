package com.tsu.notification.infrastructure.dispatcher;

import com.tsu.notification.entities.DevicePushToken;
import com.tsu.notification.enums.DeliveryStatus;
import com.tsu.notification.infrastructure.queue.OutboxEventMessage;
import com.tsu.notification.repo.DevicePushTokenRepository;
import com.tsu.notification.repo.NotificationRepository;
import com.tsu.notification.infrastructure.adapter.PushSenderAdapter;
import com.tsu.notification.infrastructure.adapter.SendResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Dispatcher for push notifications (FCM & APNs)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PushChannelDispatcher implements ChannelDispatcher {

    private final PushSenderAdapter pushSenderAdapter;
    private final NotificationRepository notificationRepository;
    private final NotificationChannelDeliveryRepository deliveryRepository;
    private final DevicePushTokenRepository deviceRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public void dispatch(OutboxEventMessage delivery) {
        if (!supports(delivery)) {
            log.warn("Delivery not supported by PushChannelDispatcher: {}", delivery.getChannel());
            return;
        }

        // Idempotency check
        if (delivery.getProviderId() != null && delivery.getStatus() == DeliveryStatus.SENT) {
            log.info("Push already sent, skipping: deliveryId={}, providerId={}",
                delivery.getId(), delivery.getProviderId());
            return;
        }

        try {
            delivery.setStatus(DeliveryStatus.PROCESSING);
            deliveryRepository.save(delivery);

            Notification notification = notificationRepository.findById(delivery.getNotificationId())
                .orElseThrow(() -> new IllegalStateException("Notification not found: " + delivery.getNotificationId()));

            // Get user's active device tokens
            String userId = delivery.getRecipient();
            List<DevicePushToken> tokens = deviceRepository.findByUserIdAndActiveAndRevokedDateIsNull(userId, true);

            if (tokens.isEmpty()) {
                log.warn("No active push tokens found for user: userId={}", userId);
                delivery.markAsSkipped("No active push tokens");
                deliveryRepository.save(delivery);
                return;
            }

            log.info("Sending push notification: deliveryId={}, recipient={}, tokenCount={}",
                delivery.getId(), userId, tokens.size());

            // Send to all user devices
            boolean anySuccess = false;
            String lastError = null;

            for (DevicePushToken token : tokens) {
                try {
                    SendResult result = pushSenderAdapter.sendPush(
                        token,
                        notification.getSubject(),
                        notification.getBody(),
                        notification.getMetadata()
                    );

                    if (result.isSuccess()) {
                        anySuccess = true;
                        token.markAsUsed();
                        deviceRepository.save(token);
                        log.info("Push sent to device: tokenId={}, providerId={}",
                            token.getId(), result.getProviderId());
                    } else {
                        lastError = result.getErrorMessage();
                        log.warn("Failed to send push to device: tokenId={}, error={}",
                            token.getId(), result.getErrorMessage());

                        // Deactivate token if it's invalid
                        if ("INVALID_TOKEN".equals(result.getErrorCode())) {
                            token.deactivate();
                            deviceRepository.save(token);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error sending push to device: tokenId={}", token.getId(), e);
                    lastError = e.getMessage();
                }
            }

            if (anySuccess) {
                delivery.markAsSent("MULTI_DEVICE", "PUSH_PROVIDER");
                deliveryRepository.save(delivery);
                auditService.logDeliverySent(delivery, "SYSTEM");
                log.info("Push sent successfully to at least one device: deliveryId={}", delivery.getId());
            } else {
                handleFailure(delivery, lastError != null ? lastError : "Failed to send to all devices", "PUSH_FAILED");
            }

        } catch (Exception e) {
            log.error("Error sending push notification: deliveryId={}", delivery.getId(), e);
            handleFailure(delivery, e.getMessage(), "EXCEPTION");
        }
    }

    private void handleFailure(NotificationChannelDelivery delivery, String error, String errorCode) {
        delivery.markAsFailed(error, errorCode);
        deliveryRepository.save(delivery);
        auditService.logDeliveryFailed(delivery, error);

        if (delivery.getStatus() == DeliveryStatus.PERMANENT_FAILURE) {
            log.error("Push permanently failed after {} attempts: deliveryId={}",
                delivery.getAttemptCount(), delivery.getId());
        } else {
            log.warn("Push failed, will retry at {}: deliveryId={}, attempt={}/{}",
                delivery.getNextAttemptAt(), delivery.getId(),
                delivery.getAttemptCount(), delivery.getMaxAttempts());
        }
    }

    @Override
    public boolean supports(NotificationChannelDelivery delivery) {
        return delivery.getChannel() == DeliveryChannel.PUSH;
    }
}
