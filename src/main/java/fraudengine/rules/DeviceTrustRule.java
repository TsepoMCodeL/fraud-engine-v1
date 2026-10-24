package fraudengine.rules;

import java.math.BigDecimal;
import java.util.Map;

import fraudengine.domain.Action;
import fraudengine.domain.FraudEvaluationContext;
import fraudengine.domain.RuleResult;

/**
 * Device trust rule checking whether the initiating hardware/browser fingerprint
 * is recognized in the customer's known trusted devices profile.
 */
public class DeviceTrustRule extends AbstractFraudRule {

    private static final String RULE_ID = "FR_2_DEVICE_TRUST";
    private static final String RULE_NAME = "Device Fingerprint Trust Verification";
    private static final int RULE_PRIORITY = 25;
    private static final BigDecimal HIGH_AMOUNT_THRESHOLD = BigDecimal.valueOf(500);
    private static final int MISSING_DEVICE_RISK_SCORE = 20;
    private static final int UNRECOGNIZED_DEVICE_RISK_SCORE = 20;
    private static final int HIGH_AMOUNT_RISK_SCORE = 35;

    public DeviceTrustRule() {
        super(RULE_ID, RULE_NAME, RULE_PRIORITY, RuleCategory.BEHAVIORAL, false);
    }

    @Override
    protected RuleResult doEvaluate(FraudEvaluationContext context, long startNanos) {
        var tx = context.transaction();
        var enrichment = context.enrichment();

        if (tx.deviceId() == null || tx.deviceId().isBlank()) {
            return RuleResult.triggered(
                    id(),
                    name(),
                    category(),
                    MISSING_DEVICE_RISK_SCORE,
                    Action.APPROVE,
                    "Transaction initiated without device fingerprint",
                    Map.of("hasDeviceFingerprint", false),
                    System.nanoTime() - startNanos
            );
        }

        if (!enrichment.isDeviceRecognized()) {
            boolean isHighAmount = tx.amount().compareTo(HIGH_AMOUNT_THRESHOLD) > 0;
            int score = isHighAmount ? HIGH_AMOUNT_RISK_SCORE : UNRECOGNIZED_DEVICE_RISK_SCORE;
            Action action = isHighAmount ? Action.REVIEW : Action.APPROVE;

            return RuleResult.triggered(
                    id(),
                    name(),
                    category(),
                    score,
                    action,
                    "New unrecognized device fingerprint: " + tx.deviceId() + (isHighAmount ? " with elevated transaction amount" : ""),
                    Map.of("deviceId", tx.deviceId(), "isDeviceRecognized", false),
                    System.nanoTime() - startNanos
            );
        }

        return RuleResult.passed(id(), name(), category(), System.nanoTime() - startNanos);
    }
}
