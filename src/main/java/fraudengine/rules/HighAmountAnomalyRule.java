package fraudengine.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import fraudengine.domain.Action;
import fraudengine.domain.FraudEvaluationContext;
import fraudengine.domain.RuleResult;

/**
 * Behavioral anomaly rule comparing current transaction amount against
 * customer's baseline average transaction value.
 */
public class HighAmountAnomalyRule extends AbstractFraudRule {

    private static final String RULE_ID = "FR_4_AMOUNT_ANOMALY";
    private static final String RULE_NAME = "Amount Baseline Deviation Check";
    private static final int RULE_PRIORITY = 20;
    private static final BigDecimal DEFAULT_CRITICAL_MULTIPLIER = BigDecimal.TEN;
    private static final BigDecimal DEFAULT_REVIEW_MULTIPLIER = BigDecimal.valueOf(4);
    private static final BigDecimal DEFAULT_ABSOLUTE_THRESHOLD = BigDecimal.valueOf(10_000.00);
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO;
    private static final int RATIO_SCALE = 2;
    private static final int ABSOLUTE_THRESHOLD_RISK_SCORE = 50;
    private static final int CRITICAL_MULTIPLIER_RISK_SCORE = 70;
    private static final int REVIEW_MULTIPLIER_RISK_SCORE = 35;

    private final BigDecimal criticalMultiplier;
    private final BigDecimal reviewMultiplier;
    private final BigDecimal absoluteThreshold;

    public HighAmountAnomalyRule() {
        this(DEFAULT_CRITICAL_MULTIPLIER, DEFAULT_REVIEW_MULTIPLIER, DEFAULT_ABSOLUTE_THRESHOLD);
    }

    public HighAmountAnomalyRule(BigDecimal criticalMultiplier, BigDecimal reviewMultiplier, BigDecimal absoluteThreshold) {
        super(RULE_ID, RULE_NAME, RULE_PRIORITY, RuleCategory.BEHAVIORAL, false);
        this.criticalMultiplier = criticalMultiplier;
        this.reviewMultiplier = reviewMultiplier;
        this.absoluteThreshold = absoluteThreshold;
    }

    @Override
    protected RuleResult doEvaluate(FraudEvaluationContext context, long startNanos) {
        var tx = context.transaction();
        var profile = context.customerProfile();
        BigDecimal avg = profile.averageTransactionAmount();
        BigDecimal amount = tx.amount();

        // Check absolute hard threshold first
        if (amount.compareTo(absoluteThreshold) > 0 && !profile.isVip()) {
            return RuleResult.triggered(
                    id(),
                    name(),
                    category(),
                    ABSOLUTE_THRESHOLD_RISK_SCORE,
                    Action.REVIEW,
                    String.format("Transaction amount %s exceeds absolute high-value threshold %s",
                            amount, absoluteThreshold),
                    Map.of("amount", amount, "threshold", absoluteThreshold),
                    System.nanoTime() - startNanos
            );
        }

        // Baseline deviation check
        if (avg.compareTo(ZERO_AMOUNT) > 0) {
            BigDecimal ratio = amount.divide(avg, RATIO_SCALE, RoundingMode.HALF_UP);

            if (ratio.compareTo(criticalMultiplier) >= 0) {
                return RuleResult.triggered(
                        id(),
                        name(),
                        category(),
                        CRITICAL_MULTIPLIER_RISK_SCORE,
                        Action.REVIEW,
                        String.format("Transaction amount %s is %.1fx customer's average (%s)",
                                amount, ratio.doubleValue(), avg),
                        Map.of("amount", amount, "average", avg, "ratio", ratio.doubleValue()),
                        System.nanoTime() - startNanos
                );
            } else if (ratio.compareTo(reviewMultiplier) >= 0) {
                return RuleResult.triggered(
                        id(),
                        name(),
                        category(),
                        REVIEW_MULTIPLIER_RISK_SCORE,
                        Action.REVIEW,
                        String.format("Unusual transaction amount %s is %.1fx customer's average (%s)",
                                amount, ratio.doubleValue(), avg),
                        Map.of("amount", amount, "average", avg, "ratio", ratio.doubleValue()),
                        System.nanoTime() - startNanos
                );
            }
        }

        return RuleResult.passed(id(), name(), category(), System.nanoTime() - startNanos);
    }
}
