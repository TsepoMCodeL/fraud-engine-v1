package fraudengine.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable aggregated decision emitted by the fraud engine.
 */
public record FraudDecision(
        String evaluationId,
        String transactionId,
        String accountId,
        Action action,
        RiskLevel riskLevel,
        int aggregateRiskScore,
        List<RuleResult> triggeredRules,
        List<RuleResult> allEvaluatedRules,
        Instant evaluatedAt,
        long totalExecutionTimeNanos,
        String primaryReason
) {
    public FraudDecision {
        Objects.requireNonNull(evaluationId, "evaluationId cannot be null");
        Objects.requireNonNull(transactionId, "transactionId cannot be null");
        Objects.requireNonNull(accountId, "accountId cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(riskLevel, "riskLevel cannot be null");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt cannot be null");

        triggeredRules = triggeredRules == null ? Collections.emptyList() : List.copyOf(triggeredRules);
        allEvaluatedRules = allEvaluatedRules == null ? Collections.emptyList() : List.copyOf(allEvaluatedRules);
        primaryReason = primaryReason == null ? "Evaluation complete" : primaryReason;
    }

    public boolean isApproved() {
        return action == Action.APPROVE;
    }

    public double totalExecutionTimeMs() {
        return totalExecutionTimeNanos / 1_000_000.0;
    }
}
