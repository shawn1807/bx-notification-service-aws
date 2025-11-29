package com.tsu.notification.infrastructure.dispatcher;

import com.tsu.notification.entities.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handler for notification-related outbox events
 * Routes to specific channel dispatchers
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventHandler {

    private final ChannelDispatcherFactory channelDispatcherFactory;

    public void handle(OutboxEvent event) {
        String eventType = event.getEventType();
        Map<String, Object> payload = event.getPayload();

        log.debug("Handling notification event: type={}, payload={}", eventType, payload);

        switch (eventType) {
            case "NOTIFICATION_CREATED" -> handleNotificationCreated(event);
            case "DELIVERY_REQUESTED" -> handleDeliveryRequested(event);
            default -> log.warn("Unknown event type: {}", eventType);
        }
    }

    /**
     * Handle notification created event - trigger all pending deliveries
     */
    private void handleNotificationCreated(OutboxEvent event) {
        UUID notificationId = event.getAggregateId();
        log.info("Handling notification created: notificationId={}", notificationId);

        // Find all pending deliveries for this notification
        List<NotificationChannelDelivery> deliveries = deliveryRepository.findByNotificationId(notificationId);

        for (NotificationChannelDelivery delivery : deliveries) {
            if (delivery.isReadyForProcessing()) {
                dispatchDelivery(delivery);
            }
        }
    }

    /**
     * Handle delivery requested event - trigger specific delivery
     */
    private void handleDeliveryRequested(OutboxEvent event) {
        Map<String, Object> payload = event.getPayload();
        UUID deliveryId = UUID.fromString((String) payload.get("deliveryId"));

        deliveryRepository.findById(deliveryId).ifPresent(delivery -> {
            if (delivery.isReadyForProcessing()) {
                dispatchDelivery(delivery);
            }
        });
    }

    /**
     * Dispatch delivery to appropriate channel handler
     */
    private void dispatchDelivery(NotificationChannelDelivery delivery) {
        try {
            log.info("Dispatching delivery: id={}, channel={}, notificationId={}",
                delivery.getId(), delivery.getChannel(), delivery.getNotificationId());

            ChannelDispatcher dispatcher = channelDispatcherFactory.getDispatcher(delivery.getChannel());
            dispatcher.dispatch(delivery);

        } catch (Exception e) {
            log.error("Failed to dispatch delivery: id={}", delivery.getId(), e);
            delivery.markAsFailed(e.getMessage(), "DISPATCH_ERROR");
            deliveryRepository.save(delivery);
        }
    }
}
