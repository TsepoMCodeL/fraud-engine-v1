package fraudengine.rules;

import java.time.Duration;
import java.util.Map;

import fraudengine.domain.Action;
import fraudengine.domain.FraudEvaluationContext;
import fraudengine.domain.RuleResult;

/**
 * Geographic anomaly rule detecting physically impossible travel speeds
 * between consecutive transactions on the same account.
 */
public class GeoVelocityAnomalyRule extends AbstractFraudRule {

    private static final String RULE_ID = "FR_3_IMPOSSIBLE_TRAVEL";
    private static final String RULE_NAME = "Impossible Travel Speed Check";
    private static final int RULE_PRIORITY = 15;
    private static final double DEFAULT_MAX_SPEED_KMH = 900;
    private static final double GPS_JITTER_DISTANCE_KM = 20;
    private static final double MIN_TRAVEL_TIME_HOURS = 0.001;
    private static final double MILLIS_PER_HOUR = Duration.ofHours(1).toMillis();
    private static final double INFINITE_SPEED_DISPLAY_KMH = 999_999;
    private static final int IMPOSSIBLE_TRAVEL_RISK_SCORE = 85;

    private final double maxSpeedKmh;

    public GeoVelocityAnomalyRule() {
        this(DEFAULT_MAX_SPEED_KMH);
    }

    public GeoVelocityAnomalyRule(double maxSpeedKmh) {
        super(RULE_ID, RULE_NAME, RULE_PRIORITY, RuleCategory.GEOGRAPHIC, false);
        this.maxSpeedKmh = maxSpeedKmh;
    }

    @Override
    protected RuleResult doEvaluate(FraudEvaluationContext context, long startNanos) {
        var tx = context.transaction();
        var lastTxOpt = context.enrichment().lastKnownTransaction();

        if (lastTxOpt.isEmpty() || tx.location() == null || lastTxOpt.get().location() == null) {
            return RuleResult.passed(id(), name(), category(), System.nanoTime() - startNanos);
        }

        var lastTx = lastTxOpt.get();
        double distanceKm = tx.location().distanceKmTo(lastTx.location());

        // Ignore small distances (< 20 km) to prevent GPS jitter false positives
        if (distanceKm < GPS_JITTER_DISTANCE_KM) {
            return RuleResult.passed(id(), name(), category(), System.nanoTime() - startNanos);
        }

        Duration elapsed = Duration.between(lastTx.timestamp(), tx.timestamp()).abs();
        double hours = elapsed.toMillis() / MILLIS_PER_HOUR;

        // Guard against simultaneous transactions in different locations (hours close to 0)
        double speedKmh = hours <= MIN_TRAVEL_TIME_HOURS ? Double.POSITIVE_INFINITY : (distanceKm / hours);

        if (speedKmh > maxSpeedKmh) {
            String reason = String.format(
                    "Impossible travel detected: %.1f km traversed in %d minutes (Implied speed: %.1f km/h > threshold: %.1f km/h)",
                    distanceKm, elapsed.toMinutes(), speedKmh, maxSpeedKmh);

            Map<String, Object> attrs = Map.of(
                    "distanceKm", Math.round(distanceKm),
                    "elapsedMinutes", elapsed.toMinutes(),
                    "calculatedSpeedKmh", Double.isInfinite(speedKmh) ? INFINITE_SPEED_DISPLAY_KMH : Math.round(speedKmh),
                    "currentCountry", tx.location().countryCode(),
                    "previousCountry", lastTx.location().countryCode()
            );

            return RuleResult.triggered(
                    id(),
                    name(),
                    category(),
                    IMPOSSIBLE_TRAVEL_RISK_SCORE,
                    Action.DECLINE,
                    reason,
                    attrs,
                    System.nanoTime() - startNanos
            );
        }

        return RuleResult.passed(id(), name(), category(), System.nanoTime() - startNanos);
    }
}
