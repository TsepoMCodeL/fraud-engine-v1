package fraudengine.rules;

import fraudengine.domain.FraudEvaluationContext;
import fraudengine.domain.RuleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base template implementation providing execution timing and exception isolation.
 */
public abstract class AbstractFraudRule implements FraudRule {

    private static final Logger log = LoggerFactory.getLogger(AbstractFraudRule.class);

    private final String id;
    private final String name;
    private final int priority;
    private final RuleCategory category;
    private final boolean shortCircuit;

    protected AbstractFraudRule(String id, String name, int priority, RuleCategory category, boolean shortCircuit) {
        this.id = id;
        this.name = name;
        this.priority = priority;
        this.category = category;
        this.shortCircuit = shortCircuit;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public RuleCategory category() {
        return category;
    }

    @Override
    public boolean isShortCircuit() {
        return shortCircuit;
    }

    @Override
    public final RuleResult evaluate(FraudEvaluationContext context) {
        long start = System.nanoTime();
        try {
            return doEvaluate(context, start);
        } catch (Exception ex) {
            long duration = System.nanoTime() - start;
            log.error("Fraud rule {} failed; declining transaction", id, ex);
            return RuleResult.error(id, name, category, ex, duration);
        }
    }

    /**
     * Subclasses implement their rule evaluation logic here.
     */
    protected abstract RuleResult doEvaluate(FraudEvaluationContext context, long startNanos);
}
