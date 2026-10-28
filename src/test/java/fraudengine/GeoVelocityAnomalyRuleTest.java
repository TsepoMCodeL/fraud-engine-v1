package fraudengine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import fraudengine.domain.*;
import fraudengine.rules.GeoVelocityAnomalyRule;
import fraudengine.rules.RuleCategory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GeoVelocityAnomalyRuleTest {

    private final GeoVelocityAnomalyRule rule = new GeoVelocityAnomalyRule(900.0);

    @Test
    @DisplayName("Should pass when no prior transaction exists")
    void shouldPassWhenNoPriorTransaction() {
        Transaction current = Transaction.builder()
                .id("TX-1")
                .accountId("ACC-1")
                .amount(50.0)
                .location(new GeoLocation(40.7128, -74.0060, "ZA"))
                .build();

        FraudEvaluationContext ctx = new FraudEvaluationContext(
                current,
                CustomerProfile.defaultProfile("ACC-1"),
                EnrichedContext.empty()
        );

        RuleResult result = rule.evaluate(ctx);
        assertFalse(result.triggered());
        assertEquals(Action.APPROVE, result.recommendedAction());
    }

    @Test
    @DisplayName("Should trigger DECLINE when distance requires supersonic speed (> 900 km/h)")
    void shouldTriggerWhenSpeedExceedsCommercialFlight() {
        Instant now = Instant.now();

        Transaction past = Transaction.builder()
                .id("TX-PAST")
                .accountId("ACC-1")
                .amount(20.0)
                .timestamp(now.minus(20, ChronoUnit.MINUTES))
                .location(new GeoLocation(51.5074, -0.1278, "GB")) // London
                .build();

        Transaction current = Transaction.builder()
                .id("TX-CURRENT")
                .accountId("ACC-1")
                .amount(80.0)
                .timestamp(now)
                .location(new GeoLocation(40.7128, -74.0060, "US")) // New York (~5,570 km away in 20 min)
                .build();

        EnrichedContext enrichment = new EnrichedContext(
                1, 1, 1, Optional.of(past), true, false, false, false, null
        );

        FraudEvaluationContext ctx = new FraudEvaluationContext(
                current,
                CustomerProfile.defaultProfile("ACC-1"),
                enrichment
        );

        RuleResult result = rule.evaluate(ctx);
        assertTrue(result.triggered());
        assertEquals(Action.DECLINE, result.recommendedAction());
        assertEquals(RuleCategory.GEOGRAPHIC, result.category());
        assertTrue(result.riskScore() >= 80);
    }
}
