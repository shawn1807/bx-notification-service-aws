# In-App Notification Integration

## Overview

In-app notifications allow real-time delivery of messages to connected web and mobile clients without relying on external push notification services.

## Architecture Options

### 1. WebSocket (Recommended for Bidirectional Communication)

Best for:
- Real-time chat applications
- Collaborative tools
- Live dashboards
- Two-way communication needed

### 2. Server-Sent Events (SSE)

Best for:
- One-way server-to-client updates
- Simpler implementation
- Better browser compatibility
- Automatic reconnection

## WebSocket Implementation

### Spring Boot Configuration

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple broker for /topic and /queue
        config.enableSimpleBroker("/topic", "/queue");

        // Set application destination prefix
        config.setApplicationDestinationPrefixes("/app");

        // Set user destination prefix
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register STOMP endpoint
        registry.addEndpoint("/ws/notifications")
            .setAllowedOriginPatterns("*")
            .withSockJS();
    }
}
```

### WebSocket Controller

```java
@Controller
public class WebSocketNotificationController {

    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public WebSocketNotificationController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Send notification to specific user
     */
    public void sendToUser(String userId, NotificationMessage notification) {
        messagingTemplate.convertAndSendToUser(
            userId,
            "/queue/notifications",
            notification
        );
    }

    /**
     * Broadcast notification to all connected users
     */
    public void broadcast(NotificationMessage notification) {
        messagingTemplate.convertAndSend(
            "/topic/notifications",
            notification
        );
    }

    /**
     * Handle subscription events
     */
    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = sha.getSessionId();
        String userId = sha.getUser().getName();

        log.info("User connected: userId={}, sessionId={}", userId, sessionId);

        // Send pending notifications on connect
        sendPendingNotifications(userId);
    }

    @EventListener
    public void handleSessionDisconnected(SessionDisconnectEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = sha.getSessionId();

        log.info("User disconnected: sessionId={}", sessionId);
    }

    private void sendPendingNotifications(String userId) {
        // Fetch unread notifications from database
        List<NotificationMessage> pending = fetchUnreadNotifications(userId);

        for (NotificationMessage notification : pending) {
            sendToUser(userId, notification);
        }
    }
}
```

### Notification Message DTO

```java
@Data
@Builder
public class NotificationMessage {
    private String id;
    private String type;
    private String title;
    private String body;
    private Map<String, Object> data;
    private Instant timestamp;
    private boolean read;
}
```

### Client Integration (JavaScript)

```javascript
// Using SockJS and STOMP
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

class NotificationClient {
    constructor(userId) {
        this.userId = userId;
        this.client = null;
        this.notifications = [];
    }

    connect() {
        const socket = new SockJS('http://localhost:8080/ws/notifications');

        this.client = new Client({
            webSocketFactory: () => socket,
            debug: (str) => console.log('STOMP: ' + str),

            onConnect: () => {
                console.log('Connected to WebSocket');

                // Subscribe to user-specific queue
                this.client.subscribe(`/user/queue/notifications`, (message) => {
                    const notification = JSON.parse(message.body);
                    this.handleNotification(notification);
                });

                // Subscribe to broadcast topic
                this.client.subscribe('/topic/notifications', (message) => {
                    const notification = JSON.parse(message.body);
                    this.handleNotification(notification);
                });
            },

            onStompError: (frame) => {
                console.error('STOMP error', frame);
            }
        });

        this.client.activate();
    }

    handleNotification(notification) {
        console.log('Received notification:', notification);

        this.notifications.push(notification);

        // Show browser notification
        if (Notification.permission === 'granted') {
            new Notification(notification.title, {
                body: notification.body,
                icon: '/icon.png',
                tag: notification.id
            });
        }

        // Update UI
        this.updateBadgeCount();
        this.displayNotification(notification);
    }

    updateBadgeCount() {
        const unread = this.notifications.filter(n => !n.read).length;
        document.getElementById('notification-badge').textContent = unread;
    }

    displayNotification(notification) {
        // Add to notification list in UI
        const notificationList = document.getElementById('notification-list');
        const item = document.createElement('div');
        item.className = 'notification-item';
        item.innerHTML = `
            <h4>${notification.title}</h4>
            <p>${notification.body}</p>
            <small>${new Date(notification.timestamp).toLocaleString()}</small>
        `;
        notificationList.prepend(item);
    }

    disconnect() {
        if (this.client) {
            this.client.deactivate();
        }
    }
}

// Usage
const notificationClient = new NotificationClient('user-123');
notificationClient.connect();
```

### React Integration

```javascript
import React, { useEffect, useState } from 'react';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

function NotificationProvider({ userId, children }) {
    const [notifications, setNotifications] = useState([]);
    const [client, setClient] = useState(null);

    useEffect(() => {
        const socket = new SockJS('http://localhost:8080/ws/notifications');

        const stompClient = new Client({
            webSocketFactory: () => socket,

            onConnect: () => {
                console.log('Connected');

                stompClient.subscribe(`/user/queue/notifications`, (message) => {
                    const notification = JSON.parse(message.body);
                    setNotifications(prev => [notification, ...prev]);
                });
            }
        });

        stompClient.activate();
        setClient(stompClient);

        return () => {
            stompClient.deactivate();
        };
    }, [userId]);

    return (
        <NotificationContext.Provider value={{ notifications, client }}>
            {children}
        </NotificationContext.Provider>
    );
}
```

## Server-Sent Events (SSE) Implementation

### Spring Boot Controller

```java
@RestController
@RequestMapping("/api/v1/notifications")
public class SseNotificationController {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * Subscribe to notification stream
     */
    @GetMapping(path = "/stream/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications(@PathVariable String userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        // Store emitter
        emitters.put(userId, emitter);

        // Handle completion and timeout
        emitter.onCompletion(() -> {
            log.info("SSE completed for user: {}", userId);
            emitters.remove(userId);
        });

        emitter.onTimeout(() -> {
            log.warn("SSE timeout for user: {}", userId);
            emitters.remove(userId);
        });

        emitter.onError(e -> {
            log.error("SSE error for user: {}", userId, e);
            emitters.remove(userId);
        });

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data("Successfully connected to notification stream"));

            // Send pending notifications
            sendPendingNotifications(userId, emitter);

        } catch (IOException e) {
            log.error("Error sending initial event", e);
            emitters.remove(userId);
        }

        return emitter;
    }

    /**
     * Send notification to user via SSE
     */
    public void sendNotification(String userId, NotificationMessage notification) {
        SseEmitter emitter = emitters.get(userId);

        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                    .name("notification")
                    .id(notification.getId())
                    .data(notification));

                log.info("Sent SSE notification to user: {}", userId);

            } catch (IOException e) {
                log.error("Failed to send SSE notification", e);
                emitters.remove(userId);
            }
        } else {
            log.debug("No active SSE connection for user: {}", userId);
        }
    }

    /**
     * Broadcast to all connected users
     */
    public void broadcast(NotificationMessage notification) {
        emitters.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                    .name("notification")
                    .data(notification));
            } catch (IOException e) {
                log.error("Failed to broadcast to user: {}", userId, e);
                emitters.remove(userId);
            }
        });
    }

    private void sendPendingNotifications(String userId, SseEmitter emitter) {
        // Fetch and send unread notifications
        List<NotificationMessage> pending = fetchUnreadNotifications(userId);

        for (NotificationMessage notification : pending) {
            try {
                emitter.send(SseEmitter.event()
                    .name("notification")
                    .data(notification));
            } catch (IOException e) {
                log.error("Error sending pending notification", e);
                break;
            }
        }
    }
}
```

### Client Integration (JavaScript)

```javascript
class SseNotificationClient {
    constructor(userId) {
        this.userId = userId;
        this.eventSource = null;
        this.notifications = [];
    }

    connect() {
        this.eventSource = new EventSource(
            `http://localhost:8080/api/v1/notifications/stream/${this.userId}`
        );

        this.eventSource.addEventListener('connected', (event) => {
            console.log('Connected to SSE:', event.data);
        });

        this.eventSource.addEventListener('notification', (event) => {
            const notification = JSON.parse(event.data);
            this.handleNotification(notification);
        });

        this.eventSource.onerror = (error) => {
            console.error('SSE error:', error);

            // Automatic reconnection happens by default
            // But you can implement custom retry logic here
            if (this.eventSource.readyState === EventSource.CLOSED) {
                console.log('SSE connection closed, will retry...');
            }
        };
    }

    handleNotification(notification) {
        console.log('Received notification:', notification);

        this.notifications.push(notification);
        this.updateUI(notification);
    }

    updateUI(notification) {
        // Show browser notification
        if (Notification.permission === 'granted') {
            new Notification(notification.title, {
                body: notification.body
            });
        }

        // Update badge count
        const unreadCount = this.notifications.filter(n => !n.read).length;
        document.getElementById('notification-badge').textContent = unreadCount;
    }

    disconnect() {
        if (this.eventSource) {
            this.eventSource.close();
        }
    }
}

// Usage
const client = new SseNotificationClient('user-123');
client.connect();
```

### React SSE Hook

```javascript
import { useEffect, useState } from 'react';

function useNotificationStream(userId) {
    const [notifications, setNotifications] = useState([]);
    const [isConnected, setIsConnected] = useState(false);

    useEffect(() => {
        const eventSource = new EventSource(
            `http://localhost:8080/api/v1/notifications/stream/${userId}`
        );

        eventSource.addEventListener('connected', () => {
            setIsConnected(true);
        });

        eventSource.addEventListener('notification', (event) => {
            const notification = JSON.parse(event.data);
            setNotifications(prev => [notification, ...prev]);
        });

        eventSource.onerror = () => {
            setIsConnected(false);
        };

        return () => {
            eventSource.close();
        };
    }, [userId]);

    return { notifications, isConnected };
}

// Usage
function NotificationComponent({ userId }) {
    const { notifications, isConnected } = useNotificationStream(userId);

    return (
        <div>
            <div>Status: {isConnected ? 'Connected' : 'Disconnected'}</div>
            <div>Unread: {notifications.filter(n => !n.read).length}</div>
            <ul>
                {notifications.map(n => (
                    <li key={n.id}>
                        <h4>{n.title}</h4>
                        <p>{n.body}</p>
                    </li>
                ))}
            </ul>
        </div>
    );
}
```

## Badge Count Management

### Server-Side

```java
@Service
public class BadgeCountService {

    private final NotificationRepository notificationRepository;

    public int getUnreadCount(String userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    public void incrementBadge(String userId) {
        // Increment badge count in database or cache
        redisTemplate.opsForValue().increment("badge:" + userId);
    }

    public void resetBadge(String userId) {
        redisTemplate.delete("badge:" + userId);
    }
}
```

### Client-Side

```javascript
// Fetch unread count on app launch
async function fetchUnreadCount(userId) {
    const response = await fetch(`/api/v1/notifications/user/${userId}/unread-count`);
    const data = await response.json();
    updateBadge(data.count);
}

// Update badge display
function updateBadge(count) {
    const badge = document.getElementById('notification-badge');
    badge.textContent = count;
    badge.style.display = count > 0 ? 'inline' : 'none';

    // Update favicon badge (optional)
    updateFaviconBadge(count);
}
```

## Mobile Client Integration

### Android (Kotlin)

```kotlin
class NotificationWebSocketClient(private val userId: String) {

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    fun connect() {
        val request = Request.Builder()
            .url("ws://api.example.com/ws/notifications")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Subscribe to user notifications
                val subscribeMessage = """
                    {
                        "type": "SUBSCRIBE",
                        "userId": "$userId"
                    }
                """.trimIndent()

                webSocket.send(subscribeMessage)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val notification = Gson().fromJson(text, NotificationMessage::class.java)
                handleNotification(notification)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Reconnect with exponential backoff
                scheduleReconnect()
            }
        })
    }

    private fun handleNotification(notification: NotificationMessage) {
        // Update badge count
        updateBadgeCount()

        // Show in-app notification
        showInAppNotification(notification)
    }

    fun disconnect() {
        webSocket?.close(1000, "User logout")
    }
}
```

### iOS (Swift)

```swift
import Starscream

class NotificationWebSocketManager: WebSocketDelegate {

    private var socket: WebSocket?
    private let userId: String

    init(userId: String) {
        self.userId = userId
    }

    func connect() {
        var request = URLRequest(url: URL(string: "ws://api.example.com/ws/notifications")!)
        request.timeoutInterval = 5

        socket = WebSocket(request: request)
        socket?.delegate = self
        socket?.connect()
    }

    func websocketDidConnect(socket: WebSocketClient) {
        print("WebSocket connected")

        // Subscribe to user notifications
        let subscribeMessage: [String: Any] = [
            "type": "SUBSCRIBE",
            "userId": userId
        ]

        if let jsonData = try? JSONSerialization.data(withJSONObject: subscribeMessage),
           let jsonString = String(data: jsonData, encoding: .utf8) {
            socket.write(string: jsonString)
        }
    }

    func websocketDidReceiveMessage(socket: WebSocketClient, text: String) {
        if let data = text.data(using: .utf8),
           let notification = try? JSONDecoder().decode(NotificationMessage.self, from: data) {
            handleNotification(notification)
        }
    }

    private func handleNotification(_ notification: NotificationMessage) {
        // Update badge
        UIApplication.shared.applicationIconBadgeNumber += 1

        // Show in-app notification
        showInAppNotification(notification)
    }

    func disconnect() {
        socket?.disconnect()
    }
}
```

## Comparison: WebSocket vs SSE

| Feature | WebSocket | SSE |
|---------|-----------|-----|
| Communication | Bidirectional | Unidirectional (server→client) |
| Protocol | ws:// or wss:// | HTTP/HTTPS |
| Browser Support | Excellent | Good (no IE support) |
| Automatic Reconnect | Manual | Automatic |
| Binary Data | Yes | No (text only) |
| Complexity | Higher | Lower |
| Firewall Issues | Sometimes | Rare |
| Use Case | Real-time chat, gaming | Notifications, live updates |

## Best Practices

1. **Connection Management**
   - Implement reconnection logic with exponential backoff
   - Handle network valueChangeds gracefully
   - Clean up connections on logout

2. **Performance**
   - Use connection pooling
   - Implement message batching for high volume
   - Cache unread counts

3. **Security**
   - Authenticate WebSocket/SSE connections
   - Use WSS/HTTPS in production
   - Validate user permissions

4. **User Experience**
   - Show connection status
   - Queue messages during disconnection
   - Implement optimistic UI updates

5. **Scalability**
   - Use Redis pub/sub for horizontal scaling
   - Implement sticky sessions or session persistence
   - Consider dedicated notification server

## Dependencies

```xml
<!-- WebSocket -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>

<!-- SSE (included in spring-boot-starter-web) -->
```

```javascript
// Client-side
npm install sockjs-client @stomp/stompjs

// Or for SSE, use native EventSource API
```
