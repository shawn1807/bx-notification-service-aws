package com.tsu.notification.infrastructure.adapter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a send operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendResult {

    private boolean success;
    private String providerId;
    private String providerName;
    private boolean permanent;
    private String errorMessage;
    private String errorCode;

    public static SendResult success(String providerId, String providerName) {
        return SendResult.builder()
            .success(true)
            .providerId(providerId)
            .providerName(providerName)
            .build();
    }

    public static SendResult failure(String errorMessage, String errorCode) {
        return SendResult.builder()
            .success(false)
            .errorMessage(errorMessage)
            .errorCode(errorCode)
            .build();
    }
    public static SendResult failure(String errorMessage, String errorCode,boolean isPermanent) {
        return SendResult.builder()
                .success(false)
                .permanent(isPermanent)
                .errorMessage(errorMessage)
                .errorCode(errorCode)
                .build();
    }
}
