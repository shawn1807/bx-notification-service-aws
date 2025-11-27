package com.tsu.notification.infrastructure.adapter;

import java.util.Map;

/**
 * Interface for SMS sending implementations
 * Allows switching between different providers (AWS SNS, Twilio, etc.)
 */
public interface SmsSenderAdapter {

    /**
     * Send SMS via provider
     *
     * @param phoneNumber E.164 format phone number (e.g., +1234567890)
     * @param message     SMS message body (max 160 chars for single SMS)
     * @param metadata    Additional metadata
     * @return SendResult with provider ID for idempotency
     */
    SendResult sendSms(String phoneNumber, String message, Map<String, Object> metadata);

    /**
     * Validate phone number format
     *
     * @param phoneNumber Phone number to validate
     * @return true if valid E.164 format
     */
    default boolean isValidPhoneNumber(String phoneNumber) {
        // E.164 format: +[country code][subscriber number]
        return phoneNumber != null && phoneNumber.matches("^\\+[1-9]\\d{1,14}$");
    }
}
