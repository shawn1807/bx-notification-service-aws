package com.tsu.notification.enums;

/**
 * Delivery status lifecycle
 */
public enum DeliveryStatus {
    PENDING,            // Not yet attempted
    PROCESSING,         // Currently being sent
    SENT,               // Successfully sent to provider
    DELIVERED,          // Confirmed delivery (if provider supports it)
    FAILED,             // Temporary failure, will retry
    PERMANENT_FAILURE,  // Max retries exhausted or unrecoverable error
    SKIPPED             // User opt-out or invalid recipient
}
