# FCM Test Application

A simple Spring Boot application for testing Firebase Cloud Messaging (FCM) push notifications locally.

## Features

- ✅ Send FCM push notifications via REST API
- ✅ Support for notification title, body, and image
- ✅ Custom data payload support
- ✅ Android and iOS specific configurations
- ✅ Error handling with permanent error detection
- ✅ Token validation and logging

## Prerequisites

- Java 21+
- Maven 3.9+
- Firebase project with Admin SDK credentials
- Android/iOS app with FCM integrated

## Setup

### 1. Firebase Project Setup

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a new project or select an existing one
3. Go to Project Settings → Service Accounts
4. Click "Generate New Private Key"
5. Download the JSON file (e.g., `firebase-service-account.json`)

### 2. Configure Application

Option A: Using configuration file path
```bash
export FIREBASE_CREDENTIALS_PATH=/path/to/firebase-service-account.json
```

Option B: Using environment variable (recommended for production)
```bash
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/firebase-service-account.json
```

### 3. Build and Run

```bash
# Build the application
mvn clean package

# Run the application
mvn spring-boot:run

# Or run the JAR directly
java -jar target/fcm-test-app-1.0.0.jar
```

The application will start on `http://localhost:8081`

## API Endpoints

### 1. Test Service Health

```bash
curl http://localhost:8081/api/fcm/test
```

Response:
```json
{
  "status": "ok",
  "message": "FCM Test Service is running",
  "version": "1.0.0"
}
```

### 2. Send Simple Notification

```bash
curl -X POST "http://localhost:8081/api/fcm/send-simple" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=YOUR_FCM_DEVICE_TOKEN" \
  -d "title=Hello" \
  -d "body=This is a test notification"
```

### 3. Send Test Notification

```bash
curl -X POST "http://localhost:8081/api/fcm/send-test?token=YOUR_FCM_DEVICE_TOKEN"
```

### 4. Send Full Notification with Data

```bash
curl -X POST http://localhost:8081/api/fcm/send \
  -H "Content-Type: application/json" \
  -d '{
    "token": "YOUR_FCM_DEVICE_TOKEN",
    "title": "Order Update",
    "body": "Your order #12345 has been shipped",
    "imageUrl": "https://example.com/image.jpg",
    "data": {
      "orderId": "12345",
      "type": "ORDER_SHIPPED",
      "timestamp": "2025-12-04T10:30:00Z"
    }
  }'
```

## Response Format

### Success Response
```json
{
  "success": true,
  "messageId": "projects/my-project/messages/0:1234567890123456",
  "error": null,
  "errorCode": null
}
```

### Error Response
```json
{
  "success": false,
  "messageId": null,
  "error": "Registration token is invalid",
  "errorCode": "INVALID_ARGUMENT"
}
```

## Getting Device Token

### Android (Kotlin)
```kotlin
FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
    if (task.isSuccessful) {
        val token = task.result
        Log.d("FCM", "Device token: $token")
    }
}
```

### iOS (Swift)
```swift
Messaging.messaging().token { token, error in
    if let error = error {
        print("Error fetching FCM token: \(error)")
    } else if let token = token {
        print("FCM token: \(token)")
    }
}
```

### React Native (JavaScript)
```javascript
import messaging from '@react-native-firebase/messaging';

async function getToken() {
  const token = await messaging().getToken();
  console.log('FCM Token:', token);
}
```

## Error Codes

The application handles various FCM error codes:

| Error Code | Description | Permanent? |
|-----------|-------------|------------|
| INVALID_ARGUMENT | Invalid token format | Yes |
| NOT_FOUND | Token not found | Yes |
| UNAVAILABLE | Token unregistered | Yes |
| INTERNAL | Internal server error | No |
| QUOTA_EXCEEDED | Rate limit exceeded | No |

**Permanent errors** indicate the device token should be removed from your database.

## Testing Tips

1. **Use FCM Console**: Test notifications via Firebase Console → Cloud Messaging → Send test message
2. **Check token validity**: Tokens can expire or become invalid if the app is uninstalled
3. **Test on real devices**: Emulators may have issues receiving notifications
4. **Check app state**: Test with app in foreground, background, and killed states
5. **Monitor logs**: Check application logs for detailed error messages

## Configuration

### Application Properties

Edit `src/main/resources/application.yml`:

```yaml
server:
  port: 8081  # ValueChange port if needed

firebase:
  credentials:
    path: /path/to/firebase-service-account.json

logging:
  level:
    com.tsu.fcmtest: DEBUG  # ValueChange to DEBUG for more details
```

## Project Structure

```
fcm-test-app/
├── src/main/java/com/tsu/fcmtest/
│   ├── FcmTestApplication.java          # Main application class
│   ├── config/
│   │   └── FirebaseConfig.java          # Firebase initialization
│   ├── controller/
│   │   └── FcmController.java           # REST API endpoints
│   ├── dto/
│   │   ├── PushNotificationRequest.java # Request DTO
│   │   └── PushNotificationResponse.java # Response DTO
│   └── service/
│       └── FcmService.java              # FCM business logic
├── src/main/resources/
│   └── application.yml                  # Application configuration
└── pom.xml                              # Maven dependencies
```

## Dependencies

- Spring Boot 3.2.0
- Firebase Admin SDK 9.2.0
- Lombok (for reducing boilerplate)

## Troubleshooting

### Error: "Failed to initialize Firebase"

- Check that the credentials file path is correct
- Verify the JSON file is valid
- Ensure `GOOGLE_APPLICATION_CREDENTIALS` or `FIREBASE_CREDENTIALS_PATH` is set

### Error: "INVALID_ARGUMENT"

- Token format is invalid
- Token may be from a different Firebase project
- Check that the token is not expired

### Error: "NOT_FOUND" or "UNAVAILABLE"

- App was uninstalled from the device
- Token was manually deleted
- Token expired (tokens can expire after ~2 months of inactivity)

### Notifications not received

- Check device internet connection
- Verify FCM is properly configured in your mobile app
- Test with Firebase Console first
- Check mobile app notification permissions

## Production Considerations

When moving to production:

1. **Use environment variables** for credentials (not hardcoded paths)
2. **Implement rate limiting** to prevent abuse
3. **Add authentication** to protect endpoints
4. **Store tokens in database** with user associations
5. **Handle token refresh** when apps reinstall or update
6. **Implement retry logic** for transient errors
7. **Monitor error rates** and set up alerts
8. **Use batch sending** for multiple recipients (up to 500 per batch)

## Batch Sending Example

```java
List<Message> messages = tokens.stream()
    .map(token -> Message.builder()
        .setToken(token)
        .setNotification(notification)
        .build())
    .collect(Collectors.toList());

BatchResponse response = FirebaseMessaging.getInstance().sendAll(messages);
System.out.println(response.getSuccessCount() + " messages sent successfully");
```

## References

- [Firebase Cloud Messaging Documentation](https://firebase.google.com/docs/cloud-messaging)
- [Firebase Admin SDK for Java](https://firebase.google.com/docs/admin/setup)
- [FCM HTTP v1 API](https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages)

## License

This project is licensed under the MIT License.
