#!/bin/bash

# AWS Lambda Deployment Script
# Usage: ./deploy.sh [dev|staging|prod]

set -e

STAGE=${1:-dev}
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/../.."

echo "==================================="
echo "Deploying Notification Service"
echo "Stage: $STAGE"
echo "==================================="

# Validate stage
if [[ ! "$STAGE" =~ ^(dev|staging|prod)$ ]]; then
    echo "Error: Invalid stage. Must be dev, staging, or prod"
    exit 1
fi

# Check prerequisites
echo "Checking prerequisites..."

if ! command -v sam &> /dev/null; then
    echo "Error: AWS SAM CLI is not installed"
    echo "Install it with: pip install aws-sam-cli"
    exit 1
fi

if ! command -v aws &> /dev/null; then
    echo "Error: AWS CLI is not installed"
    echo "Install it with: pip install awscli"
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed"
    exit 1
fi

# Build application
echo ""
echo "Building application..."
cd "$PROJECT_ROOT"
mvn clean package -DskipTests

# Verify JAR exists
JAR_FILE="$PROJECT_ROOT/target/bx-notification-1.0-aws-lambda.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found at $JAR_FILE"
    exit 1
fi

echo "JAR size: $(du -h $JAR_FILE | cut -f1)"

# Validate SAM template
echo ""
echo "Validating SAM template..."
cd "$SCRIPT_DIR"
sam validate --lint

# Build SAM application
echo ""
echo "Building SAM application..."
sam build

# Deploy
echo ""
echo "Deploying to AWS..."

if [ "$STAGE" == "dev" ]; then
    # Dev deployment with prompts disabled
    sam deploy --config-env dev --no-confirm-changeset
elif [ "$STAGE" == "staging" ]; then
    # Staging deployment with confirmation
    sam deploy --config-env staging
elif [ "$STAGE" == "prod" ]; then
    # Production deployment with confirmation
    echo "WARNING: Deploying to PRODUCTION"
    read -p "Are you sure you want to deploy to production? (yes/no): " confirm
    if [ "$confirm" != "yes" ]; then
        echo "Deployment cancelled"
        exit 0
    fi
    sam deploy --config-env prod
fi

# Get outputs
echo ""
echo "==================================="
echo "Deployment completed successfully!"
echo "==================================="
echo ""

STACK_NAME="notification-service-$STAGE"

# Get API endpoint
API_ENDPOINT=$(aws cloudformation describe-stacks \
    --stack-name $STACK_NAME \
    --query 'Stacks[0].Outputs[?OutputKey==`ApiEndpoint`].OutputValue' \
    --output text)

# Get Queue URL
QUEUE_URL=$(aws cloudformation describe-stacks \
    --stack-name $STACK_NAME \
    --query 'Stacks[0].Outputs[?OutputKey==`NotificationEventsQueueUrl`].OutputValue' \
    --output text)

echo "API Endpoint: $API_ENDPOINT"
echo "Queue URL: $QUEUE_URL"
echo ""

# Test API endpoint
echo "Testing API endpoint..."
curl -X POST "${API_ENDPOINT}api/v1/notifications" \
    -H "Content-Type: application/json" \
    -d '{
        "type": "TRANSACTIONAL",
        "priority": "HIGH",
        "recipientEmail": "test@example.com",
        "subject": "Test Notification",
        "body": "This is a test notification from Lambda",
        "channels": ["EMAIL"]
    }' \
    -w "\n\nHTTP Status: %{http_code}\n" \
    -s || echo "API test failed (this is expected if email is not verified)"

echo ""
echo "Next steps:"
echo "1. Verify SES email addresses: aws ses verify-email-identity --email-address you@example.com"
echo "2. Monitor CloudWatch logs: sam logs -n NotificationApiFunction --stack-name $STACK_NAME --tail"
echo "3. View CloudWatch metrics in AWS Console"
echo ""
