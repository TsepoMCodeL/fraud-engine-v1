package fraudengine.domain;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import fraudengine.rules.RuleCategory;

/**
 * Output produced by evaluating an individual rule against a transaction context.
 */
public record RuleResult(
        String ruleId,
        String ruleName,
        RuleCategory category,
        boolean triggered,
        int riskScore,
        Action recommendedAction,
        String reason,
        Map<String, Object> attributes,
        long executionTimeNanos
) {
    public RuleResult {
        Objects.requireNonNull(ruleId, "ruleId cannot be null");
        Objects.requireNonNull(ruleName, "ruleName cannot be null");
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(recommendedAction, "recommendedAction cannot be null");
        attributes = attributes == null ? Collections.emptyMap() : Collections.unmodifiableMap(attributes);
    }

    public static RuleResult passed(String ruleId, String ruleName, RuleCategory category, long executionNanos) {
        return new RuleResult(
                ruleId,
                ruleName,
                category,
                false,
                0,
                Action.APPROVE,
                "Rule conditions not met (clean)",
                Collections.emptyMap(),
                executionNanos
        );
    }

    public static RuleResult triggered(
            String ruleId,
            String ruleName,
            RuleCategory category,
            int riskScore,
            Action recommendedAction,
            String reason,
            Map<String, Object> attributes,
            long executionNanos
    ) {
        return new RuleResult(
                ruleId,
                ruleName,
                category,
                true,
                riskScore,
                recommendedAction,
                reason,
                attributes,
                executionNanos
        );
    }

    public static RuleResult error(
            String ruleId,
            String ruleName,
            RuleCategory category,
            Throwable ex,
            long executionNanos
    ) {
        return new RuleResult(
                ruleId,
                ruleName,
                category,
                true,
                100,
                Action.DECLINE,
                "Rule execution failed; transaction declined",
                Map.of("error", ex.getClass().getSimpleName()),
                executionNanos
        );
    }
}
