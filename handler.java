# Refactoring Guide: Handler Class

## BEFORE (Old Code Pattern)

```java
public class BankHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(BankHandler.class);
    
    public void handle(Message<String> message) {
        logger.info("Bank handler received message");
        logger.info("Processing Bank message in handler");
        
        try {
            // Validate
            logger.info("Validating Bank message");
            boolean isValid = validate(message.body());
            
            if (isValid) {
                logger.info("Bank message validation passed");
                // Process
                processValidMessage(message.body());
                logger.info("Bank message processing completed");
            } else {
                logger.warn("Bank message validation failed");
                // Handle invalid
                handleInvalidMessage(message.body());
            }
            
        } catch (Exception e) {
            logger.error("Error processing Bank message: " + e.getMessage());
        }
    }
}
```

**Problems:**
- ❌ 7 log statements for orchestration (noise!)
- ❌ No timing information
- ❌ Validation logs belong in ValidationService, not here
- ❌ Duplicated across all handlers

---

## AFTER (New Code Pattern)

```java
import com.yourcompany.logging.PipelineLogger;

public class BankHandler {
    
    private ValidationService validationService;
    private DatabaseService databaseService;
    private PublisherService publisherService;
    
    public void handle(Message<String> message) {
        // Extract context from headers
        String correlationId = message.headers().get("correlationId");
        String topic = message.headers().get("topic");
        
        try {
            // 1. Validation (ValidationService logs internally)
            ValidationResult result = validationService.validate(message.body(), correlationId, topic);
            
            if (!result.isValid()) {
                // Send to retry (retry logging happens in retry handler)
                sendToRetry(message, result.getReason());
                return;
            }
            
            // 2. Database operation (DatabaseService logs internally)
            String market = result.getMarket(); // Extracted during validation
            databaseService.processData(message.body(), correlationId, topic, market);
            
            // 3. Publish (PublisherService logs internally)
            publisherService.publish(message.body(), correlationId, topic, market);
            
        } catch (Exception e) {
            // Log critical handler-level errors only
            PipelineLogger.logError(correlationId, topic, "HANDLER", 
                "Handler orchestration failed", e);
            sendToRetry(message, e.getMessage());
        }
    }
    
    private void sendToRetry(Message<String> message, String reason) {
        // Retry logic...
    }
}
```

**Benefits:**
- ✅ NO logging in handler (orchestration only)
- ✅ Each service logs its own work
- ✅ Handler stays clean and focused
- ✅ Errors still captured
- ✅ Works for ALL topics (no duplication)

---

## KEY PRINCIPLE FOR HANDLERS

**Handlers should NOT log - they orchestrate!**

### Handler Responsibilities:
1. Extract correlation ID and topic
2. Call services in order
3. Handle exceptions
4. Route to retry if needed

### Services Log Their Own Work:
- ValidationService logs validation results
- DatabaseService logs DB operations
- PublisherService logs publishing

---

## REFACTORING CHECKLIST

### For Each Handler Class:

- [ ] Remove ALL info/debug logs
- [ ] Extract correlationId and topic from message headers/context
- [ ] Pass correlationId and topic to all service calls
- [ ] Keep ONLY error logging for handler-level failures
- [ ] Remove timing code (services handle their own timing)
- [ ] Ensure services are doing their own logging
- [ ] Test end-to-end flow

---

## CONTEXT PASSING PATTERNS

### Pattern 1: Via Method Parameters (Recommended)

```java
// Handler
ValidationResult result = validationService.validate(
    message.body(), 
    correlationId, 
    topic
);

// Service
public ValidationResult validate(String data, String correlationId, String topic) {
    long startTime = System.currentTimeMillis();
    
    // Validation logic...
    
    long duration = System.currentTimeMillis() - startTime;
    
    if (isValid) {
        PipelineLogger.logValidationSuccess(correlationId, topic, duration);
    } else {
        PipelineLogger.logValidationFailure(correlationId, topic, error, ...);
    }
    
    return result;
}
```

### Pattern 2: Via Context Object

```java
public class PipelineContext {
    private String correlationId;
    private String topic;
    private String market;
    private Map<String, Object> data;
    // ... getters/setters
}

// Handler
PipelineContext ctx = new PipelineContext(correlationId, topic);
ctx.setData(message.body());

ValidationResult result = validationService.validate(ctx);
databaseService.process(ctx);
publisherService.publish(ctx);
```

---

## EXCEPTION HANDLING PATTERN

```java
public void handle(Message<String> message) {
    String correlationId = extractCorrelationId(message);
    String topic = extractTopic(message);
    
    try {
        // Service calls...
        validationService.validate(...);
        databaseService.process(...);
        publisherService.publish(...);
        
    } catch (ValidationException e) {
        // Validation service already logged
        sendToRetry(message, "VALIDATION", e.getMessage());
        
    } catch (DatabaseException e) {
        // Database service already logged
        sendToRetry(message, "DB_OPERATION", e.getMessage());
        
    } catch (Exception e) {
        // Unexpected handler-level error
        PipelineLogger.logError(correlationId, topic, "HANDLER", 
            "Unexpected handler error", e);
        sendToRetry(message, "HANDLER", e.getMessage());
    }
}
```

---

## COMMON MISTAKES TO AVOID

### ❌ Don't Do This:
```java
// Handler logging what services should log
logger.info("Calling validation service");
validationService.validate(...);
logger.info("Validation completed");
```

### ✅ Do This Instead:
```java
// Service logs internally, handler just orchestrates
ValidationResult result = validationService.validate(...);
// ValidationService calls PipelineLogger.logValidation...() internally
```

---

### ❌ Don't Do This:
```java
// Logging the same information in multiple places
logger.info("Processing message " + correlationId); // Handler
// ... then ValidationService also logs
// ... then DatabaseService also logs
// = 3x duplication!
```

### ✅ Do This Instead:
```java
// Each layer logs ONLY its own responsibility
// Handler: No logs (just orchestration)
// ValidationService: Logs validation result
// DatabaseService: Logs DB operation
// = No duplication!
```

---

## TESTING CHECKLIST

After refactoring:

- [ ] Run end-to-end test with one message
- [ ] Verify handler has NO logs in output
- [ ] Verify validation logs appear (from ValidationService)
- [ ] Verify DB operation logs appear (from DatabaseService)
- [ ] Verify publish logs appear (from PublisherService)
- [ ] Test error scenario - verify error logged once
- [ ] Check correlation ID appears in all service logs
- [ ] Verify log volume reduced significantly

---

## EXPECTED RESULT

**Before Handler Refactor:**
```
INFO: Bank handler received message
INFO: Processing Bank message in handler
INFO: Validating Bank message
INFO: Bank message validation passed
INFO: Processing data in DB
INFO: DB operation completed
INFO: Publishing message
INFO: Bank message processing completed
```
**= 8 logs per message × 100K messages = 800K logs**

**After Handler Refactor:**
```
INFO: correlationId=abc123 topic=Bank stage=VALIDATION validationResult=PASS duration=45ms
INFO: correlationId=abc123 topic=Bank stage=DB_OPERATION market=GLOBAL ... success=true
INFO: correlationId=abc123 topic=Bank stage=PUBLISHED targetTopic=bank-consumer-global ...
```
**= 3 logs per message (sampled at 1%) × 100K = 3K logs**

**Reduction: 99.6%!**

---

## REPLICATION TO OTHER HANDLERS

Once Bank handler works:

1. Copy pattern to SPCHandler, ABCHandler, etc.
2. Change NOTHING except ensuring correlationId/topic are passed
3. All handlers use same services (ValidationService, DatabaseService, etc.)
4. Services already log properly (only refactor once)

**Estimated time per handler:** 10-20 minutes
