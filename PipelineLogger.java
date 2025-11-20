package com.yourcompany.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralized logging utility for pipeline operations.
 * Thread-safe and optimized for high-throughput multi-topic scenarios (100K+ messages/topic).
 * 
 * Key Features:
 * - Per-topic sampling (ensures fair logging across all 15+ topics)
 * - Automatic slow query detection
 * - Structured JSON logging for OpenSearch
 * - Built-in performance thresholds
 * 
 * Usage: Import and call static methods at each pipeline stage
 * Example: PipelineLogger.logConsumed(topic, correlationId, messageId, partition, offset);
 */
public class PipelineLogger {
    
    private static final Logger logger = LoggerFactory.getLogger("PIPELINE_METRICS");
    
    // Per-topic sampling counters (thread-safe)
    private static final ConcurrentHashMap<String, AtomicLong> topicCounters = new ConcurrentHashMap<>();
    
    // Sampling configuration (1% = log 1 out of 100 per topic)
    private static final int SAMPLE_RATE = 100;
    
    // Performance thresholds (milliseconds)
    private static final long SLOW_QUERY_THRESHOLD = 500;
    private static final long SLOW_TABLE_THRESHOLD = 300;
    private static final long SLOW_PIPELINE_THRESHOLD = 2000;
    
    /**
     * Get or create counter for a specific topic (thread-safe)
     */
    private static AtomicLong getTopicCounter(String topic) {
        return topicCounters.computeIfAbsent(topic, k -> new AtomicLong(0));
    }
    
    /**
     * Per-topic sampling - ensures each topic gets fair sampling
     */
    private static boolean shouldSample(String topic) {
        AtomicLong counter = getTopicCounter(topic);
        return counter.incrementAndGet() % SAMPLE_RATE == 0;
    }
    
    /**
     * Log message consumption from Kafka (SAMPLED per topic)
     */
    public static void logConsumed(String topic, String correlationId, String messageId, 
                                    int partition, long offset) {
        if (shouldSample(topic)) {
            logger.info("correlationId={} topic={} stage=CONSUMED messageId={} partition={} offset={} timestamp={}", 
                correlationId, topic, messageId, partition, offset, System.currentTimeMillis());
        }
    }
    
    /**
     * Log successful validation (SAMPLED per topic)
     */
    public static void logValidationSuccess(String correlationId, String topic, long durationMs) {
        if (shouldSample(topic)) {
            logger.info("correlationId={} topic={} stage=VALIDATION validationResult=PASS duration={}ms",
                correlationId, topic, durationMs);
        }
    }
    
    /**
     * Log validation failure (ALWAYS logged - not sampled)
     */
    public static void logValidationFailure(String correlationId, String topic, String validationError, 
                                           String validationRule, boolean willRetry, String retryTopic, 
                                           long durationMs) {
        logger.warn("correlationId={} topic={} stage=VALIDATION validationResult=FAIL validationError='{}' " +
                   "validationRule={} willRetry={} retryTopic={} duration={}ms",
            correlationId, topic, validationError, validationRule, willRetry, retryTopic, durationMs);
    }
    
    /**
     * Log market classification (SAMPLED per topic)
     */
    public static void logMarketClassification(String correlationId, String topic, String market, 
                                              String recordType) {
        if (shouldSample(topic)) {
            logger.info("correlationId={} topic={} stage=MARKET_CLASSIFICATION market={} recordType={}",
                correlationId, topic, market, recordType);
        }
    }
    
    /**
     * Log database operation
     * - WARN if slow (>500ms)
     * - ERROR if failed
     * - INFO if success (sampled per topic)
     */
    public static void logDbOperation(String correlationId, String topic, String market, 
                                     String dbOperation, String tablesAffected, int recordsAffected, 
                                     long queryDurationMs, boolean success, String errorMessage) {
        if (!success) {
            logger.error("correlationId={} topic={} stage=DB_OPERATION market={} dbOperation={} " +
                        "tablesAffected={} recordsAffected={} queryDuration={}ms success=false errorMessage='{}'",
                correlationId, topic, market, dbOperation, tablesAffected, recordsAffected, 
                queryDurationMs, errorMessage);
        } else if (queryDurationMs > SLOW_QUERY_THRESHOLD) {
            logger.warn("correlationId={} topic={} stage=DB_OPERATION market={} dbOperation={} " +
                       "tablesAffected={} recordsAffected={} queryDuration={}ms success=true " +
                       "performanceIssue=SLOW_QUERY threshold={}ms",
                correlationId, topic, market, dbOperation, tablesAffected, recordsAffected, 
                queryDurationMs, SLOW_QUERY_THRESHOLD);
        } else if (shouldSample(topic)) {
            logger.info("correlationId={} topic={} stage=DB_OPERATION market={} dbOperation={} " +
                       "tablesAffected={} recordsAffected={} queryDuration={}ms success=true",
                correlationId, topic, market, dbOperation, tablesAffected, recordsAffected, queryDurationMs);
        }
    }
    
    /**
     * Log individual table operation (ONLY if slow - >300ms)
     */
    public static void logDbOperationDetail(String correlationId, String topic, String market, 
                                           String tableName, String operation, int rowsAffected, 
                                           long durationMs) {
        if (durationMs > SLOW_TABLE_THRESHOLD) {
            logger.warn("correlationId={} topic={} stage=DB_OPERATION_DETAIL market={} tableName={} " +
                       "operation={} rowsAffected={} duration={}ms performanceIssue=SLOW_TABLE_OPERATION " +
                       "threshold={}ms",
                correlationId, topic, market, tableName, operation, rowsAffected, durationMs, 
                SLOW_TABLE_THRESHOLD);
        }
    }
    
    /**
     * Log Kafka message publishing
     * - SAMPLED per topic for success
     * - ALWAYS logged for failure
     */
    public static void logPublished(String correlationId, String sourceTopic, String market, 
                                   String targetTopic, boolean success, int publishAttempt, 
                                   long durationMs, String errorMessage) {
        if (!success) {
            logger.error("correlationId={} topic={} stage=PUBLISHED market={} targetTopic={} " +
                        "publishSuccess=false publishAttempt={} duration={}ms errorMessage='{}'",
                correlationId, sourceTopic, market, targetTopic, publishAttempt, durationMs, errorMessage);
        } else if (shouldSample(sourceTopic)) {
            logger.info("correlationId={} topic={} stage=PUBLISHED market={} targetTopic={} " +
                       "publishSuccess=true publishAttempt={} duration={}ms",
                correlationId, sourceTopic, market, targetTopic, publishAttempt, durationMs);
        }
    }
    
    /**
     * Log retry attempt (ALWAYS logged)
     */
    public static void logRetry(String correlationId, String originalTopic, String failedStage, 
                               String retryTopic, int attemptNumber, String reason, int maxRetries) {
        logger.warn("correlationId={} topic={} stage=RETRY originalStage={} retryTopic={} " +
                   "retryAttempt={} retryReason='{}' maxRetries={}",
            correlationId, originalTopic, failedStage, retryTopic, attemptNumber, reason, maxRetries);
    }
    
    /**
     * Log retry exhausted (ALWAYS logged)
     */
    public static void logRetryExhausted(String correlationId, String originalTopic, String failedStage, 
                                        String retryTopic, int attemptNumber, int maxRetries, String reason) {
        logger.error("correlationId={} topic={} stage=RETRY_EXHAUSTED originalStage={} retryTopic={} " +
                    "retryAttempt={} maxRetries={} finalStatus=FAILED reason='{}'",
            correlationId, originalTopic, failedStage, retryTopic, attemptNumber, maxRetries, reason);
    }
    
    /**
     * Log end-to-end pipeline completion
     * - SAMPLED per topic for fast success
     * - ALWAYS logged if slow or failed
     */
    public static void logPipelineComplete(String correlationId, String topic, String market, 
                                          long totalDurationMs, String stagesCompleted, boolean success) {
        String finalStatus = success ? "SUCCESS" : "FAILED";
        
        if (!success) {
            logger.error("correlationId={} topic={} stage=PIPELINE_COMPLETE market={} totalDuration={}ms " +
                        "stagesCompleted={} finalStatus={}",
                correlationId, topic, market, totalDurationMs, stagesCompleted, finalStatus);
        } else if (totalDurationMs > SLOW_PIPELINE_THRESHOLD) {
            logger.warn("correlationId={} topic={} stage=PIPELINE_COMPLETE market={} totalDuration={}ms " +
                       "stagesCompleted={} finalStatus={} performanceIssue=SLOW_PIPELINE threshold={}ms",
                correlationId, topic, market, totalDurationMs, stagesCompleted, finalStatus, 
                SLOW_PIPELINE_THRESHOLD);
        } else if (shouldSample(topic)) {
            logger.info("correlationId={} topic={} stage=PIPELINE_COMPLETE market={} totalDuration={}ms " +
                       "stagesCompleted={} finalStatus={}",
                correlationId, topic, market, totalDurationMs, stagesCompleted, finalStatus);
        }
    }
    
    /**
     * Log critical errors (ALWAYS logged)
     */
    public static void logError(String correlationId, String topic, String stage, String errorMessage, 
                               Throwable throwable) {
        logger.error("correlationId={} topic={} stage={} errorType=CRITICAL errorMessage='{}'",
            correlationId, topic, stage, errorMessage, throwable);
    }
}
