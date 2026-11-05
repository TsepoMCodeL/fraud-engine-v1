package fraudengine.decision;

import fraudengine.domain.Action;
import fraudengine.domain.FraudDecision;
import fraudengine.domain.RiskLevel;
import fraudengine.domain.RuleResult;
import fraudengine.domain.Transaction;
import fraudengine.rules.RuleCategory;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        List<RuleResult> triggeredRules = new ArrayList<>();
        @Nullable RuleResult hardDecline = null;
        @Nullable RuleResult highestRiskRule = null;
        int aggregateScore = 0;

        for (RuleResult rule : ruleResults) {
            if (!rule.triggered()) {
                continue;
            }

            triggeredRules.add(rule);
            aggregateScore += rule.riskScore();
            if (highestRiskRule == null || rule.riskScore() > highestRiskRule.riskScore()) {
                highestRiskRule = rule;
            }
            if (hardDecline == null
                    && rule.category() == RuleCategory.HARD_BLOCK
                    && rule.recommendedAction() == Action.DECLINE) {
                hardDecline = rule;
            }
        }

        // 1. Check for immediate hard overrides
        if (thresholds.honorHardOverrides() && hardDecline != null) {
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
                    "Hard override triggered: " + hardDecline.reason()
            );
        }

        // 2. Aggregate risk scores
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
        String primaryReason = highestRiskRule == null
                ? "All security checks passed"
                : highestRiskRule.reason();

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
