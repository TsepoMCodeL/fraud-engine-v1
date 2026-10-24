package fraudengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "fraud.engine")
public class FraudEngineProperties {

    /**
     * Key prefix for all Redis keys stored by the fraud engine.
     */
    private String redisKeyPrefix = "fraud:";

    /**
     * Time-to-live in hours for account velocity sorted sets in Redis.
     */
    private int velocityTtlHours = 48;

    /**
     * Minimum composite risk score [0-100] required to route a transaction to manual review or step-up auth.
     */
    private int reviewThreshold = 40;

    /**
     * Minimum composite risk score [0-100] required to decline/block a transaction.
     */
    private int declineThreshold = 75;

    /**
     * If true, hard block rule violations (blacklists) override composite scores to decline immediately.
     */
    private boolean honorHardOverrides = true;

    /**
     * If true, halts further rule evaluation upon triggering a short-circuit decline rule.
     */
    private boolean shortCircuitEnabled = true;

    /**
     * If true, approved transactions are automatically registered to update sliding-window velocity metrics.
     */
    private boolean autoRecordTransactions = true;

    /**
     * In the event of an unrecoverable engine failure, fail open (APPROVE) or fail closed (DECLINE).
     */
    private boolean failOpenOnFatalError = false;

    private Duration maxFutureTimestampSkew = Duration.ofMinutes(5);

    // Getters & Setters
    public String getRedisKeyPrefix() { return redisKeyPrefix; }
    public void setRedisKeyPrefix(String redisKeyPrefix) { this.redisKeyPrefix = redisKeyPrefix; }

    public int getVelocityTtlHours() { return velocityTtlHours; }
    public void setVelocityTtlHours(int velocityTtlHours) { this.velocityTtlHours = velocityTtlHours; }

    public int getReviewThreshold() { return reviewThreshold; }
    public void setReviewThreshold(int reviewThreshold) { this.reviewThreshold = reviewThreshold; }

    public int getDeclineThreshold() { return declineThreshold; }
    public void setDeclineThreshold(int declineThreshold) { this.declineThreshold = declineThreshold; }

    public boolean isHonorHardOverrides() { return honorHardOverrides; }
    public void setHonorHardOverrides(boolean honorHardOverrides) { this.honorHardOverrides = honorHardOverrides; }

    public boolean isShortCircuitEnabled() { return shortCircuitEnabled; }
    public void setShortCircuitEnabled(boolean shortCircuitEnabled) { this.shortCircuitEnabled = shortCircuitEnabled; }

    public boolean isAutoRecordTransactions() { return autoRecordTransactions; }
    public void setAutoRecordTransactions(boolean autoRecordTransactions) { this.autoRecordTransactions = autoRecordTransactions; }

    public boolean isFailOpenOnFatalError() { return failOpenOnFatalError; }
    public void setFailOpenOnFatalError(boolean failOpenOnFatalError) { this.failOpenOnFatalError = failOpenOnFatalError; }

    public Duration getMaxFutureTimestampSkew() { return maxFutureTimestampSkew; }
    public void setMaxFutureTimestampSkew(Duration maxFutureTimestampSkew) {
        this.maxFutureTimestampSkew = maxFutureTimestampSkew;
    }
}
