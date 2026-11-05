package fraudengine.domain;

import org.jspecify.annotations.Nullable;

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
    public FraudDecision(
            String evaluationId,
            String transactionId,
            String accountId,
            Action action,
            RiskLevel riskLevel,
            int aggregateRiskScore,
            @Nullable List<RuleResult> triggeredRules,
            @Nullable List<RuleResult> allEvaluatedRules,
            Instant evaluatedAt,
            long totalExecutionTimeNanos,
            @Nullable String primaryReason
    ) {
        this.evaluationId = Objects.requireNonNull(evaluationId, "evaluationId cannot be null");
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId cannot be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId cannot be null");
        this.action = Objects.requireNonNull(action, "action cannot be null");
        this.riskLevel = Objects.requireNonNull(riskLevel, "riskLevel cannot be null");
        this.aggregateRiskScore = aggregateRiskScore;
        this.triggeredRules = triggeredRules == null ? Collections.emptyList() : List.copyOf(triggeredRules);
        this.allEvaluatedRules = allEvaluatedRules == null ? Collections.emptyList() : List.copyOf(allEvaluatedRules);
        this.evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt cannot be null");
        this.totalExecutionTimeNanos = totalExecutionTimeNanos;
        this.primaryReason = primaryReason == null ? "Evaluation complete" : primaryReason;
    }

    public boolean isApproved() {
        return action == Action.APPROVE;
    }

    public double totalExecutionTimeMs() {
        return totalExecutionTimeNanos / 1_000_000.0;
    }
}
