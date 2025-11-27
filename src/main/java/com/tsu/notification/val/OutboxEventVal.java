package com.tsu.notification.val;

import com.tsu.enums.MessageChannel;
import com.tsu.enums.OutboxStatus;

import java.util.UUID;

public record OutboxEventVal(MessageChannel channel, UUID messageId,String eventType, OutboxStatus status) {
}
