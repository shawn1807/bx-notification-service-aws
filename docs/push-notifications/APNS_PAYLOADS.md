# Apple Push Notification Service (APNs) Payloads

## Overview

APNs uses HTTP/2-based protocol for sending push notifications to iOS, iPadOS, macOS, watchOS, and tvOS devices.

## Authentication

APNs supports two authentication methods:

### 1. Token-Based Authentication (Recommended)

```java
// Add dependency: com.eatthepath:pushy:0.15.2

ApnsClient apnsClient = new ApnsClientBuilder()
    .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
    .setSigningKey(ApnsSigningKey.loadFromPkcs8File(
        new File("AuthKey_KEYID.p8"),
        "TEAMID",
        "KEYID"))
    .build();
```

### 2. Certificate-Based Authentication (Legacy)

```java
ApnsClient apnsClient = new ApnsClientBuilder()
    .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
    .setClientCredentials(new File("certificate.p12"), "password")
    .build();
```

## Basic Notification Payload

### Simple Alert

```json
{
  "aps": {
    "alert": "Hello, World!",
    "sound": "default",
    "badge": 1
  }
}
```

### Structured Alert

```json
{
  "aps": {
    "alert": {
      "title": "New Order",
      "subtitle": "Order #12345",
      "body": "Your order has been confirmed"
    },
    "sound": "default",
    "badge": 5
  }
}
```

### Java Code Example

```java
import com.eatthepath.pushy.apns.*;
import com.eatthepath.pushy.apns.util.*;

public SendResult sendApnsNotification(String token, String title, String body) {
    try {
        String payload = new SimpleApnsPushNotification.Builder()
            .setToken(token)
            .setTopic("com.yourcompany.app")
            .setAlertTitle(title)
            .setAlertBody(body)
            .setSound("default")
            .setBadgeNumber(1)
            .build()
            .getPayload();

        SimpleApnsPushNotification pushNotification =
            new SimpleApnsPushNotification(token, "com.yourcompany.app", payload);

        PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>>
            sendNotificationFuture = apnsClient.sendNotification(pushNotification);

        PushNotificationResponse<SimpleApnsPushNotification> response =
            sendNotificationFuture.get();

        if (response.isAccepted()) {
            return SendResult.success(
                response.getApnsId().toString(),
                "APNS"
            );
        } else {
            return SendResult.failure(
                response.getRejectionReason(),
                "APNS_REJECTED"
            );
        }

    } catch (Exception e) {
        return SendResult.failure(e.getMessage(), "APNS_ERROR");
    }
}
```

## Custom Data Payload

```json
{
  "aps": {
    "alert": {
      "title": "Payment Received",
      "body": "You received $50"
    },
    "sound": "payment.aiff",
    "badge": 1
  },
  "transaction_id": "txn-12345",
  "amount": 50.00,
  "sender": "John Doe",
  "timestamp": "2025-01-20T10:30:00Z"
}
```

### Java Code

```java
String payload = new ApnsPayloadBuilder()
    .setAlertTitle("Payment Received")
    .setAlertBody("You received $50")
    .setSound("payment.aiff")
    .setBadgeNumber(1)
    .addCustomProperty("transaction_id", "txn-12345")
    .addCustomProperty("amount", 50.00)
    .addCustomProperty("sender", "John Doe")
    .addCustomProperty("timestamp", Instant.now().toString())
    .build();

SimpleApnsPushNotification notification = new SimpleApnsPushNotification(
    token,
    "com.yourcompany.app",
    payload
);
```

## Silent Notifications (Background Updates)

```json
{
  "aps": {
    "content-available": 1
  },
  "data": {
    "sync_type": "messages",
    "message_count": 3
  }
}
```

### Java Code

```java
String payload = new ApnsPayloadBuilder()
    .setContentAvailable(true)
    .addCustomProperty("sync_type", "messages")
    .addCustomProperty("message_count", 3)
    .build();

SimpleApnsPushNotification notification = new SimpleApnsPushNotification(
    token,
    "com.yourcompany.app",
    payload,
    null,  // expiration
    DeliveryPriority.CONSERVE_POWER  // Low priority for background
);
```

## Rich Notifications with Media

### Image Attachment

```json
{
  "aps": {
    "alert": {
      "title": "New Product",
      "body": "Check out our latest product"
    },
    "mutable-content": 1,
    "category": "PRODUCT_NOTIFICATION"
  },
  "media_url": "https://example.com/images/product.jpg",
  "media_type": "image"
}
```

Note: Rich notifications require a Notification Service Extension in your iOS app to download and display media.

## Action Buttons (Categories)

```json
{
  "aps": {
    "alert": {
      "title": "Friend Request",
      "body": "John Doe sent you a friend request"
    },
    "category": "FRIEND_REQUEST",
    "sound": "default"
  },
  "user_id": "user-12345"
}
```

The `category` must be registered in the iOS app with action buttons:

```swift
// iOS app code
let acceptAction = UNNotificationAction(
    identifier: "ACCEPT_ACTION",
    title: "Accept",
    options: [.foreground]
)

let declineAction = UNNotificationAction(
    identifier: "DECLINE_ACTION",
    title: "Decline",
    options: [.destructive]
)

let category = UNNotificationCategory(
    identifier: "FRIEND_REQUEST",
    actions: [acceptAction, declineAction],
    intentIdentifiers: []
)

UNUserNotificationCenter.current().setNotificationCategories([category])
```

## Critical Alerts

Critical alerts bypass Do Not Disturb and the mute switch (requires special entitlement):

```json
{
  "aps": {
    "alert": {
      "title": "Emergency Alert",
      "body": "Severe weather warning in your area"
    },
    "sound": {
      "critical": 1,
      "name": "emergency.aiff",
      "volume": 1.0
    },
    "interruption-level": "critical"
  }
}
```

## Notification Priority

### High Priority (Immediate Delivery)

```java
SimpleApnsPushNotification notification = new SimpleApnsPushNotification(
    token,
    "com.yourcompany.app",
    payload,
    Instant.now().plus(1, ChronoUnit.HOURS),  // Expiration
    DeliveryPriority.IMMEDIATE,
    PushType.ALERT,
    "collapse-id-123"  // Collapse ID
);
```

### Low Priority (Power Conservation)

```java
SimpleApnsPushNotification notification = new SimpleApnsPushNotification(
    token,
    "com.yourcompany.app",
    payload,
    null,
    DeliveryPriority.CONSERVE_POWER,
    PushType.BACKGROUND,
    null
);
```

## Localized Notifications

```json
{
  "aps": {
    "alert": {
      "title-loc-key": "NEW_MESSAGE_TITLE",
      "title-loc-args": ["John"],
      "loc-key": "NEW_MESSAGE_BODY",
      "loc-args": ["John", "3"]
    },
    "sound": "default"
  }
}
```

The iOS app's `Localizable.strings` file would contain:

```
"NEW_MESSAGE_TITLE" = "%@ sent you a message";
"NEW_MESSAGE_BODY" = "%@ sent you %@ messages";
```

## Collapsing Notifications

Use `apns-collapse-id` header to replace previous notifications:

```java
SimpleApnsPushNotification notification = new SimpleApnsPushNotification(
    token,
    "com.yourcompany.app",
    payload,
    null,
    DeliveryPriority.IMMEDIATE,
    PushType.ALERT,
    "order-12345"  // Collapse ID - replaces previous notifications with same ID
);
```

## Error Handling

### Common APNs Error Reasons

| Reason | Description | Action |
|--------|-------------|--------|
| `BadDeviceToken` | Invalid token format or value | Deactivate token |
| `Unregistered` | Device token is no longer active | Deactivate token |
| `DeviceTokenNotForTopic` | Token not valid for this app | Deactivate token |
| `BadCertificate` | Invalid signing certificate | Fix credentials |
| `TooManyRequests` | Rate limit exceeded | Retry with backoff |
| `InternalServerError` | APNs server error | Retry with backoff |
| `ServiceUnavailable` | APNs temporarily unavailable | Retry with backoff |

### Java Error Handling

```java
PushNotificationResponse<SimpleApnsPushNotification> response =
    sendNotificationFuture.get();

if (!response.isAccepted()) {
    String rejectionReason = response.getRejectionReason();

    // Deactivate token for these errors
    if (rejectionReason != null && (
        rejectionReason.equals("BadDeviceToken") ||
        rejectionReason.equals("Unregistered") ||
        rejectionReason.equals("DeviceTokenNotForTopic"))) {

        deviceToken.deactivate();
        deviceRepository.save(deviceToken);

        return SendResult.failure(rejectionReason, "INVALID_TOKEN");
    }

    // Retry for these errors
    if (rejectionReason != null && (
        rejectionReason.equals("TooManyRequests") ||
        rejectionReason.equals("InternalServerError") ||
        rejectionReason.equals("ServiceUnavailable"))) {

        return SendResult.failure(rejectionReason, "RETRY_LATER");
    }

    return SendResult.failure(rejectionReason, "APNS_REJECTED");
}
```

## Testing

### Development Environment

```java
// Use sandbox APNs server for development
ApnsClient apnsClient = new ApnsClientBuilder()
    .setApnsServer(ApnsClientBuilder.DEVELOPMENT_APNS_HOST)
    .setSigningKey(signingKey)
    .build();
```

### Send Test Notification (curl)

```bash
# Generate JWT token (requires jose CLI or similar)
TOKEN=$(generate_apns_jwt.sh)

# Send notification
curl -v \
  --http2 \
  --header "apns-topic: com.yourcompany.app" \
  --header "authorization: bearer $TOKEN" \
  --data '{"aps":{"alert":"Test","sound":"default"}}' \
  https://api.push.apple.com/3/device/DEVICE_TOKEN_HERE
```

## Best Practices

1. **Token Management**
   - Use token-based authentication (easier to manage)
   - Store device tokens securely
   - Handle token invalidation gracefully

2. **Payload Size**
   - Maximum size: 4KB (regular), 5KB (VoIP)
   - Keep payloads minimal
   - Use notification service extensions for rich content

3. **Battery Optimization**
   - Use appropriate priority levels
   - Avoid sending too many notifications
   - Use silent notifications sparingly

4. **User Experience**
   - Respect notification preferences
   - Use meaningful categories and actions
   - Test on real devices

5. **Error Handling**
   - Implement exponential backoff
   - Deactivate invalid tokens
   - Monitor delivery metrics

6. **Security**
   - Protect signing keys
   - Use separate keys for development/production
   - Rotate keys periodically

## Dependencies

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.eatthepath</groupId>
    <artifactId>pushy</artifactId>
    <version>0.15.2</version>
</dependency>
```

```gradle
// build.gradle
implementation 'com.eatthepath:pushy:0.15.2'
```

## Additional Resources

- [APNs Provider API Documentation](https://developer.apple.com/documentation/usernotifications)
- [Pushy Library Documentation](https://github.com/jchambers/pushy)
- [APNs Payload Format](https://developer.apple.com/documentation/usernotifications/setting_up_a_remote_notification_server/generating_a_remote_notification)
