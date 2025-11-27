# Firebase Cloud Messaging (FCM) Payloads

## Overview

FCM HTTP v1 API is the recommended way to send push notifications to Android, iOS, and Web clients.

## Authentication

FCM requires OAuth 2.0 authentication using Google Service Account credentials.

```java
// Add dependency: com.google.firebase:firebase-admin:9.2.0

FirebaseOptions options = FirebaseOptions.builder()
    .setCredentials(GoogleCredentials.fromStream(
        new FileInputStream("path/to/service-account.json")))
    .build();

FirebaseApp.initializeApp(options);
```

## Basic Notification Payload

### Android Notification

```json
{
  "message": {
    "token": "fcm-device-token-here",
    "notification": {
      "title": "New Order",
      "body": "You have a new order #12345"
    },
    "android": {
      "priority": "HIGH",
      "notification": {
        "channel_id": "orders",
        "sound": "default",
        "color": "#FF0000",
        "icon": "notification_icon",
        "click_action": "OPEN_ORDER"
      }
    }
  }
}
```

### Java Code Example

```java
import com.google.firebase.messaging.*;

public SendResult sendFcmNotification(String token, String title, String body) {
    try {
        Message message = Message.builder()
            .setToken(token)
            .setNotification(Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build())
            .setAndroidConfig(AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(AndroidNotification.builder()
                    .setChannelId("default")
                    .setSound("default")
                    .setColor("#FF0000")
                    .build())
                .build())
            .build();

        String messageId = FirebaseMessaging.getInstance().send(message);
        return SendResult.success(messageId, "FCM");

    } catch (FirebaseMessagingException e) {
        return SendResult.failure(e.getMessage(), e.getErrorCode().name());
    }
}
```

## Data-Only Messages (Silent Push)

```json
{
  "message": {
    "token": "fcm-device-token-here",
    "data": {
      "type": "order_update",
      "order_id": "12345",
      "status": "shipped",
      "timestamp": "2025-01-20T10:30:00Z"
    },
    "android": {
      "priority": "HIGH"
    }
  }
}
```

### Java Code

```java
Message message = Message.builder()
    .setToken(token)
    .putAllData(Map.of(
        "type", "order_update",
        "order_id", "12345",
        "status", "shipped",
        "timestamp", Instant.now().toString()
    ))
    .setAndroidConfig(AndroidConfig.builder()
        .setPriority(AndroidConfig.Priority.HIGH)
        .build())
    .build();

String messageId = FirebaseMessaging.getInstance().send(message);
```

## Rich Notifications with Images

```json
{
  "message": {
    "token": "fcm-device-token-here",
    "notification": {
      "title": "New Product Launch",
      "body": "Check out our latest product!",
      "image": "https://example.com/images/product.jpg"
    },
    "android": {
      "notification": {
        "channel_id": "promotions",
        "image": "https://example.com/images/product.jpg",
        "style": "BIGPICTURE"
      }
    }
  }
}
```

## Action Buttons

```json
{
  "message": {
    "token": "fcm-device-token-here",
    "notification": {
      "title": "Payment Received",
      "body": "You received $50 from John Doe"
    },
    "android": {
      "notification": {
        "click_action": "VIEW_TRANSACTION"
      }
    },
    "data": {
      "action": "payment_received",
      "transaction_id": "txn-12345",
      "amount": "50.00",
      "sender": "John Doe"
    }
  }
}
```

## Topic-Based Messaging

```json
{
  "message": {
    "topic": "breaking-news",
    "notification": {
      "title": "Breaking News",
      "body": "Major event happening now!"
    },
    "android": {
      "priority": "HIGH"
    }
  }
}
```

### Java Code

```java
Message message = Message.builder()
    .setTopic("breaking-news")
    .setNotification(Notification.builder()
        .setTitle("Breaking News")
        .setBody("Major event happening now!")
        .build())
    .build();

String messageId = FirebaseMessaging.getInstance().send(message);
```

## Condition-Based Messaging

```java
Message message = Message.builder()
    .setCondition("'stock-alerts' in topics && 'tech' in topics")
    .setNotification(Notification.builder()
        .setTitle("AAPL Stock Alert")
        .setBody("Apple stock is up 5%")
        .build())
    .build();
```

## Batch Sending (Multicast)

```java
public SendResult sendToMultipleDevices(List<String> tokens, String title, String body) {
    try {
        MulticastMessage message = MulticastMessage.builder()
            .addAllTokens(tokens)
            .setNotification(Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build())
            .build();

        BatchResponse response = FirebaseMessaging.getInstance()
            .sendMulticast(message);

        // Check individual responses
        if (response.getFailureCount() > 0) {
            List<SendResponse> responses = response.getResponses();
            for (int i = 0; i < responses.size(); i++) {
                if (!responses.get(i).isSuccessful()) {
                    // Handle failed token (might be invalid/expired)
                    String failedToken = tokens.get(i);
                    Exception exception = responses.get(i).getException();
                    // Deactivate token in database
                }
            }
        }

        return SendResult.success(
            response.getSuccessCount() + " sent",
            "FCM_MULTICAST"
        );

    } catch (FirebaseMessagingException e) {
        return SendResult.failure(e.getMessage(), "FCM_BATCH_ERROR");
    }
}
```

## Error Handling

### Common FCM Error Codes

| Error Code | Description | Action |
|------------|-------------|--------|
| `INVALID_ARGUMENT` | Invalid token format | Deactivate token |
| `UNREGISTERED` | Token is no longer valid | Deactivate token |
| `SENDER_ID_MISMATCH` | Token registered to different sender | Deactivate token |
| `QUOTA_EXCEEDED` | Message rate exceeded | Retry with backoff |
| `UNAVAILABLE` | FCM service unavailable | Retry with backoff |
| `INTERNAL` | Internal FCM error | Retry with backoff |

### Java Error Handling

```java
try {
    String messageId = FirebaseMessaging.getInstance().send(message);
    return SendResult.success(messageId, "FCM");

} catch (FirebaseMessagingException e) {
    String errorCode = e.getErrorCode().name();

    // Deactivate token for these errors
    if (errorCode.equals("INVALID_ARGUMENT") ||
        errorCode.equals("UNREGISTERED") ||
        errorCode.equals("SENDER_ID_MISMATCH")) {

        // Mark token as inactive in database
        deviceToken.deactivate();
        deviceRepository.save(deviceToken);

        return SendResult.failure(e.getMessage(), "INVALID_TOKEN");
    }

    // Retry for these errors
    if (errorCode.equals("UNAVAILABLE") ||
        errorCode.equals("INTERNAL") ||
        errorCode.equals("QUOTA_EXCEEDED")) {

        return SendResult.failure(e.getMessage(), "RETRY_LATER");
    }

    return SendResult.failure(e.getMessage(), errorCode);
}
```

## Testing

### Send Test Notification

```bash
# Get access token
ACCESS_TOKEN=$(gcloud auth application-default print-access-token)

# Send notification
curl -X POST https://fcm.googleapis.com/v1/projects/YOUR_PROJECT_ID/messages:send \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "message": {
      "token": "YOUR_DEVICE_TOKEN",
      "notification": {
        "title": "Test Notification",
        "body": "This is a test message"
      }
    }
  }'
```

## Best Practices

1. **Token Management**
   - Store tokens in database with active flag
   - Deactivate tokens that return errors
   - Handle token refresh on client side

2. **Message Priority**
   - Use HIGH priority for time-sensitive notifications
   - Use NORMAL priority for non-urgent messages

3. **Payload Size**
   - Maximum payload size: 4KB
   - Keep data minimal for better performance

4. **Rate Limiting**
   - Implement exponential backoff for rate limit errors
   - Consider batching messages

5. **Testing**
   - Test on real devices
   - Use FCM test lab for automated testing
   - Monitor delivery reports

## Dependencies

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.google.firebase</groupId>
    <artifactId>firebase-admin</artifactId>
    <version>9.2.0</version>
</dependency>
```

```gradle
// build.gradle
implementation 'com.google.firebase:firebase-admin:9.2.0'
```
