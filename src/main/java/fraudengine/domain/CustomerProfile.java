package fraudengine.domain;

import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Historical customer profile baseline for anomaly detection.
 */
@NullMarked
public record CustomerProfile(
        String accountId,
        Instant accountCreatedAt,
        BigDecimal averageTransactionAmount,
        int trustScore,
        Set<String> knownDeviceIds,
        boolean isVip
) {
    public static final int DEFAULT_TRUST_SCORE = 50;
    public static final BigDecimal DEFAULT_AVERAGE_TRANSACTION_AMOUNT = new BigDecimal("100.00");

    public CustomerProfile {
        Objects.requireNonNull(accountId, "accountId cannot be null");
        if (accountId.isBlank()) {
            throw new IllegalArgumentException("accountId cannot be blank");
        }
        Objects.requireNonNull(accountCreatedAt, "accountCreatedAt cannot be null");
        Objects.requireNonNull(averageTransactionAmount, "averageTransactionAmount cannot be null");
        if (averageTransactionAmount.signum() < 0) {
            throw new IllegalArgumentException("averageTransactionAmount cannot be negative");
        }
        if (trustScore < 0 || trustScore > 100) {
            throw new IllegalArgumentException("trustScore out of range: " + trustScore);
        }
        knownDeviceIds = Set.copyOf(knownDeviceIds);
    }

    public static CustomerProfile defaultProfile(String accountId, Clock clock) {
        return new CustomerProfile(
                accountId, clock.instant(), DEFAULT_AVERAGE_TRANSACTION_AMOUNT,
                DEFAULT_TRUST_SCORE, Set.of(), false);
    }

    public static CustomerProfile defaultProfile(String accountId) {
        return defaultProfile(accountId, Clock.systemUTC());
    }
}
