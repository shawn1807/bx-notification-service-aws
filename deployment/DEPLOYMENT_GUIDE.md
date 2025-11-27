# Notification System Deployment Guide

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Local Development](#local-development)
4. [Docker Deployment](#docker-deployment)
5. [Kubernetes Deployment](#kubernetes-deployment)
6. [Scaling](#scaling)
7. [Monitoring](#monitoring)
8. [Troubleshooting](#troubleshooting)

## Overview

The Notification System consists of two main components:

1. **API Service**: Handles REST API requests and WebSocket connections
2. **Dispatcher Service**: Polls outbox events and dispatches notifications

Both components can scale horizontally for high availability and performance.

## Prerequisites

### Local Development

- Java 17 or higher
- Maven 3.9+
- PostgreSQL 14+
- Docker & Docker Compose (optional)

### Kubernetes Deployment

- Kubernetes cluster 1.24+
- kubectl configured
- Helm 3+ (optional)
- Container registry access

## Local Development

### 1. Database Setup

```bash
# Start PostgreSQL
docker run -d \
  --name notification-postgres \
  -e POSTGRES_DB=notifications \
  -e POSTGRES_USER=notification \
  -e POSTGRES_PASSWORD=password \
  -p 5432:5432 \
  postgres:14-alpine

# Initialize schema
psql -h localhost -U notification -d notifications < db/schema.sql
```

### 2. Application Configuration

Create `application-dev.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/notifications
    username: notification
    password: password

notification:
  dispatcher:
    enabled: true
    batch-size: 100
    poll-interval: 5000

logging:
  level:
    com.bx.notification: DEBUG
```

### 3. Run Application

```bash
# Build
mvn clean package

# Run API (dispatcher disabled)
java -jar target/notification-service-1.0.0.jar \
  --spring.profiles.active=dev \
  --notification.dispatcher.enabled=false

# Run Dispatcher (in separate terminal)
java -jar target/notification-service-1.0.0.jar \
  --spring.profiles.active=dev \
  --notification.dispatcher.enabled=true
```

### 4. Test API

```bash
# Health check
curl http://localhost:8080/api/v1/health

# Create notification
curl -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "type": "TRANSACTIONAL",
    "priority": "HIGH",
    "recipientEmail": "user@example.com",
    "subject": "Test Notification",
    "body": "This is a test",
    "channels": ["EMAIL"]
  }'
```

## Docker Deployment

### Build Image

```bash
cd deployment/docker
docker build -t notification-service:latest -f Dockerfile ../..
```

### Run with Docker Compose

```bash
cd deployment
docker-compose up -d

# View logs
docker-compose logs -f notification-api
docker-compose logs -f notification-dispatcher

# Stop services
docker-compose down
```

### Push to Registry

```bash
# Tag for registry
docker tag notification-service:latest \
  your-registry.com/notification-service:v1.0.0

# Push
docker push your-registry.com/notification-service:v1.0.0
```

## Kubernetes Deployment

### 1. Create Namespace

```bash
kubectl create namespace notification-system
```

### 2. Create Secrets

```bash
# Database credentials
kubectl create secret generic notification-secrets \
  --from-literal=db-username=notification \
  --from-literal=db-password=your-secure-password \
  -n notification-system

# FCM credentials
kubectl create secret generic fcm-credentials \
  --from-file=service-account.json=path/to/fcm-service-account.json \
  -n notification-system

# APNs key
kubectl create secret generic apns-credentials \
  --from-file=apns-key.p8=path/to/AuthKey_KEYID.p8 \
  -n notification-system
```

### 3. Deploy PostgreSQL

```bash
kubectl apply -f deployment/kubernetes/postgres.yaml
```

Wait for PostgreSQL to be ready:

```bash
kubectl wait --for=condition=ready pod -l app=postgres \
  -n notification-system \
  --timeout=300s
```

### 4. Initialize Database Schema

```bash
# Copy schema file to pod
kubectl cp db/schema.sql \
  notification-system/postgres-0:/tmp/schema.sql

# Execute schema
kubectl exec -it postgres-0 -n notification-system -- \
  psql -U notification -d notifications -f /tmp/schema.sql
```

### 5. Deploy Application

```bash
# Deploy API and Dispatcher
kubectl apply -f deployment/kubernetes/deployment.yaml

# Deploy Ingress
kubectl apply -f deployment/kubernetes/ingress.yaml
```

### 6. Verify Deployment

```bash
# Check pods
kubectl get pods -n notification-system

# Check services
kubectl get svc -n notification-system

# View logs
kubectl logs -f deployment/notification-api -n notification-system
kubectl logs -f deployment/notification-dispatcher -n notification-system
```

## Scaling

### Horizontal Scaling

#### Manual Scaling

```bash
# Scale API pods
kubectl scale deployment notification-api \
  --replicas=5 \
  -n notification-system

# Scale Dispatcher pods
kubectl scale deployment notification-dispatcher \
  --replicas=3 \
  -n notification-system
```

#### Auto-Scaling

HPA is already configured in `deployment.yaml`:

```bash
# View HPA status
kubectl get hpa -n notification-system

# Describe HPA
kubectl describe hpa notification-api-hpa -n notification-system
```

### Dispatcher Scaling Considerations

Multiple dispatcher instances are safe because:

1. **FOR UPDATE SKIP LOCKED** prevents concurrent processing
2. Each instance polls independently
3. Failed jobs are automatically retried by other instances

Recommended dispatcher replicas:
- Low volume (< 1000/min): 1-2 replicas
- Medium volume (1000-10000/min): 2-4 replicas
- High volume (> 10000/min): 4-8 replicas

### Database Scaling

For production, consider:

1. **Connection Pooling**: Adjust HikariCP settings
2. **Read Replicas**: For query-heavy workloads
3. **Partitioning**: For large tables (outbox_event, audit_log)
4. **Archiving**: Move old data to separate tables

## Monitoring

### Health Endpoints

```bash
# Liveness probe
curl http://api.notification.example.com/api/v1/health/live

# Readiness probe
curl http://api.notification.example.com/api/v1/health/ready
```

### Metrics

Application exposes metrics at `/actuator/prometheus`:

```yaml
# prometheus.yaml
scrape_configs:
  - job_name: 'notification-service'
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names:
            - notification-system
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
        action: keep
        regex: true
```

### Key Metrics to Monitor

1. **API Metrics**
   - Request rate: `http_server_requests_seconds_count`
   - Error rate: `http_server_requests_seconds_count{status="5xx"}`
   - Latency: `http_server_requests_seconds_sum`

2. **Dispatcher Metrics**
   - Outbox processing rate: `outbox_events_processed_total`
   - Delivery success rate: `notifications_delivered_total`
   - Delivery failure rate: `notifications_failed_total`

3. **Database Metrics**
   - Connection pool usage: `hikaricp_connections_active`
   - Query latency: `spring_data_repository_invocations_seconds`

4. **JVM Metrics**
   - Heap usage: `jvm_memory_used_bytes{area="heap"}`
   - GC pause: `jvm_gc_pause_seconds_sum`

### Logging

Centralized logging with ELK/Loki:

```yaml
# fluentd-config.yaml
<source>
  @type tail
  path /var/log/containers/*notification*.log
  pos_file /var/log/fluentd-notification.pos
  tag kubernetes.*
  <parse>
    @type json
  </parse>
</source>
```

### Alerting

Example Prometheus alerts:

```yaml
groups:
  - name: notification-alerts
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status="5xx"}[5m]) > 0.05
        for: 5m
        annotations:
          summary: "High error rate detected"

      - alert: DispatcherStuck
        expr: rate(outbox_events_processed_total[5m]) == 0
        for: 10m
        annotations:
          summary: "Dispatcher not processing events"

      - alert: HighDeliveryFailureRate
        expr: rate(notifications_failed_total[5m]) > 0.1
        for: 5m
        annotations:
          summary: "High notification failure rate"
```

## Troubleshooting

### Common Issues

#### 1. Dispatcher Not Processing Events

**Symptoms**: Outbox events remain in PENDING status

**Diagnosis**:
```bash
# Check dispatcher pods
kubectl logs -l component=dispatcher -n notification-system

# Check database
kubectl exec -it postgres-0 -n notification-system -- \
  psql -U notification -d notifications \
  -c "SELECT status, COUNT(*) FROM outbox_event GROUP BY status;"
```

**Solutions**:
- Verify `DISPATCHER_ENABLED=true` in dispatcher pods
- Check for database connection errors
- Verify FOR UPDATE SKIP LOCKED support (PostgreSQL 9.5+)

#### 2. High Memory Usage

**Symptoms**: Pods restarting due to OOM

**Diagnosis**:
```bash
# Check memory usage
kubectl top pods -n notification-system

# Get heap dump
kubectl exec notification-api-xxx -n notification-system -- \
  jcmd 1 GC.heap_dump /tmp/heapdump.hprof
```

**Solutions**:
- Increase memory limits in deployment.yaml
- Adjust JVM heap size: `-Xmx1024m`
- Reduce batch size in dispatcher

#### 3. Database Connection Pool Exhausted

**Symptoms**: "Connection timeout" errors

**Diagnosis**:
```bash
# Check active connections
kubectl exec -it postgres-0 -n notification-system -- \
  psql -U notification -d notifications \
  -c "SELECT count(*) FROM pg_stat_activity WHERE state = 'active';"
```

**Solutions**:
- Increase HikariCP pool size
- Add more database replicas
- Optimize slow queries

#### 4. WebSocket Connection Failures

**Symptoms**: Clients unable to connect to WebSocket

**Diagnosis**:
```bash
# Test WebSocket endpoint
wscat -c ws://api.notification.example.com/ws/notifications
```

**Solutions**:
- Verify Ingress WebSocket configuration
- Enable session affinity: `sessionAffinity: ClientIP`
- Check firewall rules

### Performance Tuning

#### Database

```sql
-- Add indexes for common queries
CREATE INDEX CONCURRENTLY idx_outbox_pending
  ON outbox_event (status, created_at)
  WHERE status = 'PENDING';

-- Vacuum regularly
VACUUM ANALYZE outbox_event;

-- Monitor slow queries
SELECT query, mean_exec_time, calls
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 10;
```

#### Application

```yaml
# Increase connection pool
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10

# Batch processing
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50
        order_inserts: true
        order_updates: true

# Async processing
notification:
  dispatcher:
    batch-size: 200
    pool-size: 10
```

## Security Best Practices

1. **Secrets Management**
   - Use Kubernetes Secrets or external secret managers (Vault, AWS Secrets Manager)
   - Rotate credentials regularly
   - Never commit secrets to version control

2. **Network Security**
   - Use NetworkPolicies to restrict pod communication
   - Enable TLS for Ingress
   - Use private container registry

3. **RBAC**
   - Create service accounts with minimal permissions
   - Use Pod Security Standards

4. **Database Security**
   - Enable SSL for database connections
   - Use strong passwords
   - Regular backups

## Backup and Recovery

### Database Backup

```bash
# Automated backup (CronJob)
kubectl apply -f - <<EOF
apiVersion: batch/v1
kind: CronJob
metadata:
  name: postgres-backup
  namespace: notification-system
spec:
  schedule: "0 2 * * *"  # 2 AM daily
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: postgres:14-alpine
            command:
            - sh
            - -c
            - |
              pg_dump -U notification notifications > /backup/backup-$(date +%Y%m%d-%H%M%S).sql
            volumeMounts:
            - name: backup
              mountPath: /backup
          restartPolicy: OnFailure
          volumes:
          - name: backup
            persistentVolumeClaim:
              claimName: backup-pvc
EOF
```

### Disaster Recovery

```bash
# Restore from backup
kubectl exec -it postgres-0 -n notification-system -- \
  psql -U notification -d notifications < backup-20250120.sql
```

## Production Checklist

- [ ] Database backups configured
- [ ] Monitoring and alerting set up
- [ ] Resource limits configured
- [ ] HPA enabled
- [ ] PodDisruptionBudgets configured
- [ ] Secrets properly managed
- [ ] TLS certificates configured
- [ ] Logging centralized
- [ ] Documentation updated
- [ ] Runbooks created
- [ ] Disaster recovery tested
