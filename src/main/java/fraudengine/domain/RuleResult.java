package fraudengine.domain;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import fraudengine.rules.RuleCategory;

/**
 * Output produced by evaluating an individual rule against a transaction context.
 */
@NullMarked
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
    public RuleResult(
            String ruleId,
            String ruleName,
            RuleCategory category,
            boolean triggered,
            int riskScore,
            Action recommendedAction,
            String reason,
            @Nullable Map<String, Object> attributes,
            long executionTimeNanos
    ) {
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId cannot be null");
        this.ruleName = Objects.requireNonNull(ruleName, "ruleName cannot be null");
        this.category = Objects.requireNonNull(category, "category cannot be null");
        this.triggered = triggered;
        this.riskScore = riskScore;
        this.recommendedAction = Objects.requireNonNull(recommendedAction, "recommendedAction cannot be null");
        this.reason = reason;
        this.attributes = attributes == null || attributes.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        this.executionTimeNanos = executionTimeNanos;
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
                Map.of(),
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
