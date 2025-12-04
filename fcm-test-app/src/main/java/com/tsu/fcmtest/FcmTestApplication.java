package com.tsu.fcmtest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FCM Test Application
 *
 * A simple Spring Boot application for testing FCM push notification delivery.
 */
@SpringBootApplication
public class FcmTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(FcmTestApplication.class, args);
    }
}
