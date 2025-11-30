package com.tsu.notification.infrastructure.dispatcher;

import com.tsu.notification.infrastructure.queue.OutboxEventMessage;

/**
 * Interface for channel-specific dispatchers
 */
public interface ChannelDispatcher {


    void dispatch(OutboxEventMessage delivery);


}
