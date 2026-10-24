package fraudengine.domain;

import java.util.Objects;

/**
 * Encapsulates all state required by rules to evaluate a transaction without
 * performing external blocking I/O calls.
 */
public record FraudEvaluationContext(
        Transaction transaction,
        CustomerProfile customerProfile,
        EnrichedContext enrichment
) {
    public FraudEvaluationContext {
        Objects.requireNonNull(transaction, "transaction cannot be null");
        Objects.requireNonNull(customerProfile, "customerProfile cannot be null");
        Objects.requireNonNull(enrichment, "enrichment cannot be null");
    }
}

