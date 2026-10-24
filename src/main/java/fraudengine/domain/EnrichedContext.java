package fraudengine.domain;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Real-time dynamic features enriched by the feature store prior to rule evaluation.
 */
public record EnrichedContext(
        int transactionsLast1Minute,
        int transactionsLast10Minutes,
        int transactionsLast24Hours,
        Optional<Transaction> lastKnownTransaction,
        boolean isDeviceRecognized,
        boolean isAccountBlacklisted,
        boolean isIpBlacklisted,
        boolean isDeviceBlacklisted,
        Map<String, Object> customFeatures
) {
    public EnrichedContext {
        lastKnownTransaction = lastKnownTransaction == null ? Optional.empty() : lastKnownTransaction;
        customFeatures = customFeatures == null ? Collections.emptyMap() : Collections.unmodifiableMap(customFeatures);
    }

    public static EnrichedContext empty() {
        return new EnrichedContext(
                0,
                0,
                0,
                Optional.empty(),
                false,
                false,
                false,
                false,
                Collections.emptyMap()
        );
    }
}
