package fraudengine.rules;

import fraudengine.domain.FraudEvaluationContext;
import fraudengine.domain.RuleResult;

/**
 * Contract for all fraud rules in the engine.
 * Rules must be thread-safe, stateless, and idempotent.
 */
public interface FraudRule extends Comparable<FraudRule> {

    /**
     * Unique identifier for the rule (e.g. "R-101-BLACKLIST").
     */
    String id();

    /**
     * Human-readable rule name.
     */
    String name();

    /**
     * Priority determines execution order (lower value = executed earlier).
     */
    int priority();

    /**
     * Category of the rule.
     */
    RuleCategory category();

    /**
     * Whether this rule is currently enabled.
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * If true and this rule triggers with Action.DECLINE, the engine can
     * immediately halt further rule evaluation to conserve CPU and latency.
     */
    default boolean isShortCircuit() {
        return false;
    }

    /**
     * Evaluates the given enriched context.
     *
     * @param context Pre-enriched transaction context.
     * @return Result of the evaluation.
     */
    RuleResult evaluate(FraudEvaluationContext context);

    @Override
    default int compareTo(FraudRule other) {
        return Integer.compare(this.priority(), other.priority());
    }
}

