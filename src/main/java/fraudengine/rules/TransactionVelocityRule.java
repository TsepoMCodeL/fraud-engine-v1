package fraudengine.rules;

import java.util.Map;

import fraudengine.domain.Action;
import fraudengine.domain.FraudEvaluationContext;
import fraudengine.domain.RuleResult;

/**
 * Velocity rule detecting rapid bursts of transactions indicative of card testing or automated abuse.
 */
public class TransactionVelocityRule extends AbstractFraudRule {

    private static final String RULE_ID = "FR_6_BURST_VELOCITY";
    private static final String RULE_NAME = "Short-term Transaction Velocity Check";
    private static final int RULE_PRIORITY = 10;
    private static final int DEFAULT_BURST_LIMIT = 5;
    private static final int DEFAULT_REVIEW_LIMIT = 3;
    private static final int BURST_RISK_SCORE = 80;
    private static final int REVIEW_RISK_SCORE = 45;
    private static final int WINDOW_MINUTES = 10;

    private final int burstLimit;
    private final int reviewLimit;

    public TransactionVelocityRule() {
        this(DEFAULT_BURST_LIMIT, DEFAULT_REVIEW_LIMIT);
    }

    public TransactionVelocityRule(int burstLimit, int reviewLimit) {
        super(RULE_ID, RULE_NAME, RULE_PRIORITY, RuleCategory.VELOCITY, false);
        this.burstLimit = burstLimit;
        this.reviewLimit = reviewLimit;
    }

    @Override
    protected RuleResult doEvaluate(FraudEvaluationContext context, long startNanos) {
        int txCount10m = context.enrichment().transactionsLast10Minutes();

        if (txCount10m >= burstLimit) {
            return RuleResult.triggered(
                    id(),
                    name(),
                    category(),
                    BURST_RISK_SCORE,
                    Action.DECLINE,
                    String.format("Critical transaction burst: %d transactions in last %d minutes (limit: %d)",
                            txCount10m, WINDOW_MINUTES, burstLimit),
                    Map.of("txCount10m", txCount10m, "limit", burstLimit),
                    System.nanoTime() - startNanos
            );
        } else if (txCount10m >= reviewLimit) {
            return RuleResult.triggered(
                    id(),
                    name(),
                    category(),
                    REVIEW_RISK_SCORE,
                    Action.REVIEW,
                    String.format("Elevated transaction frequency: %d transactions in last %d minutes (warning: %d)",
                            txCount10m, WINDOW_MINUTES, reviewLimit),
                    Map.of("txCount10m", txCount10m, "limit", reviewLimit),
                    System.nanoTime() - startNanos
            );
        }

        return RuleResult.passed(id(), name(), category(), System.nanoTime() - startNanos);
    }
}
