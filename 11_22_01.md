# Address Processing System - Logging Architecture & Strategy

## Executive Summary
This document outlines the comprehensive logging strategy for the Address Processing System, designed for optimal observability, debugging, and monitoring through ELF Dashboard.

---

## 1. Core Logging Principles

### 1.1 Key Objectives
- **Traceability**: Track every message from Kafka ingestion to completion
- **Debuggability**: Quick root cause analysis
- **Observability**: Real-time system health monitoring
- **Compliance**: Audit trail for data operations
- **Performance**: Minimal overhead with maximum insight

### 1.2 Log Format Standard
```json
{
  "timestamp": "2024-11-22T10:30:45.123Z",
  "correlationId": "UUID",
  "domain": "ADDRESS",
  "component": "AddressVerticle|AddressHandler",
  "event": "EVENT_NAME",
  "level": "INFO|DEBUG|WARN|ERROR",
  "message": "Human readable message",
  "context": {
    "batchId": "string",
    "recordId": "string",
    "kafkaTopic": "string",
    "kafkaPartition": "int",
    "kafkaOffset": "long"
  },
  "metadata": {
    // Event-specific data
  },
  "duration": "milliseconds",
  "errorDetails": {
    // Only for ERROR/WARN
  }
}
```

---

## 2. Logging Flow Architecture

### 2.1 Message Processing Lifecycle

```
┌─────────────────────────────────────────────────────────────────┐
│                     KAFKA MESSAGE ARRIVAL                        │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │  LOG: MESSAGE_RECEIVED                 │
        │  Level: INFO                           │
        │  Context: Topic, Partition, Offset     │
        └────────────────────┬───────────────────┘
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │  LOG: BATCH_PROCESSING_STARTED         │
        │  Level: INFO                           │
        │  Context: BatchId, RecordCount         │
        └────────────────────┬───────────────────┘
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │  LOG: RECORD_PROCESSING_STARTED        │
        │  Level: DEBUG                          │
        │  Context: RecordId, Index              │
        └────────────────────┬───────────────────┘
                             │
                    ┌────────┴────────┐
                    │                 │
                    ▼                 ▼
        ┌───────────────────┐  ┌──────────────────┐
        │ VALIDATION PHASE  │  │  DATABASE PHASE  │
        └───────────────────┘  └──────────────────┘
                    │                 │
                    └────────┬────────┘
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │  LOG: KAFKA_PUBLISH_INITIATED          │
        │  Level: INFO                           │
        └────────────────────┬───────────────────┘
                             │
                    ┌────────┴────────┐
                    │                 │
                    ▼                 ▼
        ┌───────────────────┐  ┌──────────────────┐
        │   SUCCESS PATH    │  │   FAILURE PATH   │
        └───────────────────┘  └──────────────────┘
                    │                 │
                    │                 ▼
                    │      ┌──────────────────────┐
                    │      │  LOG: RETRY_STORED   │
                    │      │  Level: WARN         │
                    │      └──────────────────────┘
                    │
                    ▼
        ┌────────────────────────────────────────┐
        │  LOG: MESSAGE_PROCESSING_COMPLETED     │
        │  Level: INFO                           │
        │  Context: Status, Duration, Metrics    │
        └────────────────────────────────────────┘
```

---

## 3. Standardized Log Events

### 3.1 AddressVerticle Events

| Event Code | Level | Description | Key Metadata |
|------------|-------|-------------|--------------|
| `VERTICLE_DEPLOYED` | INFO | Verticle initialization complete | deploymentId, config |
| `KAFKA_SUBSCRIPTION_SUCCESS` | INFO | Kafka consumer subscribed | topic, groupId, partitions |
| `KAFKA_SUBSCRIPTION_FAILED` | ERROR | Kafka subscription error | topic, error |
| `MESSAGE_RECEIVED` | INFO | Kafka message consumed | topic, partition, offset, key |
| `MESSAGE_DESERIALIZATION_FAILED` | ERROR | Failed to parse message | offset, rawPayload, error |
| `MESSAGE_PROCESSING_STARTED` | DEBUG | Processing initiated | correlationId, messageSize |
| `MESSAGE_PROCESSING_COMPLETED` | INFO | Processing finished | correlationId, duration, status |
| `MESSAGE_PROCESSING_FAILED` | ERROR | Processing error | correlationId, stage, error |
| `RETRY_SCHEDULED` | WARN | Message scheduled for retry | correlationId, retryCount, nextRetryAt |
| `DLQ_PUBLISHED` | ERROR | Message sent to DLQ | correlationId, reason |

### 3.2 AddressHandler Events

#### Batch & Initialization
| Event Code | Level | Description | Key Metadata |
|------------|-------|-------------|--------------|
| `HANDLER_INITIALIZED` | INFO | Handler instantiated | handlerId, config |
| `BATCH_PROCESSING_STARTED` | INFO | Batch processing initiated | batchId, recordCount, domain, table |
| `BATCH_PROCESSING_COMPLETED` | INFO | Batch processing finished | batchId, successCount, failureCount, duration |
| `BATCH_PROCESSING_FAILED` | ERROR | Batch processing error | batchId, failedAt, error |

#### Data Processing
| Event Code | Level | Description | Key Metadata |
|------------|-------|-------------|--------------|
| `RECORD_PROCESSING_STARTED` | DEBUG | Individual record processing | recordId, index, addressType |
| `DATA_EXTRACTION_SUCCESS` | DEBUG | Data extracted from request | recordId, fieldCount |
| `DATA_EXTRACTION_FAILED` | ERROR | Data extraction error | recordId, error |
| `ENCRYPTION_STARTED` | DEBUG | Field encryption initiated | recordId, fieldList |
| `ENCRYPTION_SUCCESS` | DEBUG | Encryption completed | recordId, encryptedCount |
| `ENCRYPTION_FAILED` | ERROR | Encryption error | recordId, field, error |
| `DECRYPTION_STARTED` | DEBUG | Field decryption initiated | recordId, fieldList |
| `DECRYPTION_SUCCESS` | DEBUG | Decryption completed | recordId, decryptedCount |
| `DECRYPTION_FAILED` | ERROR | Decryption error | recordId, field, error |

#### Validation
| Event Code | Level | Description | Key Metadata |
|------------|-------|-------------|--------------|
| `VALIDATION_STARTED` | DEBUG | Validation jar invoked | recordId, validatorVersion |
| `VALIDATION_SUCCESS` | DEBUG | Validation passed | recordId, rulesApplied, validationTime |
| `VALIDATION_FAILED` | WARN | Validation failed | recordId, failedRules, violations |
| `VALIDATION_JAR_ERROR` | ERROR | Validator exception | recordId, jarVersion, error |

#### Database Operations
| Event Code | Level | Description | Key Metadata |
|------------|-------|-------------|--------------|
| `DB_QUERY_GENERATED` | DEBUG | SQL/Query prepared | recordId, queryType, parameters |
| `DB_OPERATION_STARTED` | DEBUG | Database call initiated | recordId, operation, table |
| `DB_OPERATION_SUCCESS` | INFO | DB operation completed | recordId, operation, rowsAffected, duration |
| `DB_OPERATION_PARTIAL` | WARN | Partial success | recordId, successCount, failedCount, errors |
| `DB_OPERATION_FAILED` | ERROR | DB operation failed | recordId, operation, sqlState, error |
| `DB_CONNECTION_ERROR` | ERROR | Connection issue | connectionPool, error |
| `DB_TIMEOUT` | WARN | Query timeout | recordId, query, timeoutMs |

#### Kafka Publishing
| Event Code | Level | Description | Key Metadata |
|------------|-------|-------------|--------------|
| `KAFKA_PUBLISH_STARTED` | DEBUG | Publishing to Kafka | recordId, topic, partition |
| `KAFKA_PUBLISH_SUCCESS` | INFO | Message published | recordId, topic, partition, offset |
| `KAFKA_PUBLISH_FAILED` | ERROR | Publish error | recordId, topic, error |
| `KAFKA_PRODUCER_ERROR` | ERROR | Producer exception | producerId, error |

#### Retry & Error Handling
| Event Code | Level | Description | Key Metadata |
|------------|-------|-------------|--------------|
| `RETRY_TABLE_WRITE_STARTED` | DEBUG | Writing to retry table | recordId, retryReason |
| `RETRY_TABLE_WRITE_SUCCESS` | WARN | Stored for retry | recordId, retryId, nextRetry |
| `RETRY_TABLE_WRITE_FAILED` | ERROR | Failed to store retry | recordId, error |
| `MAX_RETRY_EXCEEDED` | ERROR | Retry limit reached | recordId, attemptCount, lastError |
| `VD_JAR_ERROR` | ERROR | VD jar exception | recordId, jarVersion, error |

---

## 4. Contextual Information Strategy

### 4.1 Mandatory Context (Every Log)
```json
{
  "timestamp": "ISO-8601",
  "correlationId": "UUID - tracks single message end-to-end",
  "domain": "ADDRESS",
  "component": "AddressVerticle|AddressHandler",
  "version": "application version",
  "environment": "DEV|UAT|PROD",
  "hostname": "pod/instance identifier"
}
```

### 4.2 Message Context (Kafka Origin)
```json
{
  "kafka.topic": "address.topic.name",
  "kafka.partition": 3,
  "kafka.offset": 123456,
  "kafka.key": "message key",
  "kafka.timestamp": "message timestamp",
  "kafka.groupId": "consumer group"
}
```

### 4.3 Business Context
```json
{
  "batchId": "unique batch identifier",
  "recordId": "individual record identifier",
  "recordIndex": "position in batch",
  "domain": "ADDRESS",
  "table": "target table name",
  "addressType": "BILLING|SHIPPING|MAILING",
  "customerId": "if applicable",
  "accountId": "if applicable"
}
```

### 4.4 Technical Context
```json
{
  "threadId": "processing thread",
  "sessionId": "if applicable",
  "operation": "INSERT|UPDATE|DELETE",
  "retryCount": "current retry attempt",
  "processingNode": "pod identifier"
}
```

---

## 5. Error Logging Standards

### 5.1 Error Log Structure
```json
{
  "timestamp": "2024-11-22T10:30:45.123Z",
  "correlationId": "abc-123-def",
  "component": "AddressHandler",
  "event": "DB_OPERATION_FAILED",
  "level": "ERROR",
  "message": "Database operation failed for record",
  "context": {
    "recordId": "REC-001",
    "operation": "UPDATE",
    "table": "ADDRESS_MASTER"
  },
  "errorDetails": {
    "errorType": "SQLException",
    "errorCode": "23000",
    "errorMessage": "Duplicate key violation",
    "sqlState": "23000",
    "stackTrace": "first 10 lines",
    "rootCause": "actual root cause",
    "recoverable": true,
    "retryable": true
  },
  "impact": {
    "scope": "SINGLE_RECORD|BATCH|SYSTEM",
    "severity": "LOW|MEDIUM|HIGH|CRITICAL",
    "userImpact": "description"
  }
}
```

### 5.2 Error Categories

#### Category 1: Data Errors (WARN/ERROR)
- Validation failures
- Data format issues
- Missing required fields
- Business rule violations

#### Category 2: Integration Errors (ERROR)
- Kafka publish failures
- Database connection issues
- External service timeouts
- VD jar errors
- Validation jar errors

#### Category 3: System Errors (ERROR/CRITICAL)
- Out of memory
- Thread exhaustion
- Configuration errors
- Deployment issues

#### Category 4: Security Errors (ERROR)
- Encryption/Decryption failures
- Authentication issues
- Authorization failures

---

## 6. Performance & Metrics Logging

### 6.1 Duration Tracking
Log execution time for:
```json
{
  "event": "OPERATION_METRICS",
  "level": "INFO",
  "metrics": {
    "operation": "RECORD_PROCESSING",
    "duration": 245,  // milliseconds
    "breakdown": {
      "validation": 45,
      "encryption": 30,
      "dbOperation": 120,
      "kafkaPublish": 50
    }
  }
}
```

### 6.2 Throughput Metrics
```json
{
  "event": "BATCH_METRICS",
  "level": "INFO",
  "metrics": {
    "batchId": "BATCH-001",
    "totalRecords": 1000,
    "processedRecords": 980,
    "failedRecords": 20,
    "duration": 5400,  // ms
    "throughput": 185,  // records/sec
    "avgRecordTime": 5.4  // ms
  }
}
```

---

## 7. Implementation Examples

### 7.1 AddressVerticle - Message Received
```java
private void handleKafkaMessage(KafkaConsumerRecord<String, String> record) {
    String correlationId = UUID.randomUUID().toString();
    MDC.put("correlationId", correlationId);
    MDC.put("kafkaTopic", record.topic());
    MDC.put("kafkaPartition", String.valueOf(record.partition()));
    MDC.put("kafkaOffset", String.valueOf(record.offset()));
    
    logger.info("MESSAGE_RECEIVED | Kafka message consumed | " +
                "key={} size={}", 
                record.key(), 
                record.value().length());
    
    long startTime = System.currentTimeMillis();
    
    try {
        processMessage(record, correlationId);
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("MESSAGE_PROCESSING_COMPLETED | " +
                   "status=SUCCESS duration={}ms", duration);
        
    } catch (Exception e) {
        logger.error("MESSAGE_PROCESSING_FAILED | " +
                    "stage={} error={}", 
                    getCurrentStage(), 
                    e.getMessage(), 
                    e);
        handleError(record, correlationId, e);
    } finally {
        MDC.clear();
    }
}
```

### 7.2 AddressHandler - Batch Processing
```java
public void processBatch(List<AddressRecord> records, String batchId) {
    MDC.put("batchId", batchId);
    MDC.put("recordCount", String.valueOf(records.size()));
    
    logger.info("BATCH_PROCESSING_STARTED | " +
               "domain={} table={} count={}", 
               domain, table, records.size());
    
    long startTime = System.currentTimeMillis();
    int successCount = 0;
    int failureCount = 0;
    
    for (int i = 0; i < records.size(); i++) {
        AddressRecord record = records.get(i);
        MDC.put("recordId", record.getId());
        MDC.put("recordIndex", String.valueOf(i));
        
        try {
            logger.debug("RECORD_PROCESSING_STARTED | " +
                        "index={} type={}", i, record.getType());
            
            processRecord(record);
            successCount++;
            
        } catch (Exception e) {
            failureCount++;
            logger.error("RECORD_PROCESSING_FAILED | " +
                        "index={} error={}", i, e.getMessage(), e);
        } finally {
            MDC.remove("recordId");
            MDC.remove("recordIndex");
        }
    }
    
    long duration = System.currentTimeMillis() - startTime;
    
    logger.info("BATCH_PROCESSING_COMPLETED | " +
               "success={} failed={} duration={}ms throughput={}/sec",
               successCount, 
               failureCount, 
               duration,
               (records.size() * 1000.0 / duration));
    
    MDC.clear();
}
```

### 7.3 Validation with Detailed Logging
```java
private void validateRecord(AddressRecord record) {
    logger.debug("VALIDATION_STARTED | " +
                "validatorVersion={} rules={}", 
                validatorVersion, 
                validationRules.size());
    
    long startTime = System.currentTimeMillis();
    
    try {
        ValidationResult result = validationJar.validate(record);
        long duration = System.currentTimeMillis() - startTime;
        
        if (result.isSuccess()) {
            logger.debug("VALIDATION_SUCCESS | " +
                        "rulesApplied={} duration={}ms", 
                        result.getRulesApplied(), 
                        duration);
        } else {
            logger.warn("VALIDATION_FAILED | " +
                       "violations={} failedRules={}", 
                       result.getViolations().size(),
                       result.getFailedRules());
            
            // Log each violation
            result.getViolations().forEach(v -> 
                logger.warn("VALIDATION_VIOLATION | " +
                           "field={} rule={} message={}", 
                           v.getField(), 
                           v.getRule(), 
                           v.getMessage())
            );
        }
        
    } catch (Exception e) {
        logger.error("VALIDATION_JAR_ERROR | " +
                    "jarVersion={} error={}", 
                    validatorVersion, 
                    e.getMessage(), 
                    e);
        throw new ValidationException("Validation jar error", e);
    }
}
```

### 7.4 Database Operation with Retry Logging
```java
private void executeDatabaseOperation(AddressRecord record, String operation) {
    logger.debug("DB_QUERY_GENERATED | " +
                "operation={} table={} params={}", 
                operation, 
                tableName, 
                getParameterSummary(record));
    
    logger.debug("DB_OPERATION_STARTED | operation={}", operation);
    
    long startTime = System.currentTimeMillis();
    
    try {
        int rowsAffected = dbJar.execute(record);
        long duration = System.currentTimeMillis() - startTime;
        
        logger.info("DB_OPERATION_SUCCESS | " +
                   "operation={} rowsAffected={} duration={}ms", 
                   operation, 
                   rowsAffected, 
                   duration);
        
    } catch (SQLException e) {
        long duration = System.currentTimeMillis() - startTime;
        
        logger.error("DB_OPERATION_FAILED | " +
                    "operation={} sqlState={} errorCode={} duration={}ms error={}", 
                    operation,
                    e.getSQLState(),
                    e.getErrorCode(),
                    duration,
                    e.getMessage(),
                    e);
        
        // Store in retry table
        storeForRetry(record, e);
        
        throw new DatabaseException("DB operation failed", e);
    }
}

private void storeForRetry(AddressRecord record, Exception error) {
    logger.debug("RETRY_TABLE_WRITE_STARTED | reason={}", 
                error.getMessage());
    
    try {
        String retryId = retryService.store(record, error);
        
        logger.warn("RETRY_TABLE_WRITE_SUCCESS | " +
                   "retryId={} nextRetry={} attemptCount={}", 
                   retryId,
                   calculateNextRetry(),
                   record.getRetryCount());
        
    } catch (Exception e) {
        logger.error("RETRY_TABLE_WRITE_FAILED | " +
                    "originalError={} retryError={}", 
                    error.getMessage(),
                    e.getMessage(),
                    e);
    }
}
```

### 7.5 Kafka Publishing
```java
private void publishToKafka(AddressRecord record, String topic) {
    logger.debug("KAFKA_PUBLISH_STARTED | topic={} key={}", 
                topic, 
                record.getKey());
    
    long startTime = System.currentTimeMillis();
    
    try {
        RecordMetadata metadata = kafkaProducer.send(
            new ProducerRecord<>(topic, record.getKey(), record.toJson())
        ).get();
        
        long duration = System.currentTimeMillis() - startTime;
        
        logger.info("KAFKA_PUBLISH_SUCCESS | " +
                   "topic={} partition={} offset={} duration={}ms", 
                   metadata.topic(),
                   metadata.partition(),
                   metadata.offset(),
                   duration);
        
    } catch (Exception e) {
        long duration = System.currentTimeMillis() - startTime;
        
        logger.error("KAFKA_PUBLISH_FAILED | " +
                    "topic={} duration={}ms error={}", 
                    topic,
                    duration,
                    e.getMessage(),
                    e);
        
        // Store in retry table
        storeForRetry(record, e);
    }
}
```

---

## 8. ELF Dashboard Query Patterns

### 8.1 Critical Monitoring Queries

#### Query 1: End-to-End Message Tracing
```
correlationId:"abc-123-def" AND domain:"ADDRESS"
| sort timestamp asc
```

#### Query 2: Error Rate by Component
```
level:"ERROR" AND domain:"ADDRESS"
| stats count by component, event
| sort count desc
```

#### Query 3: Performance Bottlenecks
```
event:*_COMPLETED AND duration:>1000
| stats avg(duration), max(duration), count by event
```

#### Query 4: Validation Failure Analysis
```
event:"VALIDATION_FAILED" 
| stats count by failedRules
| sort count desc
```

#### Query 5: Retry Table Activity
```
event:("RETRY_TABLE_WRITE_SUCCESS" OR "MAX_RETRY_EXCEEDED")
| stats count by event, retryReason
```

#### Query 6: Database Operation Health
```
event:DB_OPERATION_*
| stats count by event, sqlState
| sort count desc
```

#### Query 7: Batch Processing Metrics
```
event:"BATCH_PROCESSING_COMPLETED"
| stats avg(successCount), avg(failureCount), avg(duration) by table
```

#### Query 8: Kafka Consumer Lag
```
event:"MESSAGE_RECEIVED"
| stats count by kafka.partition
| timechart span=1m count
```

### 8.2 Dashboard Widgets

#### Widget 1: Processing Funnel
```
MESSAGE_RECEIVED → BATCH_PROCESSING_STARTED → 
VALIDATION_SUCCESS → DB_OPERATION_SUCCESS → 
KAFKA_PUBLISH_SUCCESS → MESSAGE_PROCESSING_COMPLETED
```

#### Widget 2: Error Distribution
```
Pie chart: count by errorType
- ValidationError
- DatabaseError
- KafkaError
- EncryptionError
- SystemError
```

#### Widget 3: Throughput Graph
```
Line chart: records/sec over time
event:"BATCH_METRICS" 
| timechart avg(throughput) by table
```

#### Widget 4: Retry Queue Size
```
Single value: 
event:"RETRY_TABLE_WRITE_SUCCESS" 
| stats count - (count where event:"RETRY_PROCESSED")
```

---

## 9. Log Levels Strategy

### 9.1 Level Guidelines

| Level | Usage | Examples | Retention |
|-------|-------|----------|-----------|
| **ERROR** | System failures, unhandled exceptions, critical errors | DB connection failed, Kafka publish failed, Validation jar crashed | 90 days |
| **WARN** | Recoverable issues, business validation failures, retry scenarios | Validation failed, Partial DB success, Retry stored | 60 days |
| **INFO** | Key business events, state changes, milestones | Batch started, Message processed, DB operation success | 30 days |
| **DEBUG** | Detailed flow, step-by-step processing | Query generated, Encryption started, Record processing | 7 days |
| **TRACE** | Very detailed debugging (disabled in production) | Field-level changes, Crypto details | Not in PROD |

### 9.2 Dynamic Log Level Control
```java
// Support runtime log level changes
@Configuration
public class LoggingConfig {
    
    @Value("${logging.level.address:INFO}")
    private String defaultLevel;
    
    // Allow per-component level override
    public void setLogLevel(String component, String level) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(component);
        logger.setLevel(Level.valueOf(level));
        
        logger.info("LOG_LEVEL_CHANGED | component={} newLevel={}", 
                   component, level);
    }
}
```

---

## 10. Best Practices & Recommendations

### 10.1 DO's ✓

1. **Always use correlation IDs** - Track messages end-to-end
2. **Log before and after critical operations** - Entry/exit logging
3. **Include duration metrics** - Measure everything
4. **Use structured logging** - JSON format for parsing
5. **Sanitize sensitive data** - Mask PII, passwords, keys
6. **Log business context** - What, not just how
7. **Use meaningful event names** - Clear, searchable
8. **Include error recovery info** - Is it retryable?
9. **Log success too** - Not just failures
10. **Use MDC for context** - Automatic context propagation

### 10.2 DON'Ts ✗

1. **Don't log full payloads** - Especially in production
2. **Don't log in tight loops** - Aggregate instead
3. **Don't use string concatenation** - Use parameterized logging
4. **Don't log sensitive data** - PII, credentials, tokens
5. **Don't suppress stack traces** - Always log root cause
6. **Don't log at wrong level** - INFO for errors is bad
7. **Don't create duplicate logs** - Log once per event
8. **Don't ignore exceptions** - Empty catch blocks
9. **Don't forget correlation** - Every log needs context
10. **Don't over-log** - Balance detail with volume

### 10.3 Sensitive Data Handling
```java
public class SensitiveDataMasker {
    
    public static String maskPII(String data, String fieldName) {
        if (data == null) return null;
        
        switch (fieldName.toLowerCase()) {
            case "ssn":
            case "taxid":
                return data.substring(0, 3) + "***" + data.substring(data.length()-4);
            case "email":
                String[] parts = data.split("@");
                return parts[0].charAt(0) + "***@" + parts[1];
            case "phone":
                return "***-***-" + data.substring(data.length()-4);
            case "address":
                return data.substring(0, Math.min(10, data.length())) + "...";
            default:
                return "***MASKED***";
        }
    }
    
    // Usage in logging
    logger.info("RECORD_PROCESSED | customerId={} email={}", 
               customerId, 
               maskPII(email, "email"));
}
```

---

## 11. Monitoring & Alerting Setup

### 11.1 Critical Alerts

| Alert | Condition | Severity | Action |
|-------|-----------|----------|--------|
| High Error Rate | ERROR count > 50/min | CRITICAL | Page on-call |
| DB Connection Pool Exhausted | DB_CONNECTION_ERROR | CRITICAL | Page DBA |
| Kafka Consumer Lag | Lag > 10000 messages | HIGH | Investigate |
| Validation Jar Failure | VALIDATION_JAR_ERROR > 10/min | HIGH | Check jar |
| Retry Queue Buildup | Retry count > 5000 | MEDIUM | Review failures |
| Low Throughput | throughput < 50 rec/sec | MEDIUM | Performance review |

### 11.2 Health Checks
```java
@Component
public class HealthLogger {
    
    @Scheduled(fixedRate = 60000) // Every minute
    public void logHealthMetrics() {
        logger.info("HEALTH_CHECK | " +
                   "kafkaConnected={} " +
                   "dbPoolActive={} " +
                   "memoryUsage={}% " +
                   "threadCount={} " +
                   "queueSize={}",
                   kafkaHealthCheck.isConnected(),
                   dbPool.getActiveConnections(),
                   getMemoryUsagePercent(),
                   Thread.activeCount(),
                   processingQueue.size());
    }
}
```

---

## 12. Log Rotation & Retention

### 12.1 Configuration
```xml
<!-- logback.xml -->
<configuration>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/address-processor.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/address-processor.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <maxHistory>30</maxHistory>
            <totalSizeCap>10GB</totalSizeCap>
            <timeBasedFileNamingAndTriggeringPolicy 
                class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
                <maxFileSize>100MB</maxFileSize>
            </timeBasedFileNamingAndTriggeringPolicy>
        </rollingPolicy>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>correlationId</includeMdcKeyName>
            <includeMdcKeyName>batchId</includeMdcKeyName>
            <includeMdcKeyName>recordId</includeMdcKeyName>
        </encoder>
    </appender>
</configuration>
```

---

## 13. Sample Dashboard Layout

### Homepage View
```
┌──────────────────────────────────────────────────────────┐
│  ADDRESS PROCESSING - REAL-TIME DASHBOARD                │
├──────────────────────────────────────────────────────────┤
│                                                           │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐      │
│  │ Messages/Min│  │Success Rate │  │ Avg Latency │      │
│  │    1,245    │  │   98.5%     │  │    125ms    │      │
│  └─────────────┘  └─────────────┘  └─────────────┘      │
│                                                           │
│  Processing Funnel (Last Hour)                           │
│  ┌──────────────────────────────────────────────┐        │
│  │ Received: 75,000                              │        │
│  │ Validated: 74,500 (99.3%)                    │        │
│  │ DB Success: 73,800 (98.4%)                   │        │
│  │ Published: 73,500 (98%)                      │        │
│  │ Retried: 700                                 │        │
│  │ Failed: 800                                  │        │
│  └──────────────────────────────────────────────┘        │
│                                                           │
│  Error Distribution                                      │
│  ┌──────────────────────────────────────────────┐        │
│  │ [Pie Chart]                                  │        │
│  │ - Validation: 45%                            │        │
│  │ - DB Errors: 30%                             │        │
│  │ - Kafka: 15%                                 │        │
│  │ - Other: 10%                                 │        │
│  └──────────────────────────────────────────────┘        │
│                                                           │
│  Recent Critical Errors (Last 15 min)                    │
│  ┌──────────────────────────────────────────────┐        │
│  │ 10:45:23 DB_CONNECTION_ERROR Pool exhausted  │        │
│  │ 10:42:11 KAFKA_PUBLISH_FAILED Topic down     │        │
│  │ 10:40:05 VALIDATION_JAR_ERROR NPE in v2.3    │        │
│  └──────────────────────────────────────────────┘        │
└──────────────────────────────────────────────────────────┘
```

---

## 14. Implementation Checklist

- [ ] Update AddressVerticle with standardized event codes
- [ ] Update AddressHandler with standardized event codes
- [ ] Implement MDC for correlation tracking
- [ ] Configure JSON logging (Logstash encoder)
- [ ] Add duration metrics to all operations
- [ ] Implement sensitive data masking
- [ ] Set up log rotation policies
- [ ] Create ELF dashboard queries
- [ ] Configure alerts in monitoring system
- [ ] Add health check logging
- [ ] Document custom event codes for team
- [ ] Create runbook for common error scenarios
- [ ] Set up log aggregation pipeline
- [ ] Test correlation ID propagation
- [ ] Validate dashboard queries
- [ ] Train team on new logging standards

---

## 15. Conclusion

This logging architecture provides:

✅ **Complete Traceability** - Every message tracked from Kafka to completion  
✅ **Fast Debugging** - Correlation IDs and structured events  
✅ **Operational Visibility** - Real-time metrics and health monitoring  
✅ **Proactive Alerting** - Early warning for system issues  
✅ **Audit Compliance** - Complete trail of data operations  
✅ **Performance Insights** - Bottleneck identification  
✅ **Scalability** - Handles high-volume logging efficiently  

**Key Success Metrics:**
- Mean Time to Detection (MTTD): < 2 minutes
- Mean Time to Resolution (MTTR): < 15 minutes  
- Log query performance: < 3 seconds
- False positive alerts: < 5%
- Dashboard load time: < 2 seconds

---

*Document Version: 1.0*  
*Last Updated: 2024-11-22*  
*Owner: Architecture Team*
