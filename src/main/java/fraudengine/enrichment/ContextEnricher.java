package fraudengine.enrichment;

import fraudengine.domain.CustomerProfile;
import fraudengine.domain.EnrichedContext;
import fraudengine.domain.Transaction;

/**
 * Port contract for pre-enrichment.
 * Implementations aggregate real-time state (Redis/DynamoDB/In-Memory)
 * before rules are executed.
 */
public interface ContextEnricher {

    /**
     * Enriches context for a given incoming transaction and profile.
     */
    EnrichedContext enrich(Transaction transaction, CustomerProfile profile);

    /**
     * Records an approved or processed transaction to update real-time velocity metrics.
     */
    void recordTransaction(Transaction transaction);

    /**
     * Reports whether the backing state store is available for evaluation.
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * Adds an account ID to the fraud blacklist.
     */
    default void addBlacklistedAccount(String accountId) {
        throw new UnsupportedOperationException("Account blacklisting is not supported");
    }

    /**
     * Adds an IP address to the fraud blacklist.
     */
    default void addBlacklistedIp(String ipAddress) {
        throw new UnsupportedOperationException("IP blacklisting is not supported");
    }

    /**
     * Adds a device ID to the fraud blacklist.
     */
    default void addBlacklistedDevice(String deviceId) {
        throw new UnsupportedOperationException("Device blacklisting is not supported");
    }

    /**
     * Clears all cached/stored state (for testing or administrative resets).
     */
    default void clear() {}
}
