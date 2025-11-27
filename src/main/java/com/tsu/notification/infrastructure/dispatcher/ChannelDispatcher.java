package com.tsu.notification.infrastructure.dispatcher;

/**
 * Interface for channel-specific dispatchers
 */
public interface ChannelDispatcher {

    /**
     * Dispatch a delivery to the appropriate provider
     */
    void dispatch(NotificationChannelDelivery delivery);

}
