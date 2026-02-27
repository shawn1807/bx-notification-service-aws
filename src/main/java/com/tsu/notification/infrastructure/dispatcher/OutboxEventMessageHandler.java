package com.tsu.notification.infrastructure.dispatcher;

import com.tsu.notification.infrastructure.queue.OutboxEventMessage;
import com.tsu.notification.repo.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handler for notification-related outbox events
 * Routes to specific channel dispatchers
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventMessageHandler {

    private final ChannelDispatcherFactory channelDispatcherFactory;
    private final OutboxMessageRepository messageRepository;

    public void handle(OutboxEventMessage event) {
        String eventType = event.getEventType();
        log.debug("Handling notification event: type={}", eventType);
        try {
            ChannelDispatcher dispatcher = channelDispatcherFactory.getDispatcher(event.getMessageType());
            dispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to dispatch OutboxEventMessage: id={}", event.getEventId(), e);

        }
    }

}
