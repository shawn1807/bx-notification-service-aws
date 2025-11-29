package com.tsu.notification.infrastructure.dispatcher;

import com.tsu.notification.enums.DeliveryStatus;
import com.tsu.notification.infrastructure.queue.OutboxEventMessage;
import com.tsu.notification.repo.NotificationRepository;
import com.tsu.notification.infrastructure.adapter.InAppSenderAdapter;
import com.tsu.notification.infrastructure.adapter.SendResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dispatcher for in-app notifications (WebSocket/SSE)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InAppChannelDispatcher implements ChannelDispatcher {

    private final InAppSenderAdapter inAppSenderAdapter;
    private final NotificationRepository notificationRepository;
    private final NotificationChannelDeliveryRepository deliveryRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public void dispatch(OutboxEventMessage delivery) {
        if (!supports(delivery)) {
            log.warn("Delivery not supported by InAppChannelDispatcher: {}", delivery.getChannel());
            return;
        }

        // Idempotency check
        if (delivery.getProviderId() != null && delivery.getStatus() == DeliveryStatus.SENT) {
            log.info("In-app notification already sent, skipping: deliveryId={}, providerId={}",
                delivery.getId(), delivery.getProviderId());
            return;
        }

        try {
            delivery.setStatus(DeliveryStatus.PROCESSING);
            deliveryRepository.save(delivery);

            Notification notification = notificationRepository.findById(delivery.getNotificationId())
                .orElseThrow(() -> new IllegalStateException("Notification not found: " + delivery.getNotificationId()));

            String userId = delivery.getRecipient();
            log.info("Sending in-app notification: deliveryId={}, userId={}", delivery.getId(), userId);

            SendResult result = inAppSenderAdapter.sendInApp(
                userId,
                notification.getSubject(),
                notification.getBody(),
                notification.getMetadata()
            );

            if (result.isSuccess()) {
                delivery.markAsSent(result.getProviderId(), result.getProviderName());
                deliveryRepository.save(delivery);
                auditService.logDeliverySent(delivery, "SYSTEM");

                log.info("In-app notification sent successfully: deliveryId={}, providerId={}",
                    delivery.getId(), result.getProviderId());
            } else {
                // For in-app, if user is not connected, we might just mark as sent anyway
                // since it will be available when they reconnect/refresh
                if ("USER_NOT_CONNECTED".equals(result.getErrorCode())) {
                    delivery.markAsSent(delivery.getId().toString(), "IN_APP_QUEUED");
                    deliveryRepository.save(delivery);
                    log.info("In-app notification queued for offline user: deliveryId={}", delivery.getId());
                } else {
                    handleFailure(delivery, result.getErrorMessage(), result.getErrorCode());
                }
            }

        } catch (Exception e) {
            log.error("Error sending in-app notification: deliveryId={}", delivery.getId(), e);
            handleFailure(delivery, e.getMessage(), "EXCEPTION");
        }
    }

    private void handleFailure(NotificationChannelDelivery delivery, String error, String errorCode) {
        delivery.markAsFailed(error, errorCode);
        deliveryRepository.save(delivery);
        auditService.logDeliveryFailed(delivery, error);

        if (delivery.getStatus() == DeliveryStatus.PERMANENT_FAILURE) {
            log.error("In-app notification permanently failed after {} attempts: deliveryId={}",
                delivery.getAttemptCount(), delivery.getId());
        } else {
            log.warn("In-app notification failed, will retry at {}: deliveryId={}, attempt={}/{}",
                delivery.getNextAttemptAt(), delivery.getId(),
                delivery.getAttemptCount(), delivery.getMaxAttempts());
        }
    }

    @Override
    public boolean supports(NotificationChannelDelivery delivery) {
        return delivery.getChannel() == DeliveryChannel.IN_APP;
    }
}
