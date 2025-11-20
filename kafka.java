# Refactoring Guide: Kafka Consumer Verticle

## BEFORE (Old Code Pattern)

```java
public class BankConsumerVerticle extends AbstractVerticle {
    
    private static final Logger logger = LoggerFactory.getLogger(BankConsumerVerticle.class);
    
    @Override
    public void start() {
        KafkaConsumer<String, String> consumer = KafkaConsumer.create(vertx, config);
        
        consumer.handler(record -> {
            logger.info("Received message from Bank topic: " + record.value());
            logger.info("Partition: " + record.partition() + ", Offset: " + record.offset());
            logger.info("Processing Bank message with correlation ID: " + extractCorrelationId(record));
            
            // Process message
            processMessage(record);
        });
        
        consumer.subscribe("bank-topic");
    }
    
    private void processMessage(KafkaConsumerRecord<String, String> record) {
        logger.info("Starting Bank message processing");
        // ... processing logic
        logger.info("Completed Bank message processing");
    }
}
```

**Problems:**
- ❌ 4 log statements for one message (noise!)
- ❌ Logs 100% of messages (no sampling)
- ❌ Unstructured format
- ❌ Same code duplicated in SPC, ABC, etc. verticles

---

## AFTER (New Code Pattern)

```java
import com.yourcompany.logging.PipelineLogger;

public class BankConsumerVerticle extends AbstractVerticle {
    
    private static final String TOPIC = "Bank";
    
    @Override
    public void start() {
        KafkaConsumer<String, String> consumer = KafkaConsumer.create(vertx, config);
        
        consumer.handler(record -> {
            String correlationId = extractCorrelationId(record);
            
            // Single log call (sampled at 1%)
            PipelineLogger.logConsumed(
                TOPIC,
                correlationId,
                record.key(),
                record.partition(),
                record.offset()
            );
            
            // Process message
            processMessage(record, correlationId);
        });
        
        consumer.subscribe("bank-topic");
    }
    
    private void processMessage(KafkaConsumerRecord<String, String> record, String correlationId) {
        try {
            // Pass correlationId to handler
            vertx.eventBus().send("bank.handler", record.value(), 
                new DeliveryOptions().addHeader("correlationId", correlationId));
        } catch (Exception e) {
            PipelineLogger.logError(correlationId, TOPIC, "CONSUMED", 
                "Failed to send to handler", e);
        }
    }
    
    private String extractCorrelationId(KafkaConsumerRecord<String, String> record) {
        // Extract from headers or generate new
        String corrId = record.headers().get("correlationId");
        return (corrId != null) ? corrId : UUID.randomUUID().toString();
    }
}
```

**Benefits:**
- ✅ 1 log statement instead of 4 (75% reduction)
- ✅ Automatically sampled (99% reduction in volume)
- ✅ Structured format ready for OpenSearch
- ✅ CorrelationId tracked properly
- ✅ Error handling included

---

## REFACTORING CHECKLIST

### For Each Kafka Consumer Verticle:

- [ ] Import `PipelineLogger`
- [ ] Define `TOPIC` constant (e.g., "Bank", "SPC", "ABC")
- [ ] Extract correlation ID at message receipt
- [ ] Replace all INFO logs with single `PipelineLogger.logConsumed()` call
- [ ] Pass correlationId to next stage (handler/service)
- [ ] Add error logging with `PipelineLogger.logError()`
- [ ] Remove all other logging statements in this class
- [ ] Test with sample messages

---

## IMPLEMENTATION NOTES

### 1. Correlation ID Handling

**If you already have correlation IDs in Kafka headers:**
```java
String corrId = record.headers().get("correlationId");
```

**If generating new ones:**
```java
String corrId = UUID.randomUUID().toString();
// Optionally add to headers for downstream
record.headers().add("correlationId", corrId);
```

### 2. Passing CorrelationId to Next Stage

**Option A: Via Event Bus Headers (Vert.x)**
```java
DeliveryOptions options = new DeliveryOptions()
    .addHeader("correlationId", correlationId)
    .addHeader("topic", TOPIC);
    
vertx.eventBus().send("handler.address", message, options);
```

**Option B: Include in Message Payload**
```java
JsonObject payload = new JsonObject()
    .put("correlationId", correlationId)
    .put("topic", TOPIC)
    .put("data", record.value());
    
vertx.eventBus().send("handler.address", payload);
```

**Option C: Create Context Object**
```java
public class MessageContext {
    private String correlationId;
    private String topic;
    private String originalMessage;
    // ... getters/setters
}

MessageContext ctx = new MessageContext(correlationId, TOPIC, record.value());
processMessage(ctx);
```

### 3. Error Handling Pattern

Always wrap processing in try-catch:
```java
try {
    // Log consumed (sampled)
    PipelineLogger.logConsumed(TOPIC, correlationId, ...);
    
    // Process
    processMessage(record, correlationId);
    
} catch (Exception e) {
    // Always log errors (not sampled)
    PipelineLogger.logError(correlationId, TOPIC, "CONSUMED", e.getMessage(), e);
    
    // Send to retry topic if applicable
    sendToRetry(record, e);
}
```

---

## TESTING CHECKLIST

After refactoring:

- [ ] Run application with sample Kafka messages
- [ ] Verify logs appear in `logs/pipeline-processor.json`
- [ ] Check that only ~1% of messages are logged (sampling works)
- [ ] Verify JSON structure is correct
- [ ] Check that correlation ID appears in logs
- [ ] Test error scenario (invalid message)
- [ ] Verify error logs appear immediately (not sampled)
- [ ] Check log file rotation works
- [ ] Verify async logging performance (no blocking)

---

## REPLICATION TO OTHER TOPICS

Once Bank topic works:

1. Copy the pattern to SPCConsumerVerticle
2. Only change: `private static final String TOPIC = "SPC";`
3. Everything else stays the same!
4. Repeat for all 15 topics

**Estimated time per topic:** 15-30 minutes

---

## EXPECTED LOG VOLUME REDUCTION

**Before:**
- Bank topic: 100K messages in 5 min
- Logs per message: 4
- Total logs: 400K logs in 5 min

**After:**
- Bank topic: 100K messages in 5 min
- Logs per message: 1 (sampled at 1%)
- Total logs: 1K logs in 5 min

**Reduction: 99.75% for this stage!**
