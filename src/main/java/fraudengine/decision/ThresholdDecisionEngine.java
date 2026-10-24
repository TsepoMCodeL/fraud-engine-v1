package fraudengine.decision;

import fraudengine.domain.Action;
import fraudengine.domain.FraudDecision;
import fraudengine.domain.RiskLevel;
import fraudengine.domain.RuleResult;
import fraudengine.domain.Transaction;
import fraudengine.rules.RuleCategory;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Standard threshold-based decision engine implementation.
 * Evaluates hard overrides first, then computes an aggregate risk score
 * bounded between [0, 100], and maps it to Action & RiskLevel tiers.
 */
public class ThresholdDecisionEngine implements DecisionEngine {

    private final DecisionThresholds thresholds;

    public ThresholdDecisionEngine() {
        this(DecisionThresholds.defaultThresholds());
    }

    public ThresholdDecisionEngine(DecisionThresholds thresholds) {
        this.thresholds = Objects.requireNonNull(thresholds, "thresholds cannot be null");
    }

    @Override
    public FraudDecision decide(
            String evaluationId,
            Transaction transaction,
            List<RuleResult> ruleResults,
            long totalExecutionTimeNanos
    ) {
        List<RuleResult> triggeredRules = ruleResults.stream()
                .filter(ruleResult -> ruleResult.triggered())
                .toList();

        // 1. Check for immediate hard overrides
        if (thresholds.honorHardOverrides()) {
            Optional<RuleResult> hardDecline = triggeredRules.stream()
                    .filter(r -> r.category() == RuleCategory.HARD_BLOCK && r.recommendedAction() == Action.DECLINE)
                    .findFirst();

            if (hardDecline.isPresent()) {
                return new FraudDecision(
                        evaluationId,
                        transaction.id(),
                        transaction.accountId(),
                        Action.DECLINE,
                        RiskLevel.CRITICAL,
                        100,
                        triggeredRules,
                        ruleResults,
                        Instant.now(),
                        totalExecutionTimeNanos,
                        "Hard override triggered: " + hardDecline.get().reason()
                );
            }
        }

        // 2. Aggregate risk scores
        int aggregateScore = 0;
        for (RuleResult rule : triggeredRules) {
            aggregateScore += rule.riskScore();
        }
        aggregateScore = Math.min(100, Math.max(0, aggregateScore));

        // 3. Map score to Action and RiskLevel
        Action action;
        if (aggregateScore >= thresholds.declineScoreThreshold()) {
            action = Action.DECLINE;
        } else if (aggregateScore >= thresholds.reviewScoreThreshold()) {
            action = Action.REVIEW;
        } else {
            action = Action.APPROVE;
        }

        RiskLevel riskLevel = RiskLevel.fromScore(aggregateScore);

        // 4. Derive primary reason from the highest severity rule
        String primaryReason = triggeredRules.stream()
                .max(Comparator.comparingInt(ruleResult -> ruleResult.riskScore()))
                .map(ruleResult -> ruleResult.reason())
                .orElse("All security checks passed");

        return new FraudDecision(
                evaluationId,
                transaction.id(),
                transaction.accountId(),
                action,
                riskLevel,
                aggregateScore,
                triggeredRules,
                ruleResults,
                Instant.now(),
                totalExecutionTimeNanos,
                primaryReason
        );
    }
}
