# AWS Integration Guide

This notification service provides full AWS integration for all notification channels using AWS SES (Email), AWS SNS (SMS and Mobile Push), and AWS SQS (Queue).

## Overview

### AWS Services Used

1. **AWS SES (Simple Email Service)** - Email notifications
2. **AWS SNS (Simple Notification Service)** - SMS and mobile push notifications
3. **AWS SQS (Simple Queue Service)** - Message queue for outbox pattern

### Benefits of AWS Integration

- **Reliability**: AWS managed services with high availability SLAs
- **Scalability**: Auto-scales to handle millions of messages
- **Cost-Effective**: Pay-per-use pricing with no upfront costs
- **Monitoring**: Built-in CloudWatch metrics and logging
- **Security**: IAM-based access control and encryption
- **Compliance**: GDPR, HIPAA, and other compliance certifications

## Architecture

```
┌─────────────────┐
│   Application   │
└────────┬────────┘
         │
         ├─────────► AWS SES (Email)
         │
         ├─────────► AWS SNS (SMS)
         │
         ├─────────► AWS SNS (Mobile Push)
         │
         └─────────► AWS SQS (Queue)
                        │
                        └─────► Process Events
```

## Setup Guide

### 1. AWS SES (Email) Setup

#### Step 1: Verify Email Domain

```bash
# Verify domain in SES
aws ses verify-domain-identity --domain example.com

# Add DNS records returned by the command to your domain
```

#### Step 2: Create Configuration Set (Optional - for tracking)

```bash
# Create configuration set for tracking opens/clicks
aws ses create-configuration-set \
  --configuration-set Name=notification-tracking

# Add event destination for CloudWatch
aws ses put-configuration-set-event-destination \
  --configuration-set-name notification-tracking \
  --event-destination '{
    "Name": "CloudWatch",
    "Enabled": true,
    "MatchingEventTypes": ["send", "reject", "bounce", "complaint", "delivery", "open", "click"],
    "CloudWatchDestination": {
      "DimensionConfigurations": [{
        "DimensionName": "NotificationType",
        "DimensionValueSource": "messageTag",
        "DefaultDimensionValue": "notification"
      }]
    }
  }'
```

#### Step 3: Move Out of Sandbox

By default, SES is in sandbox mode (can only send to verified emails). Request production access:

```bash
# Request production access via AWS Support
# Or use AWS Console: SES > Account dashboard > Request production access
```

#### Step 4: Configure Application

```yaml
# application.yaml
notification:
  channels:
    email:
      provider: AWS_SES
      from-address: noreply@example.com
      from-name: Your App Name
      configuration-set: notification-tracking  # Optional

aws:
  region: us-east-1
```

#### Environment Variables

```bash
export EMAIL_PROVIDER=AWS_SES
export EMAIL_FROM=noreply@example.com
export EMAIL_FROM_NAME="Your App"
export EMAIL_CONFIG_SET=notification-tracking
```

### 2. AWS SNS (SMS) Setup

#### Step 1: Configure SMS Settings

```bash
# Set default SMS type (Transactional has higher priority)
aws sns set-sms-attributes \
  --attributes DefaultSMSType=Transactional

# Set monthly spending limit (in USD)
aws sns set-sms-attributes \
  --attributes MonthlySpendLimit=100.00

# Set default sender ID (not supported in all countries, e.g., USA)
aws sns set-sms-attributes \
  --attributes DefaultSenderID=YourApp
```

#### Step 2: Request Quota Increase

Default limit is 1 SMS/second. Request increase if needed:

```bash
# Request via AWS Support or Service Quotas console
# Service Quotas > Amazon SNS > Account-level SMS spending quota
```

#### Step 3: Configure Application

```yaml
# application.yaml
notification:
  channels:
    sms:
      provider: AWS_SNS
      sender-id: YourApp  # Not supported in USA
      sms-type: Transactional  # or Promotional
      max-price: 0.50  # Max price per SMS in USD

aws:
  region: us-east-1
```

#### Environment Variables

```bash
export SMS_PROVIDER=AWS_SNS
export SMS_SENDER_ID=YourApp
export SMS_TYPE=Transactional
export SMS_MAX_PRICE=0.50
```

### 3. AWS SNS (Mobile Push) Setup

#### Step 1: Create Platform Applications

**For FCM (Android):**

```bash
# Create FCM platform application
aws sns create-platform-application \
  --name YourApp-FCM \
  --platform GCM \
  --attributes PlatformCredential=<YOUR_FCM_SERVER_KEY>
```

**For APNs (iOS):**

```bash
# Create APNs platform application (token-based)
aws sns create-platform-application \
  --name YourApp-APNs \
  --platform APNS \
  --attributes '{
    "PlatformCredential": "<P8_CERTIFICATE_CONTENT>",
    "PlatformPrincipal": "<TEAM_ID>",
    "ApplePlatformTeamID": "<TEAM_ID>",
    "ApplePlatformBundleID": "com.yourapp"
  }'

# For production APNs
aws sns create-platform-application \
  --name YourApp-APNs-Production \
  --platform APNS \
  --attributes ...
```

#### Step 2: Configure Application

```yaml
# application.yaml
notification:
  channels:
    push:
      provider: AWS_SNS
      fcm:
        platform-application-arn: arn:aws:sns:us-east-1:123456789012:app/GCM/YourApp-FCM
      apns:
        platform-application-arn: arn:aws:sns:us-east-1:123456789012:app/APNS/YourApp-APNs

aws:
  region: us-east-1
```

#### Environment Variables

```bash
export PUSH_PROVIDER=AWS_SNS
export FCM_PLATFORM_ARN=arn:aws:sns:us-east-1:123456789012:app/GCM/YourApp-FCM
export APNS_PLATFORM_ARN=arn:aws:sns:us-east-1:123456789012:app/APNS/YourApp-APNs
```

## IAM Permissions

### Required Permissions

Create an IAM policy with the following permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "SESPermissions",
      "Effect": "Allow",
      "Action": [
        "ses:SendEmail",
        "ses:SendTemplatedEmail",
        "ses:SendRawEmail",
        "ses:GetSendQuota",
        "ses:GetSendStatistics"
      ],
      "Resource": "*"
    },
    {
      "Sid": "SNSSMSPermissions",
      "Effect": "Allow",
      "Action": [
        "sns:Publish",
        "sns:GetSMSAttributes",
        "sns:SetSMSAttributes"
      ],
      "Resource": "*"
    },
    {
      "Sid": "SNSPushPermissions",
      "Effect": "Allow",
      "Action": [
        "sns:CreatePlatformEndpoint",
        "sns:DeleteEndpoint",
        "sns:GetEndpointAttributes",
        "sns:SetEndpointAttributes",
        "sns:Publish",
        "sns:ListPlatformApplications"
      ],
      "Resource": [
        "arn:aws:sns:*:*:app/GCM/*",
        "arn:aws:sns:*:*:app/APNS/*",
        "arn:aws:sns:*:*:endpoint/GCM/*",
        "arn:aws:sns:*:*:endpoint/APNS/*"
      ]
    },
    {
      "Sid": "SQSPermissions",
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

### Attach Policy to IAM Role

```bash
# Create IAM role for ECS/EC2/Lambda
aws iam create-role \
  --role-name NotificationServiceRole \
  --assume-role-policy-document file://trust-policy.json

# Attach the policy
aws iam put-role-policy \
  --role-name NotificationServiceRole \
  --policy-name NotificationServicePolicy \
  --policy-document file://notification-policy.json
```

## Cost Estimation

### AWS SES Pricing (as of 2025)

- **Email**: $0.10 per 1,000 emails
- **Free Tier**: 62,000 emails/month (when sending from EC2)

**Example**: 1 million emails/month = ~$100/month

### AWS SNS Pricing (as of 2025)

- **SMS (USA)**: ~$0.00645 per message
- **Mobile Push**: $0.50 per 1 million notifications
- **Free Tier**: 1 million mobile push/month

**Example**:
- 100,000 SMS/month = ~$645/month
- 1 million push/month = $0.50/month

### AWS SQS Pricing (as of 2025)

- **Standard Queue**: $0.40 per 1 million requests
- **Free Tier**: 1 million requests/month

**Example**: 10 million messages/month = ~$3.60/month

### Total Estimated Cost

For a typical application sending:
- 500,000 emails/month
- 50,000 SMS/month
- 2 million push notifications/month
- 5 million queue messages/month

**Monthly Cost**: ~$400-450

## Monitoring and Logging

### CloudWatch Metrics

#### SES Metrics

```bash
# View SES send rate
aws cloudwatch get-metric-statistics \
  --namespace AWS/SES \
  --metric-name Send \
  --start-time 2025-01-01T00:00:00Z \
  --end-time 2025-01-31T23:59:59Z \
  --period 3600 \
  --statistics Sum

# View bounce rate
aws cloudwatch get-metric-statistics \
  --namespace AWS/SES \
  --metric-name Reputation.BounceRate \
  --start-time 2025-01-01T00:00:00Z \
  --end-time 2025-01-31T23:59:59Z \
  --period 86400 \
  --statistics Average
```

#### SNS Metrics

```bash
# View SMS success rate
aws cloudwatch get-metric-statistics \
  --namespace AWS/SNS \
  --metric-name NumberOfMessagesPublished \
  --start-time 2025-01-01T00:00:00Z \
  --end-time 2025-01-31T23:59:59Z \
  --period 3600 \
  --statistics Sum

# View SMS delivery failures
aws cloudwatch get-metric-statistics \
  --namespace AWS/SNS \
  --metric-name NumberOfNotificationsFailed \
  --start-time 2025-01-01T00:00:00Z \
  --end-time 2025-01-31T23:59:59Z \
  --period 3600 \
  --statistics Sum
```

### Application Logging

The application logs include AWS-specific information:

```java
log.info("Email sent successfully via AWS SES: messageId={}, recipient={}",
    messageId, recipient);

log.info("SMS sent successfully via AWS SNS: messageId={}, phoneNumber={}",
    messageId, phoneNumber);
```

## Testing Locally with LocalStack

LocalStack provides local AWS service emulation for development.

### Step 1: Start LocalStack

```bash
docker run -d \
  --name localstack \
  -p 4566:4566 \
  -e SERVICES=ses,sns,sqs \
  -e DEBUG=1 \
  localstack/localstack
```

### Step 2: Configure Application

```yaml
# application-dev.yaml
aws:
  region: us-east-1
  ses:
    endpoint: http://localhost:4566
  sns:
    endpoint: http://localhost:4566
  sqs:
    endpoint: http://localhost:4566

notification:
  channels:
    email:
      provider: AWS_SES  # Test with LocalStack
    sms:
      provider: AWS_SNS  # Test with LocalStack
```

### Step 3: Initialize LocalStack Services

```bash
# Configure AWS CLI to use LocalStack
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

# Verify domain in LocalStack SES
aws --endpoint-url=http://localhost:4566 ses verify-email-identity \
  --email-address noreply@example.com

# Create SQS queue
aws --endpoint-url=http://localhost:4566 sqs create-queue \
  --queue-name notification-events
```

### Step 4: Test Application

```bash
# Run application with dev profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=development
```

## Troubleshooting

### Email Issues

**Problem**: Emails not sending (sandbox mode)
```bash
# Solution: Verify recipient email in sandbox
aws ses verify-email-identity --email-address recipient@example.com

# Or request production access
```

**Problem**: High bounce rate
```bash
# Check bounce notifications
aws ses get-send-statistics

# View suppression list
aws sesv2 list-suppressed-destinations
```

### SMS Issues

**Problem**: SMS not delivered
```bash
# Check SMS delivery status
aws sns get-sms-attributes

# Verify phone number format (must be E.164)
# Correct: +12345678900
# Incorrect: (234) 567-8900
```

**Problem**: SMS blocked by spending limit
```bash
# Increase monthly spending limit
aws sns set-sms-attributes \
  --attributes MonthlySpendLimit=500.00
```

### Push Notification Issues

**Problem**: Endpoint creation fails
```bash
# Verify platform application ARN is correct
aws sns list-platform-applications

# Check platform credentials are valid
```

**Problem**: Push notification not delivered
```bash
# Check endpoint status
aws sns get-endpoint-attributes \
  --endpoint-arn <ENDPOINT_ARN>

# If endpoint disabled, delete and recreate
aws sns delete-endpoint --endpoint-arn <ENDPOINT_ARN>
```

## Security Best Practices

### 1. Use IAM Roles (Not Access Keys)

```yaml
# Don't use hardcoded credentials
# ❌ BAD
aws:
  access-key-id: AKIAIOSFODNN7EXAMPLE
  secret-access-key: wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY

# ✅ GOOD - Use IAM role attached to ECS task/EC2 instance
aws:
  region: us-east-1  # Role provides credentials automatically
```

### 2. Enable Encryption

```bash
# Enable SES TLS
# SES uses TLS 1.2+ by default

# Enable SQS encryption
aws sqs set-queue-attributes \
  --queue-url <QUEUE_URL> \
  --attributes '{"KmsMasterKeyId": "alias/aws/sqs"}'
```

### 3. Set Spending Limits

```bash
# Set SNS SMS spending limit
aws sns set-sms-attributes \
  --attributes MonthlySpendLimit=100.00

# Set up billing alerts
aws cloudwatch put-metric-alarm \
  --alarm-name notification-cost-alert \
  --alarm-description "Alert when notification costs exceed $500" \
  --metric-name EstimatedCharges \
  --namespace AWS/Billing \
  --statistic Maximum \
  --period 21600 \
  --evaluation-periods 1 \
  --threshold 500 \
  --comparison-operator GreaterThanThreshold
```

### 4. Use VPC Endpoints

```bash
# Create VPC endpoints for SES, SNS, SQS (avoid internet gateway)
aws ec2 create-vpc-endpoint \
  --vpc-id vpc-12345678 \
  --service-name com.amazonaws.us-east-1.email-smtp \
  --route-table-ids rtb-12345678
```

## Migration Guide

### From MOCK to AWS

1. **Update configuration**:
   ```yaml
   notification:
     channels:
       email:
         provider: AWS_SES  # Changed from MOCK
       sms:
         provider: AWS_SNS  # Changed from MOCK
   ```

2. **Set environment variables**:
   ```bash
   export EMAIL_PROVIDER=AWS_SES
   export SMS_PROVIDER=AWS_SNS
   ```

3. **Deploy and verify**:
   ```bash
   # Check health endpoint
   curl http://localhost:8080/actuator/health

   # Send test notification
   curl -X POST http://localhost:8080/api/v1/notifications \
     -H "Content-Type: application/json" \
     -d '{...}'
   ```

4. **Monitor metrics**:
   - Check CloudWatch for delivery rates
   - Review application logs for errors
   - Monitor costs in AWS Billing

## Additional Resources

- [AWS SES Documentation](https://docs.aws.amazon.com/ses/)
- [AWS SNS Documentation](https://docs.aws.amazon.com/sns/)
- [AWS SQS Documentation](https://docs.aws.amazon.com/sqs/)
- [LocalStack Documentation](https://docs.localstack.cloud/)
- [Queue Architecture Guide](QUEUE_ARCHITECTURE.md)
