package fraudengine.rules;

import java.util.HashMap;
import java.util.Map;

import fraudengine.domain.Action;
import fraudengine.domain.FraudEvaluationContext;
import fraudengine.domain.RuleResult;

/**
 * Hard-block rule that declines transactions if the account, IP, or device
 * is present on a global or internal blacklist.
 */
public class BlacklistRule extends AbstractFraudRule {

    private static final String RULE_ID = "FR_1_BLACKLIST";
    private static final String RULE_NAME = "Global Blacklist Check";
    private static final int RULE_PRIORITY = 1;
    private static final int DECLINE_RISK_SCORE = 100;

    public BlacklistRule() {
        super(RULE_ID, RULE_NAME, RULE_PRIORITY, RuleCategory.HARD_BLOCK, true);
    }

    @Override
    protected RuleResult doEvaluate(FraudEvaluationContext context, long startNanos) {
        var enrichment = context.enrichment();
        var tx = context.transaction();

        boolean accountHit = enrichment.isAccountBlacklisted();
        boolean ipHit = enrichment.isIpBlacklisted();
        boolean deviceHit = enrichment.isDeviceBlacklisted();

        if (accountHit || ipHit || deviceHit) {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("accountBlacklisted", accountHit);
            attributes.put("ipBlacklisted", ipHit);
            attributes.put("deviceBlacklisted", deviceHit);
            attributes.put("accountId", tx.accountId());
            attributes.put("ipAddress", tx.ipAddress());
            attributes.put("deviceId", tx.deviceId());

            String hitDetails = accountHit ? "Account is blacklisted" :
                               (ipHit ? "IP address is blacklisted" : "Device is blacklisted");

            return RuleResult.triggered(
                    id(),
                    name(),
                    category(),
                    DECLINE_RISK_SCORE,
                    Action.DECLINE,
                    "Terminal entity match: " + hitDetails,
                    attributes,
                    System.nanoTime() - startNanos
            );
        }

        return RuleResult.passed(id(), name(), category(), System.nanoTime() - startNanos);
    }
}
