package fraudengine.rules;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import fraudengine.domain.Action;
import fraudengine.domain.FraudEvaluationContext;
import fraudengine.domain.RuleResult;

/**
 * Velocity rule detecting high-frequency transaction bursts within an ultra-short window (60 seconds)
 * and rapid sub-second / automated robotic scripting attacks.
 */
public class ShortWindowVelocityRule extends AbstractFraudRule {

    private static final String RULE_ID = "FR_5_SHORT_WINDOW_VELOCITY";
    private static final String RULE_NAME = "Short-Window High-Frequency Velocity Check";
    private static final int RULE_PRIORITY = 8;
    private static final int DEFAULT_BURST_LIMIT = 3;
    private static final int DEFAULT_WARNING_LIMIT = 2;
    private static final long DEFAULT_MIN_INTERVAL_SECONDS = 3;
    private static final int PACING_RISK_SCORE = 90;
    private static final int BURST_RISK_SCORE = 85;
    private static final int WARNING_RISK_SCORE = 50;
    private static final long WINDOW_SECONDS = 60;

    private final int burstLimit;
    private final int warningLimit;
    private final long minIntervalSeconds;

    public ShortWindowVelocityRule() {
        this(DEFAULT_BURST_LIMIT, DEFAULT_WARNING_LIMIT, DEFAULT_MIN_INTERVAL_SECONDS);
    }

    public ShortWindowVelocityRule(int burstLimit, int warningLimit, long minIntervalSeconds) {
        super(RULE_ID, RULE_NAME, RULE_PRIORITY, RuleCategory.VELOCITY, false);
        this.burstLimit = burstLimit;
        this.warningLimit = warningLimit;
        this.minIntervalSeconds = minIntervalSeconds;
    }

    @Override
    protected RuleResult doEvaluate(FraudEvaluationContext context, long startNanos) {
        var enrichment = context.enrichment();
        var tx = context.transaction();

        int count1m = enrichment.transactionsLast1Minute();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("transactionsLast1Minute", count1m);
        attributes.put("burstLimit", burstLimit);
        attributes.put("warningLimit", warningLimit);

        // 1. Rapid scripted replay check (sub-second or sub-threshold time gap since last transaction)
        if (enrichment.lastKnownTransaction().isPresent()) {
            var lastTx = enrichment.lastKnownTransaction().get();
            long secondsSinceLast = Duration.between(lastTx.timestamp(), tx.timestamp()).abs().toSeconds();
            attributes.put("secondsSinceLastTransaction", secondsSinceLast);

            if (secondsSinceLast < minIntervalSeconds) {
                return RuleResult.triggered(
                        id(),
                        name(),
                        category(),
                        PACING_RISK_SCORE,
                        Action.DECLINE,
                        String.format("Automated transaction pacing detected: only %d second(s) since last transaction (min allowed: %ds)",
                                secondsSinceLast, minIntervalSeconds),
                        attributes,
                        System.nanoTime() - startNanos
                );
            }
        }

        // 2. Short-window frequency burst check (60-second window)
        if (count1m >= burstLimit) {
            return RuleResult.triggered(
                    id(),
                    name(),
                    category(),
                    BURST_RISK_SCORE,
                    Action.DECLINE,
                    String.format("Excessive transaction frequency: %d transactions in %d seconds (burst limit: %d)",
                            count1m, WINDOW_SECONDS, burstLimit),
                    attributes,
                    System.nanoTime() - startNanos
            );
        } else if (count1m >= warningLimit) {
            return RuleResult.triggered(
                    id(),
                    name(),
                    category(),
                    WARNING_RISK_SCORE,
                    Action.REVIEW,
                    String.format("Elevated short-window velocity: %d transactions in %d seconds (warning threshold: %d)",
                            count1m, WINDOW_SECONDS, warningLimit),
                    attributes,
                    System.nanoTime() - startNanos
            );
        }

        return RuleResult.passed(id(), name(), category(), System.nanoTime() - startNanos);
    }
}
