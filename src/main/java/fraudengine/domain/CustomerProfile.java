package fraudengine.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Historical customer profile baseline for anomaly detection.
 */
public record CustomerProfile(
        String accountId,
        Instant accountCreatedAt,
        BigDecimal averageTransactionAmount,
        int trustScore,
        Set<String> knownDeviceIds,
        boolean isVip
) {
    public CustomerProfile {
        Objects.requireNonNull(accountId, "accountId cannot be null");
        accountCreatedAt = accountCreatedAt == null ? Instant.now() : accountCreatedAt;
        averageTransactionAmount = averageTransactionAmount == null ? BigDecimal.ZERO : averageTransactionAmount;
        knownDeviceIds = knownDeviceIds == null ? Collections.emptySet() : Collections.unmodifiableSet(knownDeviceIds);
    }

    public static CustomerProfile defaultProfile(String accountId) {
        return new CustomerProfile(
                accountId,
                Instant.now(),
                BigDecimal.valueOf(100.00),
                50,
                Collections.emptySet(),
                false
        );
    }
}

