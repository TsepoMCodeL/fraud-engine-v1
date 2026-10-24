package fraudengine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import fraudengine.domain.*;
import fraudengine.rules.RuleCategory;
import fraudengine.rules.ShortWindowVelocityRule;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ShortWindowVelocityRuleTest {

    private final ShortWindowVelocityRule rule = new ShortWindowVelocityRule(3, 2, 3);

    @Test
    @DisplayName("Should PASS when transaction count in 60s is below warning limit")
    void shouldPassWhenLowFrequency() {
        Transaction tx = Transaction.builder()
                .id("TX-1")
                .accountId("ACC-1")
                .amount(50.00)
                .build();

        EnrichedContext enrichment = new EnrichedContext(
                1, 1, 1,
                Optional.empty(), true, false, false, false, null
        );

        FraudEvaluationContext ctx = new FraudEvaluationContext(
                tx, CustomerProfile.defaultProfile("ACC-1"), enrichment
        );

        RuleResult result = rule.evaluate(ctx);
        assertFalse(result.triggered());
        assertEquals(Action.APPROVE, result.recommendedAction());
    }

    @Test
    @DisplayName("Should trigger REVIEW when transaction count hits warning limit (2 in 60s)")
    void shouldTriggerReviewOnWarningLimit() {
        Transaction tx = Transaction.builder()
                .id("TX-2")
                .accountId("ACC-1")
                .amount(50.00)
                .timestamp(Instant.now())
                .build();

        Transaction priorTx = Transaction.builder()
                .id("TX-1")
                .accountId("ACC-1")
                .amount(50.00)
                .timestamp(Instant.now().minus(30, ChronoUnit.SECONDS))
                .build();

        EnrichedContext enrichment = new EnrichedContext(
                2, 2, 2,
                Optional.of(priorTx), true, false, false, false, null
        );

        FraudEvaluationContext ctx = new FraudEvaluationContext(
                tx, CustomerProfile.defaultProfile("ACC-1"), enrichment
        );

        RuleResult result = rule.evaluate(ctx);
        assertTrue(result.triggered());
        assertEquals(Action.REVIEW, result.recommendedAction());
        assertEquals(50, result.riskScore());
        assertEquals(RuleCategory.VELOCITY, result.category());
        assertTrue(result.reason().contains("Elevated short-window velocity"));
    }

    @Test
    @DisplayName("Should trigger DECLINE when transaction count hits burst limit (3 in 60s)")
    void shouldTriggerDeclineOnBurstLimit() {
        Transaction tx = Transaction.builder()
                .id("TX-3")
                .accountId("ACC-1")
                .amount(50.00)
                .timestamp(Instant.now())
                .build();

        Transaction priorTx = Transaction.builder()
                .id("TX-2")
                .accountId("ACC-1")
                .amount(50.00)
                .timestamp(Instant.now().minus(15, ChronoUnit.SECONDS))
                .build();

        EnrichedContext enrichment = new EnrichedContext(
                3, 3, 3,
                Optional.of(priorTx), true, false, false, false, null
        );

        FraudEvaluationContext ctx = new FraudEvaluationContext(
                tx, CustomerProfile.defaultProfile("ACC-1"), enrichment
        );

        RuleResult result = rule.evaluate(ctx);
        assertTrue(result.triggered());
        assertEquals(Action.DECLINE, result.recommendedAction());
        assertEquals(85, result.riskScore());
        assertTrue(result.reason().contains("Excessive transaction frequency"));
    }

    @Test
    @DisplayName("Should trigger DECLINE when consecutive transactions arrive with robotic sub-3-second pacing")
    void shouldTriggerDeclineOnSubThreeSecondPacing() {
        Instant now = Instant.now();

        Transaction priorTx = Transaction.builder()
                .id("TX-ROBOT-1")
                .accountId("ACC-1")
                .amount(10.00)
                .timestamp(now.minus(1, ChronoUnit.SECONDS)) // only 1 second ago!
                .build();

        Transaction currentTx = Transaction.builder()
                .id("TX-ROBOT-2")
                .accountId("ACC-1")
                .amount(10.00)
                .timestamp(now)
                .build();

        EnrichedContext enrichment = new EnrichedContext(
                1, 1, 1,
                Optional.of(priorTx), true, false, false, false, null
        );

        FraudEvaluationContext ctx = new FraudEvaluationContext(
                currentTx, CustomerProfile.defaultProfile("ACC-1"), enrichment
        );

        RuleResult result = rule.evaluate(ctx);
        assertTrue(result.triggered());
        assertEquals(Action.DECLINE, result.recommendedAction());
        assertEquals(90, result.riskScore());
        assertTrue(result.reason().contains("Automated transaction pacing detected"));
    }
}
