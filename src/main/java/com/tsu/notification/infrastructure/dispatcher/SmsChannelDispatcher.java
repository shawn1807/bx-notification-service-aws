package com.tsu.notification.infrastructure.dispatcher;

import com.tsu.notification.enums.DeliveryStatus;
import com.tsu.notification.infrastructure.adapter.SendResult;
import com.tsu.notification.infrastructure.adapter.SmsSenderAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dispatcher for SMS notifications
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmsChannelDispatcher implements ChannelDispatcher {

    private final SmsSenderAdapter smsSenderAdapter;

    @Override
    @Transactional
    public void dispatch(NotificationChannelDelivery delivery) {
        if (!supports(delivery)) {
            log.warn("Delivery not supported by SmsChannelDispatcher: {}", delivery.getChannel());
            return;
        }

        // Idempotency check
        if (delivery.getProviderId() != null && delivery.getStatus() == DeliveryStatus.SENT) {
            log.info("SMS already sent, skipping: deliveryId={}, providerId={}",
                delivery.getId(), delivery.getProviderId());
            return;
        }

        try {
            delivery.setStatus(DeliveryStatus.PROCESSING);
            deliveryRepository.save(delivery);

            Notification notification = notificationRepository.findById(delivery.getNotificationId())
                .orElseThrow(() -> new IllegalStateException("Notification not found: " + delivery.getNotificationId()));

            log.info("Sending SMS: deliveryId={}, recipient={}", delivery.getId(), delivery.getRecipient());
            SendResult result = smsSenderAdapter.sendSms(
                delivery.getRecipient(),
                notification.getBody(),
                notification.getMetadata()
            );

            if (result.isSuccess()) {
                delivery.markAsSent(result.getProviderId(), result.getProviderName());
                deliveryRepository.save(delivery);
                auditService.logDeliverySent(delivery, "SYSTEM");

                log.info("SMS sent successfully: deliveryId={}, providerId={}",
                    delivery.getId(), result.getProviderId());
            } else {
                handleFailure(delivery, result.getErrorMessage(), result.getErrorCode());
            }

        } catch (Exception e) {
            log.error("Error sending SMS: deliveryId={}", delivery.getId(), e);
            handleFailure(delivery, e.getMessage(), "EXCEPTION");
        }
    }

    private void handleFailure(NotificationChannelDelivery delivery, String error, String errorCode) {
        delivery.markAsFailed(error, errorCode);
        deliveryRepository.save(delivery);
        auditService.logDeliveryFailed(delivery, error);

        if (delivery.getStatus() == DeliveryStatus.PERMANENT_FAILURE) {
            log.error("SMS permanently failed after {} attempts: deliveryId={}",
                delivery.getAttemptCount(), delivery.getId());
        } else {
            log.warn("SMS failed, will retry at {}: deliveryId={}, attempt={}/{}",
                delivery.getNextAttemptAt(), delivery.getId(),
                delivery.getAttemptCount(), delivery.getMaxAttempts());
        }
    }

    @Override
    public boolean supports(NotificationChannelDelivery delivery) {
        return delivery.getChannel() == DeliveryChannel.SMS;
    }
}
