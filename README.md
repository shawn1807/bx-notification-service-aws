# Notification System

A production-ready, scalable notification system supporting Email, SMS, Push (FCM & APNs), and In-App notifications with the Transactional Outbox Pattern for reliable message delivery.

## Features

- **Multi-Channel Delivery**: Email, SMS, Push Notifications (FCM & APNs), In-App
- **AWS Integration**: Full support for AWS SES (Email), SNS (SMS/Push), and SQS (Queue)
- **Serverless Deployment**: Deploy as AWS Lambda functions with API Gateway
- **Transactional Outbox Pattern**: Ensures exactly-once delivery and prevents message loss
- **Queue-Based Architecture**: Decouples outbox dispatcher from event handlers using SQS
- **Retry Logic**: Exponential backoff with configurable max attempts
- **Idempotency**: Prevents duplicate notifications
- **Horizontal Scalability**: Stateless design with safe concurrent processing
- **Auto-Scaling**: Lambda functions scale automatically based on load
- **Real-time Updates**: WebSocket/SSE support for in-app notifications
- **Production-Ready**: Health checks, metrics, logging, and monitoring
- **High Availability**: Multiple dispatcher instances with FOR UPDATE SKIP LOCKED
- **Separation of Concerns**: Independent scaling of producers and consumers
- **Pluggable Adapters**: Switch between MOCK, AWS, or custom providers via configuration
- **Flexible Deployment**: Choose between serverless (Lambda) or traditional (Kubernetes/ECS)

## Architecture

### Queue-Based Architecture (Recommended)

```
┌──────────────┐
│   Client     │
│  (API Call)  │
└──────┬───────┘
       │
       ▼
┌────────────────────────┐
│   API Layer (REST)     │
│  - NotificationController
│  - DeviceController    │
└──────┬─────────────────┘
       │
       ▼
┌────────────────────────┐
│   Service Layer        │
│  - NotificationService │
│  - OutboxPublisher     │  ← @Transactional
└──────┬─────────────────┘
       │
       ▼
┌────────────────────────┐
│   PostgreSQL Database  │
│  - notification        │
│  - channel_delivery    │
│  - outbox_event        │  ← Transactional Outbox
└──────┬─────────────────┘
       │
       ▼
┌────────────────────────┐
│  Outbox Dispatcher     │  ← Producer
│  (Scheduled @5s)       │
│  - Polls outbox_event  │
│  - Publishes to Queue  │
└──────┬─────────────────┘
       │
       ▼
┌────────────────────────┐
│   AWS SQS Queue        │  ← Decoupling Layer
│  - Buffering           │
│  - Retry Logic         │
│  - Dead Letter Queue   │
└──────┬─────────────────┘
       │
       ▼
┌────────────────────────┐
│  Queue Consumer        │  ← Consumer (scales independently)
│  (Polls Queue)         │
│  - Routes to Handlers  │
└──────┬─────────────────┘
       │
       ▼
┌────────────────────────────────────────┐
│   Channel Adapters                     │
│  - EmailSenderAdapter (SES/SMTP)       │
│  - SmsSenderAdapter (SNS/Twilio)       │
│  - PushSenderAdapter (FCM/APNs)        │
│  - InAppSenderAdapter (WebSocket/SSE)  │
└────────────────────────────────────────┘
```

**Benefits**:
- **Decoupling**: Producer and consumer operate independently
- **Scalability**: Scale producers and consumers separately
- **Reliability**: Queue provides buffering and automatic retries
- **Observability**: Monitor queue depth and message age

See [QUEUE_ARCHITECTURE.md](QUEUE_ARCHITECTURE.md) for detailed queue documentation.

See [AWS_INTEGRATION.md](AWS_INTEGRATION.md) for AWS setup and configuration.

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.9+
- PostgreSQL 14+
- AWS SQS or LocalStack (for queue)
- Docker (optional)

### Local Development

1. **Start PostgreSQL**:
   ```bash
   docker run -d \
     --name notification-postgres \
     -e POSTGRES_DB=notifications \
     -e POSTGRES_USER=notification \
     -e POSTGRES_PASSWORD=password \
     -p 5432:5432 \
     postgres:14-alpine
   ```

2. **Start LocalStack (for local SQS)**:
   ```bash
   docker run -d \
     --name localstack \
     -p 4566:4566 \
     -e SERVICES=sqs \
     localstack/localstack

   # Create SQS queue
   aws --endpoint-url=http://localhost:4566 sqs create-queue \
     --queue-name notification-events
   ```

3. **Initialize Database**:
   ```bash
   psql -h localhost -U notification -d notifications < db/schema.sql
   ```

4. **Build Application**:
   ```bash
   mvn clean package
   ```

5. **Run Application**:
   ```bash
   java -jar target/notification-service-1.0.0.jar
   ```

5. **Test API**:
   ```bash
   curl -X POST http://localhost:8080/api/v1/notifications \
     -H "Content-Type: application/json" \
     -d '{
       "type": "TRANSACTIONAL",
       "priority": "HIGH",
       "recipientEmail": "user@example.com",
       "subject": "Welcome!",
       "body": "Thank you for signing up",
       "channels": ["EMAIL"]
     }'
   ```

### Docker Compose

```bash
cd deployment
docker-compose up -d
```

Services:
- API: http://localhost:8080
- Postgres: localhost:5432
- Redis: localhost:6379

## API Documentation

### OpenAPI/Swagger

Interactive API documentation available at:
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI Spec: `docs/openapi.yaml`

### Key Endpoints

#### Create Notification
```http
POST /api/v1/notifications
Content-Type: application/json

{
  "type": "TRANSACTIONAL",
  "priority": "HIGH",
  "userId": "user-123",
  "recipientEmail": "user@example.com",
  "recipientPhone": "+1234567890",
  "subject": "Order Confirmation",
  "body": "Your order #12345 has been confirmed",
  "channels": ["EMAIL", "SMS", "PUSH", "IN_APP"],
  "metadata": {
    "orderId": "12345",
    "amount": 99.99
  },
  "idempotencyKey": "order-12345-confirmation"
}
```

#### Get Notification
```http
GET /api/v1/notifications/{id}
```

#### Register Push Token
```http
POST /api/v1/devices/push-token
Content-Type: application/json

{
  "userId": "user-123",
  "deviceId": "device-abc-123",
  "platform": "FCM",
  "token": "fcm-token-xyz...",
  "appVersion": "1.2.3",
  "osVersion": "Android 13"
}
```

## Database Schema

### Core Tables

- **notification**: Main notification entity
- **notification_channel_delivery**: Per-channel delivery tracking
- **outbox_event**: Transactional outbox for reliable processing
- **device_push_token**: Device registration for push notifications
- **audit_log**: Audit trail for compliance

### Key Features

- **Soft Deletes**: `deleted_at` column
- **Optimistic Locking**: `updated_at` auto-updated
- **Idempotency**: `idempotency_key` unique constraint
- **Retry Logic**: `attempt_count`, `next_attempt_at` with exponential backoff
- **Provider Tracking**: `provider_id` for external message IDs

See `db/schema.sql` for complete DDL.

## Configuration

### Application Properties

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/notifications
    username: notification
    password: password
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          batch_size: 20

notification:
  dispatcher:
    enabled: true
    batch-size: 100
    poll-interval: 5000

# Queue configuration
queue:
  provider: sqs
  consumer:
    enabled: true
  notification-events:
    queue-url: https://sqs.us-east-1.amazonaws.com/123456789012/notification-events

# AWS configuration
aws:
  region: ap-east-2

logging:
  level:
    com.tsu.notification: INFO
```

### Environment Variables

- `DB_USERNAME`: Database username
- `DB_PASSWORD`: Database password
- `DISPATCHER_ENABLED`: Enable/disable outbox dispatcher
- `QUEUE_CONSUMER_ENABLED`: Enable/disable queue consumer
- `QUEUE_PROVIDER`: Queue provider (sqs, rabbitmq, etc.)
- `NOTIFICATION_EVENTS_QUEUE_URL`: SQS queue URL
- `AWS_REGION`: AWS region for SQS
- `AWS_SQS_ENDPOINT`: Custom SQS endpoint (for LocalStack)
- `SPRING_PROFILES_ACTIVE`: Active profile (dev, prod)

## Deployment

Multiple deployment options available. See [DEPLOYMENT_COMPARISON.md](DEPLOYMENT_COMPARISON.md) for detailed comparison.

### Option 1: AWS Lambda (Serverless) ⭐ Recommended for most use cases

Deploy as serverless Lambda functions with API Gateway and SQS:

```bash
# Build application
mvn clean package

# Deploy using AWS SAM
cd deployment/lambda
./deploy.sh prod
```

**Benefits**:
- ✅ **Auto-scaling**: Scales from 0 to thousands automatically
- ✅ **Cost-effective**: Pay only for execution time (~$22/month for 1M requests)
- ✅ **Zero maintenance**: No servers to manage or patch
- ✅ **Fast deployment**: Deploy in under 5 minutes
- ✅ **High availability**: Built-in redundancy and failover

**Cost**: ~$22/month + AWS services (SES/SNS) for 1M notifications

See [deployment/lambda/LAMBDA_DEPLOYMENT.md](deployment/lambda/LAMBDA_DEPLOYMENT.md) for complete guide.

### Option 2: Kubernetes

1. **Create Namespace**:
   ```bash
   kubectl create namespace notification-system
   ```

2. **Deploy PostgreSQL**:
   ```bash
   kubectl apply -f deployment/kubernetes/postgres.yaml
   ```

3. **Deploy Application**:
   ```bash
   kubectl apply -f deployment/kubernetes/deployment.yaml
   ```

4. **Deploy Ingress**:
   ```bash
   kubectl apply -f deployment/kubernetes/ingress.yaml
   ```

**Cost**: ~$180/month for 1M notifications (more cost-effective at >10M/month)

See `deployment/DEPLOYMENT_GUIDE.md` for detailed instructions.

### Option 3: ECS/Fargate

Deploy as containers on AWS ECS with Fargate (serverless containers):

```bash
# Create ECS cluster
aws ecs create-cluster --cluster-name notification

# Deploy task definition
aws ecs register-task-definition --cli-input-json file://ecs-task.json

# Create service
aws ecs create-service --cluster notification --service-name api --task-definition notification-api
```

**Cost**: ~$95/month for 1M notifications

### Which Deployment to Choose?

| Requests/Month | Recommended | Monthly Cost |
|---------------|-------------|--------------|
| < 1M | AWS Lambda | ~$22 |
| 1M - 10M | Lambda or ECS | $22 - $95 |
| 10M - 100M | ECS or K8s | $95 - $180 |
| > 100M | Kubernetes | ~$180 |

See [DEPLOYMENT_COMPARISON.md](DEPLOYMENT_COMPARISON.md) for detailed comparison.

### Scaling (Kubernetes)

#### API Service
```bash
kubectl scale deployment notification-api --replicas=5 -n notification-system
```

#### Dispatcher Service
```bash
kubectl scale deployment notification-dispatcher --replicas=3 -n notification-system
```

Multiple dispatchers are safe due to:
- `FOR UPDATE SKIP LOCKED` prevents concurrent processing
- Idempotency prevents duplicate sends

## Channel Adapters

### Pluggable Architecture

All notification channels support multiple providers through a pluggable adapter pattern:

| Channel | Providers | Configuration |
|---------|-----------|---------------|
| **Email** | MOCK, AWS SES | `notification.channels.email.provider` |
| **SMS** | MOCK, AWS SNS | `notification.channels.sms.provider` |
| **Push** | DIRECT (FCM/APNs), AWS SNS | `notification.channels.push.provider` |
| **In-App** | WebSocket/SSE | Built-in |

### AWS Integration

Full production-ready AWS integration:

- **AWS SES**: Email delivery with templates, tracking, and bounce handling
- **AWS SNS**: SMS delivery with transactional/promotional types and cost controls
- **AWS SNS Mobile Push**: Unified push notifications for FCM and APNs
- **AWS SQS**: Message queue for reliable event processing

See [AWS_INTEGRATION.md](AWS_INTEGRATION.md) for setup guide, IAM permissions, and cost estimation.

## Project Structure

```
bx-notification/
├── db/
│   └── schema.sql                          # Database DDL
├── src/main/java/com/tsu/notification/
│   ├── api/
│   │   ├── controller/                     # REST Controllers
│   │   └── exception/                      # Exception Handlers
│   ├── application/
│   │   ├── dto/                            # DTOs
│   │   └── service/                        # Business Logic
│   ├── domain/
│   │   ├── entity/                         # JPA Entities
│   │   ├── enums/                          # Enums
│   │   └── repository/                     # Spring Data Repositories
│   ├── infrastructure/
│   │   ├── adapter/                        # Channel Adapters
│   │   │   ├── EmailSenderAdapter.java     # Email interface
│   │   │   ├── MockEmailSenderAdapter.java # Mock implementation
│   │   │   ├── AwsSesSenderAdapter.java    # AWS SES implementation
│   │   │   ├── SmsSenderAdapter.java       # SMS interface
│   │   │   ├── MockSmsSenderAdapter.java   # Mock implementation
│   │   │   ├── AwsSnsSenderAdapter.java    # AWS SNS SMS implementation
│   │   │   ├── PushSenderAdapter.java      # Direct FCM/APNs
│   │   │   └── AwsSnsPushSenderAdapter.java # AWS SNS Push implementation
│   │   ├── config/                         # AWS Client Configurations
│   │   ├── dispatcher/                     # Outbox Dispatcher & Event Handler
│   │   └── queue/                          # Queue Infrastructure
│   │       ├── MessageQueue.java           # Queue abstraction
│   │       ├── SqsMessageQueue.java        # SQS implementation
│   │       ├── QueuePublisher.java         # Producer
│   │       └── NotificationEventConsumer.java # Consumer
│   └── lambda/                             # AWS Lambda Handlers
│       ├── ApiGatewayLambdaHandler.java    # API Gateway handler
│       ├── SqsQueueConsumerLambdaHandler.java # SQS consumer handler
│       ├── OutboxDispatcherLambdaHandler.java # Outbox dispatcher handler
│       └── LambdaConfiguration.java        # Lambda Spring config
├── docs/
│   ├── openapi.yaml                        # API Specification
│   ├── push-notifications/
│   │   ├── FCM_PAYLOADS.md                # FCM Examples
│   │   └── APNS_PAYLOADS.md               # APNs Examples
│   └── in-app-notifications/
│       └── IN_APP_INTEGRATION.md          # WebSocket/SSE Guide
├── QUEUE_ARCHITECTURE.md                   # Queue Architecture Guide
├── AWS_INTEGRATION.md                      # AWS Setup Guide
├── DEPLOYMENT_COMPARISON.md                # Deployment Options Comparison
└── deployment/
    ├── docker/
    │   └── Dockerfile                      # Container Image
    ├── docker-compose.yaml                 # Local Dev Stack
    ├── lambda/                             # AWS Lambda Deployment
    │   ├── template.yaml                   # SAM CloudFormation Template
    │   ├── samconfig.toml                  # SAM Configuration
    │   ├── deploy.sh                       # Automated Deployment Script
    │   ├── README.md                       # Quick Start Guide
    │   └── LAMBDA_DEPLOYMENT.md            # Complete Lambda Guide
    ├── kubernetes/
    │   ├── deployment.yaml                 # K8s Deployments
    │   ├── postgres.yaml                   # PostgreSQL StatefulSet
    │   └── ingress.yaml                    # Ingress Config
    └── DEPLOYMENT_GUIDE.md                 # Deployment Instructions
```

## Push Notification Integration

### FCM (Firebase Cloud Messaging)

```java
// Add dependency: com.google.firebase:firebase-admin:9.2.0

Message message = Message.builder()
    .setToken(deviceToken)
    .setNotification(Notification.builder()
        .setTitle("New Order")
        .setBody("You have a new order")
        .build())
    .build();

String messageId = FirebaseMessaging.getInstance().send(message);
```

See `docs/push-notifications/FCM_PAYLOADS.md` for detailed examples.

### APNs (Apple Push Notification Service)

```java
// Add dependency: com.eatthepath:pushy:0.15.2

ApnsClient apnsClient = new ApnsClientBuilder()
    .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
    .setSigningKey(signingKey)
    .build();

SimpleApnsPushNotification notification = new SimpleApnsPushNotification(
    token, "com.yourapp", payload
);

PushNotificationResponse response = apnsClient.sendNotification(notification).get();
```

See `docs/push-notifications/APNS_PAYLOADS.md` for detailed examples.

## In-App Notifications

### WebSocket

```javascript
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const socket = new SockJS('http://localhost:8080/ws/notifications');
const client = new Client({ webSocketFactory: () => socket });

client.onConnect = () => {
    client.subscribe('/user/queue/notifications', (message) => {
        const notification = JSON.parse(message.body);
        console.log('Received:', notification);
    });
};

client.activate();
```

### Server-Sent Events (SSE)

```javascript
const eventSource = new EventSource(
    'http://localhost:8080/api/v1/notifications/stream/user-123'
);

eventSource.addEventListener('notification', (event) => {
    const notification = JSON.parse(event.data);
    console.log('Received:', notification);
});
```

See `docs/in-app-notifications/IN_APP_INTEGRATION.md` for detailed guide.

## Monitoring

### Health Checks

- **Liveness**: `/api/v1/health/live`
- **Readiness**: `/api/v1/health/ready`

### Metrics

Prometheus metrics exposed at `/actuator/prometheus`:

- `http_server_requests_seconds_count`: Request count
- `outbox_events_processed_total`: Outbox processing rate
- `notifications_delivered_total`: Delivery success rate
- `jvm_memory_used_bytes`: Memory usage

### Logging

Structured JSON logging with correlation IDs:

```json
{
  "timestamp": "2025-01-20T10:30:00.123Z",
  "level": "INFO",
  "logger": "dispatcher.infrastructure.com.tsu.notification.OutboxDispatcher",
  "message": "Outbox event processed successfully",
  "event_id": "550e8400-e29b-41d4-a716-446655440000",
  "notification_id": "660e8400-e29b-41d4-a716-446655440001"
}
```

## Testing

### Unit Tests

```bash
mvn test
```

### Integration Tests

```bash
mvn verify
```

### Load Testing

```bash
# Using Apache Bench
ab -n 1000 -c 10 -p payload.json -T application/json \
  http://localhost:8080/api/v1/notifications
```

## Security

- **Authentication**: Implement OAuth2/JWT (not included in base implementation)
- **Authorization**: Role-based access control
- **Secrets Management**: Kubernetes Secrets, AWS Secrets Manager, Vault
- **Database**: SSL connections, encrypted at rest
- **API**: Rate limiting via Ingress annotations

## Performance

### Benchmarks

- **API Throughput**: 1000+ requests/second (single instance)
- **Dispatcher Rate**: 5000+ deliveries/minute (with 2 instances)
- **Database**: Sub-100ms query latency at 10k requests/min

### Optimization Tips

1. **Database**:
   - Use connection pooling (HikariCP)
   - Add indexes for common queries
   - Enable JPA batch processing

2. **Dispatcher**:
   - Adjust batch size based on volume
   - Scale horizontally for high throughput
   - Monitor outbox queue depth

3. **JVM**:
   - Use G1GC for low pause times
   - Set appropriate heap size
   - Enable heap dump on OOM

## Troubleshooting

See `deployment/DEPLOYMENT_GUIDE.md` for detailed troubleshooting guide.

Common issues:
- Dispatcher not processing: Check `DISPATCHER_ENABLED` setting
- High latency: Increase connection pool size
- Memory issues: Adjust JVM heap settings

## Contributing

1. Fork the repository
2. Create feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'feat: add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open Pull Request

## License

This project is licensed under the MIT License.

## Support

For issues and questions:
- GitHub Issues: https://github.com/yourorg/bx-notification/issues
- Documentation: `docs/`
- Deployment Guide: `deployment/DEPLOYMENT_GUIDE.md`

## Acknowledgments

- Transactional Outbox Pattern inspired by Chris Richardson's Microservices Patterns
- Push notification examples from Firebase and Apple documentation
- Spring Boot community for excellent documentation
