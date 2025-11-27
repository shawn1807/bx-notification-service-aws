package com.tsu.notification.infrastructure.dispatcher;

import com.tsu.notification.enums.DeliveryStatus;
import com.tsu.notification.repo.NotificationRepository;
import com.tsu.notification.infrastructure.adapter.EmailSenderAdapter;
import com.tsu.notification.infrastructure.adapter.SendResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dispatcher for email notifications
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailChannelDispatcher implements ChannelDispatcher {

    private final EmailSenderAdapter emailSenderAdapter;
    private final NotificationRepository notificationRepository;
    private final NotificationChannelDeliveryRepository deliveryRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public void dispatch(NotificationChannelDelivery delivery) {
        if (!supports(delivery)) {
            log.warn("Delivery not supported by EmailChannelDispatcher: {}", delivery.getChannel());
            return;
        }

        // Idempotency check
        if (delivery.getProviderId() != null && delivery.getStatus() == DeliveryStatus.SENT) {
            log.info("Email already sent, skipping: deliveryId={}, providerId={}",
                delivery.getId(), delivery.getProviderId());
            return;
        }

        try {
            // Mark as processing
            delivery.setStatus(DeliveryStatus.PROCESSING);
            deliveryRepository.save(delivery);

            // Fetch notification details
            Notification notification = notificationRepository.findById(delivery.getNotificationId())
                .orElseThrow(() -> new IllegalStateException("Notification not found: " + delivery.getNotificationId()));

            // Send email
            log.info("Sending email: deliveryId={}, recipient={}", delivery.getId(), delivery.getRecipient());
            SendResult result = emailSenderAdapter.sendEmail(
                delivery.getRecipient(),
                notification.getSubject(),
                notification.getBody(),
                notification.getMetadata()
            );

            if (result.isSuccess()) {
                // Mark as sent
                delivery.markAsSent(result.getProviderId(), result.getProviderName());
                deliveryRepository.save(delivery);

                log.info("Email sent successfully: deliveryId={}, providerId={}",
                    delivery.getId(), result.getProviderId());
            } else {
                // Handle failure with retry
                handleFailure(delivery, result.getErrorMessage(), result.getErrorCode());
            }

        } catch (Exception e) {
            log.error("Error sending email: deliveryId={}", delivery.getId(), e);
            handleFailure(delivery, e.getMessage(), "EXCEPTION");
        }
    }

    private void handleFailure(NotificationChannelDelivery delivery, String error, String errorCode) {
        delivery.markAsFailed(error, errorCode);
        deliveryRepository.save(delivery);
        auditService.logDeliveryFailed(delivery, error);

        if (delivery.getStatus() == DeliveryStatus.PERMANENT_FAILURE) {
            log.error("Email permanently failed after {} attempts: deliveryId={}",
                delivery.getAttemptCount(), delivery.getId());
        } else {
            log.warn("Email failed, will retry at {}: deliveryId={}, attempt={}/{}",
                delivery.getNextAttemptAt(), delivery.getId(),
                delivery.getAttemptCount(), delivery.getMaxAttempts());
        }
    }

    @Override
    public boolean supports(NotificationChannelDelivery delivery) {
        return delivery.getChannel() == DeliveryChannel.EMAIL;
    }
}
