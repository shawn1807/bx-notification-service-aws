# AWS Lambda Deployment - Quick Start

This directory contains AWS Lambda deployment configurations using AWS SAM (Serverless Application Model).

## Quick Deploy

```bash
# 1. Build application
cd /path/to/bx-notification
mvn clean package

# 2. Deploy to AWS
cd deployment/lambda
./deploy.sh dev    # Deploy to dev
./deploy.sh prod   # Deploy to production
```

## Files

- `template.yaml` - SAM CloudFormation template defining all Lambda functions and resources
- `samconfig.toml` - SAM CLI configuration for different stages (dev/staging/prod)
- `deploy.sh` - Automated deployment script
- `LAMBDA_DEPLOYMENT.md` - Complete deployment guide
- `.gitignore` - SAM build artifacts to ignore

## Architecture

The deployment creates three Lambda functions:

1. **NotificationApiFunction** - Handles HTTP requests from API Gateway
   - Trigger: API Gateway (POST /api/v1/notifications, GET /api/v1/notifications/{id})
   - Memory: 512 MB
   - Timeout: 30s

2. **QueueConsumerFunction** - Processes notification events from SQS
   - Trigger: SQS Queue (notification-events)
   - Memory: 1024 MB
   - Timeout: 300s (5 min)
   - Batch: 10 messages

3. **OutboxDispatcherFunction** - Polls database and publishes to queue
   - Trigger: EventBridge (scheduled every 1 minute)
   - Memory: 1024 MB
   - Timeout: 300s (5 min)

## Prerequisites

```bash
# Install AWS SAM CLI
brew install aws-sam-cli  # macOS
pip install aws-sam-cli   # Linux/Windows

# Install AWS CLI
brew install awscli       # macOS
pip install awscli        # Linux/Windows

# Configure AWS credentials
aws configure
```

## Manual Deployment Steps

### 1. Build

```bash
# Build Java application
mvn clean package

# Validate SAM template
sam validate --lint

# Build SAM application
sam build
```

### 2. Deploy

```bash
# Deploy interactively (first time)
sam deploy --guided

# Deploy with saved configuration
sam deploy --config-env dev
sam deploy --config-env prod
```

### 3. Test

```bash
# Get API endpoint
API_URL=$(aws cloudformation describe-stacks \
  --stack-name notification-service-dev \
  --query 'Stacks[0].Outputs[?OutputKey==`ApiEndpoint`].OutputValue' \
  --output text)

# Test API
curl -X POST ${API_URL}api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "type": "TRANSACTIONAL",
    "priority": "HIGH",
    "recipientEmail": "test@example.com",
    "subject": "Test",
    "body": "Test notification",
    "channels": ["EMAIL"]
  }'
```

## Local Testing

```bash
# Start API locally
sam local start-api

# Invoke function locally
sam local invoke NotificationApiFunction -e test-event.json

# View logs
sam logs -n NotificationApiFunction --tail
```

## Monitoring

```bash
# View CloudWatch logs
sam logs -n NotificationApiFunction --stack-name notification-service-dev --tail

# View metrics in AWS Console
https://console.aws.amazon.com/cloudwatch/
```

## Configuration

### Environment Variables

Set via SAM template parameters:

- `DBUrl` - Database connection URL
- `DBUsername` - Database username
- `DBPassword` - Database password
- `EmailProvider` - Email provider (MOCK, AWS_SES)
- `SmsProvider` - SMS provider (MOCK, AWS_SNS)
- `PushProvider` - Push provider (DIRECT, AWS_SNS)

### Stage-specific Configuration

Edit `samconfig.toml` to customize per-stage settings.

## Cleanup

```bash
# Delete entire stack
sam delete --stack-name notification-service-dev
```

## Troubleshooting

### Cold Start Issues

Enable provisioned concurrency:

```bash
aws lambda put-provisioned-concurrency-config \
  --function-name notification-api-dev \
  --provisioned-concurrent-executions 2
```

### Database Connection Timeout

Increase timeout or use RDS Proxy:

```bash
aws lambda update-function-configuration \
  --function-name notification-api-dev \
  --timeout 60
```

### High Costs

- Reduce memory allocation
- Decrease polling frequency
- Use reserved concurrency limits

## More Information

See [LAMBDA_DEPLOYMENT.md](LAMBDA_DEPLOYMENT.md) for complete documentation.
