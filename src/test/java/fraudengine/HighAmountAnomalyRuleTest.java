package fraudengine;

import fraudengine.domain.Action;
import fraudengine.domain.CustomerProfile;
import fraudengine.domain.EnrichedContext;
import fraudengine.domain.FraudEvaluationContext;
import fraudengine.domain.RuleResult;
import fraudengine.domain.Transaction;
import fraudengine.rules.HighAmountAnomalyRule;
import fraudengine.rules.RuleCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighAmountAnomalyRuleTest {

    private final HighAmountAnomalyRule rule = new HighAmountAnomalyRule();

    @Test
    @DisplayName("Should trigger critical multiplier (score 70) when amount is 500x average even if exceeding absolute threshold")
    void shouldTriggerCriticalMultiplierWhenFarExceedingAverageEvenIfAboveAbsoluteThreshold() {
        CustomerProfile profile = new CustomerProfile(
                "ACC-1",
                Instant.now(),
                BigDecimal.valueOf(100.00),
                50,
                Set.of(),
                false
        );
        Transaction tx = Transaction.builder()
                .id("TX-1")
                .accountId("ACC-1")
                .amount(50_000.00)
                .build();
        FraudEvaluationContext ctx = new FraudEvaluationContext(tx, profile, EnrichedContext.empty());

        RuleResult result = rule.evaluate(ctx);

        assertTrue(result.triggered());
        assertEquals(70, result.riskScore(), "500x deviation must trigger critical multiplier score 70, not absolute threshold score 50");
        assertEquals(Action.REVIEW, result.recommendedAction());
        assertEquals(RuleCategory.BEHAVIORAL, result.category());
        assertTrue(result.reason().contains("500.0x"));
    }

    @Test
    @DisplayName("Should trigger absolute threshold (score 50) when exceeding absolute threshold with moderate ratio")
    void shouldTriggerAbsoluteThresholdWhenExceedingAbsoluteThresholdWithModerateRatio() {
        CustomerProfile profile = new CustomerProfile(
                "ACC-1",
                Instant.now(),
                BigDecimal.valueOf(2_500.00),
                50,
                Set.of(),
                false
        );
        Transaction tx = Transaction.builder()
                .id("TX-2")
                .accountId("ACC-1")
                .amount(12_500.00) // 5x average (< 10x critical), but > 10,000 absolute threshold
                .build();
        FraudEvaluationContext ctx = new FraudEvaluationContext(tx, profile, EnrichedContext.empty());

        RuleResult result = rule.evaluate(ctx);

        assertTrue(result.triggered());
        assertEquals(50, result.riskScore(), "Should trigger absolute threshold score 50 rather than review multiplier score 35");
        assertEquals(Action.REVIEW, result.recommendedAction());
    }

    @Test
    @DisplayName("Should trigger review multiplier (score 35) when ratio is 5x customer average and below absolute threshold")
    void shouldTriggerReviewMultiplierWhenExceedingReviewMultiplierBelowAbsoluteThreshold() {
        CustomerProfile profile = new CustomerProfile(
                "ACC-1",
                Instant.now(),
                BigDecimal.valueOf(1_000.00),
                50,
                Set.of(),
                false
        );
        Transaction tx = Transaction.builder()
                .id("TX-3")
                .accountId("ACC-1")
                .amount(5_000.00) // 5x average (>= 4x review multiplier, < 10,000 threshold)
                .build();
        FraudEvaluationContext ctx = new FraudEvaluationContext(tx, profile, EnrichedContext.empty());

        RuleResult result = rule.evaluate(ctx);

        assertTrue(result.triggered());
        assertEquals(35, result.riskScore());
        assertEquals(Action.REVIEW, result.recommendedAction());
    }

    @Test
    @DisplayName("Should exempt VIP customer from absolute threshold when transaction ratio is normal")
    void shouldExemptVipFromAbsoluteThresholdWhenRatioIsLow() {
        CustomerProfile profile = new CustomerProfile(
                "ACC-VIP",
                Instant.now(),
                BigDecimal.valueOf(20_000.00),
                90,
                Set.of(),
                true // VIP
        );
        Transaction tx = Transaction.builder()
                .id("TX-VIP-1")
                .accountId("ACC-VIP")
                .amount(25_000.00) // > 10,000 threshold, but ratio is 1.25x (< 4x)
                .build();
        FraudEvaluationContext ctx = new FraudEvaluationContext(tx, profile, EnrichedContext.empty());

        RuleResult result = rule.evaluate(ctx);

        assertFalse(result.triggered(), "VIP should not trigger absolute threshold when ratio is normal");
        assertEquals(Action.APPROVE, result.recommendedAction());
        assertEquals(0, result.riskScore());
    }

    @Test
    @DisplayName("Should trigger critical multiplier for VIP customer when ratio exceeds critical multiplier")
    void shouldTriggerCriticalMultiplierForVipWhenRatioIsCritical() {
        CustomerProfile profile = new CustomerProfile(
                "ACC-VIP",
                Instant.now(),
                BigDecimal.valueOf(1_000.00),
                90,
                Set.of(),
                true // VIP
        );
        Transaction tx = Transaction.builder()
                .id("TX-VIP-2")
                .accountId("ACC-VIP")
                .amount(15_000.00) // 15x average (>= 10x critical)
                .build();
        FraudEvaluationContext ctx = new FraudEvaluationContext(tx, profile, EnrichedContext.empty());

        RuleResult result = rule.evaluate(ctx);

        assertTrue(result.triggered());
        assertEquals(70, result.riskScore());
        assertEquals(Action.REVIEW, result.recommendedAction());
    }

    @Test
    @DisplayName("Should trigger absolute threshold when customer average is zero and amount exceeds threshold")
    void shouldTriggerAbsoluteThresholdWhenAverageIsZero() {
        CustomerProfile profile = new CustomerProfile(
                "ACC-NEW",
                Instant.now(),
                BigDecimal.ZERO,
                50,
                Set.of(),
                false
        );
        Transaction tx = Transaction.builder()
                .id("TX-NEW-1")
                .accountId("ACC-NEW")
                .amount(15_000.00)
                .build();
        FraudEvaluationContext ctx = new FraudEvaluationContext(tx, profile, EnrichedContext.empty());

        RuleResult result = rule.evaluate(ctx);

        assertTrue(result.triggered());
        assertEquals(50, result.riskScore());
        assertEquals(Action.REVIEW, result.recommendedAction());
    }

    @Test
    @DisplayName("Should pass cleanly for standard transaction within normal deviation")
    void shouldPassWhenNormalAmountAndRatio() {
        CustomerProfile profile = new CustomerProfile(
                "ACC-1",
                Instant.now(),
                BigDecimal.valueOf(100.00),
                50,
                Set.of(),
                false
        );
        Transaction tx = Transaction.builder()
                .id("TX-CLEAN")
                .accountId("ACC-1")
                .amount(150.00) // 1.5x average
                .build();
        FraudEvaluationContext ctx = new FraudEvaluationContext(tx, profile, EnrichedContext.empty());

        RuleResult result = rule.evaluate(ctx);

        assertFalse(result.triggered());
        assertEquals(Action.APPROVE, result.recommendedAction());
        assertEquals(0, result.riskScore());
    }
}

