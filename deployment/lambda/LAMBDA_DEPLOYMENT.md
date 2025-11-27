# AWS Lambda Deployment Guide

This guide explains how to deploy the Notification Service as serverless AWS Lambda functions using AWS SAM (Serverless Application Model).

## Architecture Overview

The serverless deployment consists of three Lambda functions:

```
┌────────────────────┐
│   API Gateway      │
│   (REST API)       │
└─────────┬──────────┘
          │
          ▼
┌────────────────────────────┐
│  NotificationApiFunction   │  ← Handles HTTP requests
│  (API Lambda)              │
└────────────────────────────┘
          │
          │ (writes to DB)
          ▼
┌────────────────────────────┐
│  PostgreSQL (RDS)          │
│  - Outbox table            │
└─────────┬──────────────────┘
          │
          ▼
┌────────────────────────────┐
│ OutboxDispatcherFunction   │  ← EventBridge (1 min)
│ (Scheduled Lambda)         │
└─────────┬──────────────────┘
          │
          │ (publishes to queue)
          ▼
┌────────────────────────────┐
│  SQS Queue                 │
│  (notification-events)     │
└─────────┬──────────────────┘
          │
          │ (triggers)
          ▼
┌────────────────────────────┐
│ QueueConsumerFunction      │  ← SQS trigger
│ (Consumer Lambda)          │
│ - Sends emails via SES     │
│ - Sends SMS via SNS        │
│ - Sends push via SNS       │
└────────────────────────────┘
```

## Prerequisites

### 1. Install AWS SAM CLI

```bash
# macOS
brew install aws-sam-cli

# Linux
pip install aws-sam-cli

# Verify installation
sam --version
```

### 2. Install AWS CLI

```bash
# macOS
brew install awscli

# Linux
pip install awscli

# Configure credentials
aws configure
```

### 3. Set Up Database (RDS)

Create a PostgreSQL RDS instance:

```bash
# Create RDS instance
aws rds create-db-instance \
  --db-instance-identifier notification-db \
  --db-instance-class db.t3.micro \
  --engine postgres \
  --engine-version 14.7 \
  --master-username notification \
  --master-user-password <YOUR_PASSWORD> \
  --allocated-storage 20 \
  --vpc-security-group-ids sg-xxxxx \
  --db-subnet-group-name default \
  --publicly-accessible false

# Wait for instance to be available
aws rds wait db-instance-available \
  --db-instance-identifier notification-db

# Get endpoint
aws rds describe-db-instances \
  --db-instance-identifier notification-db \
  --query 'DBInstances[0].Endpoint.Address' \
  --output text
```

Initialize schema:

```bash
# Connect to RDS
psql -h <RDS_ENDPOINT> -U notification -d notifications

# Run schema
\i db/schema.sql
```

### 4. Set Up AWS Services

```bash
# Verify SES domain (if using AWS_SES)
aws ses verify-domain-identity --domain example.com

# Set SNS SMS attributes (if using AWS_SNS)
aws sns set-sms-attributes \
  --attributes DefaultSMSType=Transactional,MonthlySpendLimit=100
```

## Build and Deploy

### Step 1: Build Application

```bash
# Build JAR with dependencies
mvn clean package

# Verify JAR was created
ls -lh target/bx-notification-1.0-aws-lambda.jar
```

### Step 2: Validate SAM Template

```bash
cd deployment/lambda

# Validate template
sam validate --lint
```

### Step 3: Build SAM Application

```bash
# Build Lambda deployment package
sam build
```

### Step 4: Deploy to AWS

#### Development Deployment

```bash
# Deploy to dev stage (with MOCK providers)
sam deploy \
  --config-env dev \
  --parameter-overrides \
    DBUrl=jdbc:postgresql://<RDS_ENDPOINT>:5432/notifications \
    DBUsername=notification \
    DBPassword=<YOUR_PASSWORD>

# Or use interactive guided deploy
sam deploy --guided --config-env dev
```

#### Production Deployment

```bash
# Deploy to production stage (with AWS providers)
sam deploy \
  --config-env prod \
  --parameter-overrides \
    Stage=prod \
    DBUrl=jdbc:postgresql://<RDS_ENDPOINT>:5432/notifications \
    DBUsername=notification \
    DBPassword=<YOUR_PASSWORD> \
    EmailProvider=AWS_SES \
    SmsProvider=AWS_SNS \
    PushProvider=AWS_SNS
```

### Step 5: Get API Endpoint

```bash
# Get API Gateway endpoint
aws cloudformation describe-stacks \
  --stack-name notification-service-prod \
  --query 'Stacks[0].Outputs[?OutputKey==`ApiEndpoint`].OutputValue' \
  --output text
```

## Testing Lambda Functions

### Test API Lambda Locally

```bash
# Start API locally
sam local start-api

# Test endpoint
curl -X POST http://localhost:3000/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "type": "TRANSACTIONAL",
    "priority": "HIGH",
    "recipientEmail": "user@example.com",
    "subject": "Test",
    "body": "Test notification",
    "channels": ["EMAIL"]
  }'
```

### Test Queue Consumer Lambda

```bash
# Create test SQS event
cat > test-event.json << 'EOF'
{
  "Records": [{
    "messageId": "test-123",
    "body": "{\"messageId\":\"msg-1\",\"messageType\":\"NOTIFICATION_CREATED\",\"payload\":{\"eventId\":\"550e8400-e29b-41d4-a716-446655440000\",\"aggregateType\":\"NOTIFICATION\",\"aggregateId\":\"660e8400-e29b-41d4-a716-446655440001\",\"eventType\":\"NOTIFICATION_CREATED\",\"payload\":{}}}"
  }]
}
EOF

# Invoke locally
sam local invoke QueueConsumerFunction -e test-event.json
```

### Test Outbox Dispatcher Lambda

```bash
# Create test scheduled event
cat > test-scheduled-event.json << 'EOF'
{
  "id": "test-event",
  "detail-type": "Scheduled Event",
  "time": "2025-01-01T00:00:00Z"
}
EOF

# Invoke locally
sam local invoke OutboxDispatcherFunction -e test-scheduled-event.json
```

## Production Deployment

### Deploy Production Stack

```bash
# Deploy with all AWS services
sam deploy \
  --config-env prod \
  --parameter-overrides \
    Stage=prod \
    DBUrl=jdbc:postgresql://prod-db.region.rds.amazonaws.com:5432/notifications \
    DBUsername=notification \
    DBPassword=$DB_PASSWORD \
    EmailProvider=AWS_SES \
    SmsProvider=AWS_SNS \
    PushProvider=AWS_SNS
```

### Test Production API

```bash
# Get API endpoint
API_URL=$(aws cloudformation describe-stacks \
  --stack-name notification-service-prod \
  --query 'Stacks[0].Outputs[?OutputKey==`ApiEndpoint`].OutputValue' \
  --output text)

# Test API
curl -X POST ${API_URL}api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "type": "TRANSACTIONAL",
    "priority": "HIGH",
    "recipientEmail": "user@example.com",
    "subject": "Production Test",
    "body": "Test from production Lambda",
    "channels": ["EMAIL"]
  }'
```

## Monitoring

### CloudWatch Logs

```bash
# View API Lambda logs
sam logs -n NotificationApiFunction --stack-name notification-service-prod --tail

# View Queue Consumer logs
sam logs -n QueueConsumerFunction --stack-name notification-service-prod --tail

# View Outbox Dispatcher logs
sam logs -n OutboxDispatcherFunction --stack-name notification-service-prod --tail
```

### CloudWatch Metrics

```bash
# View Lambda invocations
aws cloudwatch get-metric-statistics \
  --namespace AWS/Lambda \
  --metric-name Invocations \
  --dimensions Name=FunctionName,Value=notification-api-prod \
  --start-time 2025-01-01T00:00:00Z \
  --end-time 2025-01-01T23:59:59Z \
  --period 3600 \
  --statistics Sum

# View Lambda errors
aws cloudwatch get-metric-statistics \
  --namespace AWS/Lambda \
  --metric-name Errors \
  --dimensions Name=FunctionName,Value=notification-queue-consumer-prod \
  --start-time 2025-01-01T00:00:00Z \
  --end-time 2025-01-01T23:59:59Z \
  --period 3600 \
  --statistics Sum

# View SQS queue depth
aws cloudwatch get-metric-statistics \
  --namespace AWS/SQS \
  --metric-name ApproximateNumberOfMessagesVisible \
  --dimensions Name=QueueName,Value=notification-events-prod \
  --start-time 2025-01-01T00:00:00Z \
  --end-time 2025-01-01T23:59:59Z \
  --period 300 \
  --statistics Average
```

### CloudWatch Alarms

The SAM template includes pre-configured alarms:

- **Queue Depth Alarm**: Triggers when queue has >1000 messages
- **DLQ Alarm**: Triggers when any message appears in Dead Letter Queue

View alarms:

```bash
aws cloudwatch describe-alarms \
  --alarm-name-prefix notification
```

## Cost Optimization

### Lambda Pricing

- **API Lambda**: ~50ms execution, 512 MB
  - 1 million requests/month = ~$0.20
- **Queue Consumer**: ~500ms execution, 1024 MB
  - 1 million invocations = ~$2.08
- **Outbox Dispatcher**: ~5s execution, 1024 MB (1440 invocations/day)
  - $0.09/month

### Total Estimated Cost

For 1 million notifications/month:
- Lambda: ~$3
- API Gateway: ~$3.50
- SQS: ~$0.40
- RDS (db.t3.micro): ~$15
- SES: ~$100
- SNS SMS: ~$645

**Total: ~$767/month** (mostly SMS costs)

### Cost Reduction Tips

1. **Use reserved concurrency** for predictable workloads
2. **Increase batch size** for SQS consumer (10-10000)
3. **Use provisioned concurrency** for API if cold starts are an issue
4. **Optimize memory allocation** based on CloudWatch metrics
5. **Set up budget alerts**

```bash
aws budgets create-budget \
  --account-id $(aws sts get-caller-identity --query Account --output text) \
  --budget file://budget.json
```

## Troubleshooting

### Lambda Cold Starts

If API latency is high (>1s):

```bash
# Enable provisioned concurrency
aws lambda put-provisioned-concurrency-config \
  --function-name notification-api-prod \
  --provisioned-concurrent-executions 2
```

### Queue Processing Slow

If queue depth increases:

```bash
# Increase reserved concurrent executions
aws lambda put-function-concurrency \
  --function-name notification-queue-consumer-prod \
  --reserved-concurrent-executions 20
```

### Database Connection Issues

If Lambdas can't connect to RDS:

1. Verify VPC configuration
2. Check security groups
3. Enable RDS Proxy for connection pooling

```bash
# Create RDS Proxy
aws rds create-db-proxy \
  --db-proxy-name notification-proxy \
  --engine-family POSTGRESQL \
  --auth '{...}' \
  --role-arn arn:aws:iam::123456789012:role/RDSProxyRole \
  --vpc-subnet-ids subnet-1 subnet-2
```

### Memory Issues

If Lambdas run out of memory:

```bash
# Update memory allocation
aws lambda update-function-configuration \
  --function-name notification-queue-consumer-prod \
  --memory-size 2048
```

## CI/CD Pipeline

### GitHub Actions Example

```yaml
# .github/workflows/deploy-lambda.yml
name: Deploy to Lambda

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Build with Maven
        run: mvn clean package

      - name: Set up AWS SAM
        uses: aws-actions/setup-sam@v2

      - name: SAM Build
        run: |
          cd deployment/lambda
          sam build

      - name: SAM Deploy
        run: |
          cd deployment/lambda
          sam deploy --no-confirm-changeset --no-fail-on-empty-changeset \
            --config-env prod \
            --parameter-overrides \
              DBUrl=${{ secrets.DB_URL }} \
              DBUsername=${{ secrets.DB_USERNAME }} \
              DBPassword=${{ secrets.DB_PASSWORD }}
        env:
          AWS_ACCESS_KEY_ID: ${{ secrets.AWS_ACCESS_KEY_ID }}
          AWS_SECRET_ACCESS_KEY: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          AWS_REGION: us-east-1
```

## Cleanup

To delete the entire stack:

```bash
# Delete SAM stack
sam delete --stack-name notification-service-prod

# Delete RDS instance
aws rds delete-db-instance \
  --db-instance-identifier notification-db \
  --skip-final-snapshot
```

## Best Practices

1. **Use VPC** for RDS connectivity and security
2. **Enable X-Ray tracing** for debugging
3. **Set up CloudWatch dashboards** for monitoring
4. **Use AWS Secrets Manager** for database credentials
5. **Implement API throttling** to prevent abuse
6. **Enable versioning** for Lambda functions
7. **Use dead letter queues** for failed messages
8. **Set up budget alerts** to monitor costs
9. **Implement graceful degradation** (fallback to MOCK on AWS failures)
10. **Use layers** for common dependencies to reduce package size

## Additional Resources

- [AWS SAM Documentation](https://docs.aws.amazon.com/serverless-application-model/)
- [Lambda Best Practices](https://docs.aws.amazon.com/lambda/latest/dg/best-practices.html)
- [Lambda Pricing](https://aws.amazon.com/lambda/pricing/)
- [RDS Proxy for Lambda](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-proxy.html)
