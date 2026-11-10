package fraudengine.domain;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
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
    public EnrichedContext(
            int transactionsLast1Minute,
            int transactionsLast10Minutes,
            int transactionsLast24Hours,
            Optional<Transaction> lastKnownTransaction,
            boolean isDeviceRecognized,
            boolean isAccountBlacklisted,
            boolean isIpBlacklisted,
            boolean isDeviceBlacklisted,
            @Nullable Map<String, Object> customFeatures
    ) {
        this.transactionsLast1Minute = transactionsLast1Minute;
        this.transactionsLast10Minutes = transactionsLast10Minutes;
        this.transactionsLast24Hours = transactionsLast24Hours;
        this.lastKnownTransaction = lastKnownTransaction == null ? Optional.empty() : lastKnownTransaction;
        this.isDeviceRecognized = isDeviceRecognized;
        this.isAccountBlacklisted = isAccountBlacklisted;
        this.isIpBlacklisted = isIpBlacklisted;
        this.isDeviceBlacklisted = isDeviceBlacklisted;
        this.customFeatures = customFeatures == null || customFeatures.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(customFeatures));
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
                Map.of()
        );
    }
}
