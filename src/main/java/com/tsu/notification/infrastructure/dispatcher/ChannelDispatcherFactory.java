package com.tsu.notification.infrastructure.dispatcher;

import com.tsu.common.enums.MessageChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory to get the appropriate channel dispatcher
 */
@Component
@RequiredArgsConstructor
public class ChannelDispatcherFactory {

    private final List<ChannelDispatcher> dispatchers;
    private Map<MessageChannel, ChannelDispatcher> dispatcherMap;

    public ChannelDispatcher getDispatcher(MessageChannel channel) {
        if (dispatcherMap == null) {
            initializeDispatcherMap();
        }

        ChannelDispatcher dispatcher = dispatcherMap.get(channel);
        if (dispatcher == null) {
            throw new IllegalArgumentException("No dispatcher found for channel: " + channel);
        }

        return dispatcher;
    }

    private void initializeDispatcherMap() {
        dispatcherMap = dispatchers.stream()
            .collect(Collectors.toMap(
                this::extractChannel,
                Function.identity()
            ));
    }

    private MessageChannel extractChannel(ChannelDispatcher dispatcher) {
        // Map dispatcher types to channels
        if (dispatcher instanceof EmailChannelDispatcher) {
            return MessageChannel.email;
        } else if (dispatcher instanceof SmsChannelDispatcher) {
            return MessageChannel.sms;
        } else if (dispatcher instanceof PushChannelDispatcher) {
            return MessageChannel.notification;
        } 
        throw new IllegalArgumentException("Unknown dispatcher type: " + dispatcher.getClass());
    }
}
