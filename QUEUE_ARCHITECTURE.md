# Queue-Based Architecture for Outbox Pattern

## Overview

This notification service uses a **queue-based architecture** to separate concerns between the outbox dispatcher and event handlers, providing better scalability, reliability, and maintainability.

## Architecture Components

### 1. Producer Side: OutboxDispatcher
**Location**: `com.tsu.notification.infrastructure.dispatcher.OutboxDispatcher`

**Responsibilities**:
- Polls the outbox table for pending events (using `FOR UPDATE SKIP LOCKED`)
- Publishes events to SQS queue via `QueuePublisher`
- Marks events as processed once successfully published

**Flow**:
```
Database (Outbox Table) → OutboxDispatcher → QueuePublisher → SQS Queue
```

### 2. Consumer Side: NotificationEventConsumer
**Location**: `com.tsu.notification.infrastructure.queue.NotificationEventConsumer`

**Responsibilities**:
- Polls messages from SQS queue (using long polling)
- Routes events to `NotificationEventHandler`
- Deletes messages from queue after successful processing

**Flow**:
```
SQS Queue → NotificationEventConsumer → NotificationEventHandler → Channel Dispatchers
```

## Benefits of Separation

### 1. **Decoupling**
- Outbox dispatcher only cares about publishing to queue
- Event handlers only care about processing events
- Changes to one don't affect the other

### 2. **Independent Scaling**
- Can scale dispatcher and consumers independently
- Add more consumers if event processing is slow
- Add more dispatchers if outbox polling is slow

### 3. **Reliability**
- Queue provides buffering during traffic spikes
- Built-in retry mechanism via SQS
- Dead Letter Queue (DLQ) for failed messages
- Messages won't be lost if consumers are temporarily down

### 4. **Observability**
- Monitor queue depth to detect processing issues
- Track message age to detect stuck events
- Separate metrics for producer vs consumer

### 5. **Flexibility**
- Can add multiple consumers for different event types
- Can implement priority queues
- Can add filtering/routing at queue level

## Configuration

### AWS SQS Setup

```yaml
# AWS credentials and region
aws:
  region: us-east-1
  sqs:
    endpoint: null  # Use default SQS endpoint

# Queue configuration
queue:
  provider: sqs
  consumer:
    enabled: true
  notification-events:
    queue-url: https://sqs.us-east-1.amazonaws.com/123456789012/notification-events
    max-messages: 10
    wait-time-seconds: 20
    visibility-timeout: 30
```

### Local Development with LocalStack

```yaml
aws:
  region: us-east-1
  sqs:
    endpoint: http://localhost:4566  # LocalStack endpoint

queue:
  notification-events:
    queue-url: http://localhost:4566/000000000000/notification-events
```

### Environment Variables

```bash
# AWS Configuration
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key

# Queue Configuration
export QUEUE_PROVIDER=sqs
export QUEUE_CONSUMER_ENABLED=true
export NOTIFICATION_EVENTS_QUEUE_URL=https://sqs.us-east-1.amazonaws.com/123456789012/notification-events

# For LocalStack
export AWS_SQS_ENDPOINT=http://localhost:4566
```

## Message Flow

### End-to-End Flow

1. **Business Logic** creates a notification
   ```java
   notificationService.createNotification(request);
   ```

2. **OutboxPublisher** writes event to outbox table (same transaction)
   ```java
   outboxPublisher.publishNotificationCreated(notificationId, data);
   ```

3. **OutboxDispatcher** polls outbox table and publishes to SQS
   ```java
   queuePublisher.publishOutboxEvent(event);
   ```

4. **SQS Queue** stores the message reliably

5. **NotificationEventConsumer** polls and receives message from SQS
   ```java
   receiveMessages() → processMessage()
   ```

6. **NotificationEventHandler** routes to appropriate channel dispatcher
   ```java
   notificationEventHandler.handle(event);
   ```

7. **Channel Dispatcher** sends the notification (Email, SMS, Push, In-App)
   ```java
   emailChannelDispatcher.dispatch(delivery);
   ```

## Message Format

### Queue Message Structure

```json
{
  "messageId": "uuid",
  "messageType": "NOTIFICATION_CREATED",
  "timestamp": 1234567890,
  "payload": {
    "eventId": "uuid",
    "aggregateType": "NOTIFICATION",
    "aggregateId": "uuid",
    "eventType": "NOTIFICATION_CREATED",
    "payload": {
      "notificationId": "uuid",
      "userId": "user-123",
      "channels": ["EMAIL", "PUSH"]
    },
    "partitionKey": "notification-uuid"
  },
  "attributes": {
    "eventType": "NOTIFICATION_CREATED",
    "aggregateType": "NOTIFICATION",
    "aggregateId": "uuid"
  }
}
```

## Error Handling

### Retry Strategy

1. **Outbox Dispatcher Failures**
   - Failed events marked as FAILED in outbox table
   - Stuck events reset after 1 hour
   - Manual retry possible via outbox table

2. **Queue Consumer Failures**
   - Message visibility timeout: 30 seconds
   - SQS automatically retries failed messages
   - Configure DLQ for messages that fail repeatedly

### Dead Letter Queue (DLQ)

Recommended setup:
```bash
# Create DLQ
aws sqs create-queue --queue-name notification-events-dlq

# Configure main queue with DLQ
aws sqs set-queue-attributes \
  --queue-url <main-queue-url> \
  --attributes '{
    "RedrivePolicy": "{
      \"maxReceiveCount\": \"3\",
      \"deadLetterTargetArn\": \"<dlq-arn>\"
    }"
  }'
```

## Monitoring

### Key Metrics

- **Queue Depth**: Number of messages in queue
- **Message Age**: How long messages wait in queue
- **Consumer Lag**: Difference between producer and consumer rates
- **Processing Time**: Time to process each message
- **Error Rate**: Failed message percentage

### CloudWatch Metrics

```bash
# Queue depth
aws cloudwatch get-metric-statistics \
  --namespace AWS/SQS \
  --metric-name ApproximateNumberOfMessagesVisible \
  --dimensions Name=QueueName,Value=notification-events

# Message age
aws cloudwatch get-metric-statistics \
  --namespace AWS/SQS \
  --metric-name ApproximateAgeOfOldestMessage \
  --dimensions Name=QueueName,Value=notification-events
```

## Testing Locally

### 1. Start LocalStack

```bash
docker run -d --name localstack \
  -p 4566:4566 \
  -e SERVICES=sqs \
  localstack/localstack
```

### 2. Create SQS Queue

```bash
aws --endpoint-url=http://localhost:4566 sqs create-queue \
  --queue-name notification-events
```

### 3. Run Application

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=development
```

## Troubleshooting

### Messages Not Being Consumed

1. Check if consumer is enabled: `queue.consumer.enabled=true`
2. Verify queue URL is correct
3. Check AWS credentials
4. Review consumer logs for errors

### Messages Stuck in Queue

1. Check visibility timeout (should be > processing time)
2. Review consumer error logs
3. Check DLQ for failed messages
4. Verify NotificationEventHandler is working

### Outbox Events Not Publishing

1. Check OutboxDispatcher logs
2. Verify QueuePublisher is configured
3. Test SQS connectivity
4. Check AWS permissions

## Migration Notes

### Switching from Direct Call to Queue

**Before** (Direct call):
```java
OutboxDispatcher → NotificationEventHandler → ChannelDispatcher
```

**After** (Queue-based):
```java
OutboxDispatcher → Queue → NotificationEventConsumer → NotificationEventHandler → ChannelDispatcher
```

### Rolling Out

1. Deploy with queue consumer disabled
2. Create SQS queue and configure
3. Enable queue consumer
4. Monitor queue depth and processing
5. Verify notifications are being sent

### Rolling Back

1. Disable queue consumer: `queue.consumer.enabled=false`
2. Revert OutboxDispatcher to call NotificationEventHandler directly
3. Deploy previous version

## Performance Considerations

### Throughput

- **Long Polling**: Reduces empty receives (saves costs)
- **Batch Processing**: Receive up to 10 messages per request
- **Parallel Consumers**: Run multiple consumer instances
- **Visibility Timeout**: Set based on average processing time

### Cost Optimization

- Use long polling (reduce API calls)
- Batch message receives
- Set appropriate message retention
- Monitor and tune consumer instances

## Security

### IAM Permissions

Required permissions for the application:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "sqs:SendMessage",
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:GetQueueAttributes"
      ],
      "Resource": "arn:aws:sqs:*:*:notification-events"
    }
  ]
}
```

### Encryption

Enable server-side encryption:
```bash
aws sqs set-queue-attributes \
  --queue-url <queue-url> \
  --attributes '{"KmsMasterKeyId": "alias/aws/sqs"}'
```
