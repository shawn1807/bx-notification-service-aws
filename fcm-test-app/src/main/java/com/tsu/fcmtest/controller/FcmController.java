package com.tsu.fcmtest.controller;

import com.tsu.fcmtest.dto.PushNotificationRequest;
import com.tsu.fcmtest.dto.PushNotificationResponse;
import com.tsu.fcmtest.service.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API controller for testing FCM push notifications
 */
@RestController
@RequestMapping("/api/fcm")
@RequiredArgsConstructor
@Slf4j
public class FcmController {

    private final FcmService fcmService;

    /**
     * Send a push notification
     */
    @PostMapping("/send")
    public ResponseEntity<PushNotificationResponse> sendNotification(
            @RequestBody PushNotificationRequest request
    ) {
        log.info("Received push notification request: title={}", request.getTitle());
        PushNotificationResponse response = fcmService.sendNotification(request);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Send a simple notification (for quick testing)
     */
    @PostMapping("/send-simple")
    public ResponseEntity<PushNotificationResponse> sendSimpleNotification(
            @RequestParam String token,
            @RequestParam String title,
            @RequestParam String body
    ) {
        log.info("Sending simple notification: title={}", title);
        PushNotificationResponse response = fcmService.sendSimpleNotification(token, title, body);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Test endpoint to verify service is running
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> test() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "ok");
        response.put("message", "FCM Test Service is running");
        response.put("version", "1.0.0");
        return ResponseEntity.ok(response);
    }

    /**
     * Send test notification with sample data
     */
    @PostMapping("/send-test")
    public ResponseEntity<PushNotificationResponse> sendTestNotification(
            @RequestParam String token
    ) {
        log.info("Sending test notification");

        Map<String, String> data = new HashMap<>();
        data.put("type", "test");
        data.put("timestamp", String.valueOf(System.currentTimeMillis()));

        PushNotificationResponse response = fcmService.sendNotificationWithData(
                token,
                "Test Notification",
                "This is a test notification from FCM Test App",
                data
        );

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
}
