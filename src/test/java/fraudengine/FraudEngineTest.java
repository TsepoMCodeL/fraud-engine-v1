package fraudengine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import fraudengine.decision.DecisionThresholds;
import fraudengine.decision.ThresholdDecisionEngine;
import fraudengine.domain.*;
import fraudengine.engine.DefaultFraudEngine;
import fraudengine.engine.FraudEngine;
import fraudengine.engine.FraudEngineConfig;
import fraudengine.rules.AbstractFraudRule;
import fraudengine.rules.RuleCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FraudEngineTest {

    private TestContextEnricher enricher;
    private FraudEngine engine;

    @BeforeEach
    void setUp() {
        enricher = new TestContextEnricher();
        engine = DefaultFraudEngine.builder()
                .contextEnricher(enricher)
                .decisionEngine(new ThresholdDecisionEngine(new DecisionThresholds(40, 75, true)))
                .config(new FraudEngineConfig(true, true, false))
                .build();
    }

    @Test
    @DisplayName("Short-circuiting: blacklisted entity halts pipeline immediately at rule 1")
    void testBlacklistShortCircuit() {
        enricher.addBlacklistedAccount("ACC-BLOCKED-01");

        Transaction tx = Transaction.builder()
                .id("TX-B1")
                .accountId("ACC-BLOCKED-01")
                .amount(10.00)
                .build();

        FraudDecision decision = engine.evaluate(tx);

        assertEquals(Action.DECLINE, decision.action());
        assertEquals(RiskLevel.CRITICAL, decision.riskLevel());
        assertEquals(100, decision.aggregateRiskScore());
        assertEquals(1, decision.allEvaluatedRules().size(), "Only BlacklistRule should execute before short-circuit");
    }

    @Test
    @DisplayName("Velocity burst triggers decline after exceeding 10-minute threshold")
    void testVelocityBurst() {
        String accountId = "ACC-VELOCITY-01";
        CustomerProfile profile = CustomerProfile.defaultProfile(accountId);

        // Record 5 previous transactions within last 5 minutes
        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            Transaction pastTx = Transaction.builder()
                    .id("TX-PREV-" + i)
                    .accountId(accountId)
                    .amount(20.00)
                    .timestamp(now.minus(i + 1, ChronoUnit.MINUTES))
                    .build();
            enricher.recordTransaction(pastTx);
        }

        // New transaction arriving now
        Transaction incomingTx = Transaction.builder()
                .id("TX-NEW-BURST")
                .accountId(accountId)
                .amount(25.00)
                .timestamp(now)
                .build();

        FraudDecision decision = engine.evaluate(incomingTx, profile);

        assertEquals(Action.DECLINE, decision.action());
        assertTrue(decision.aggregateRiskScore() >= 75);
    }

    @Test
    @DisplayName("Legitimate transaction passes with APPROVE and score 0")
    void testCleanTransaction() {
        String accountId = "ACC-CLEAN-01";
        CustomerProfile profile = new CustomerProfile(
                accountId,
                Instant.now().minus(100, ChronoUnit.DAYS),
                BigDecimal.valueOf(50.00),
                80,
                Set.of("DEVICE-1"),
                false
        );

        Transaction tx = Transaction.builder()
                .id("TX-CLEAN")
                .accountId(accountId)
                .amount(45.00)
                .deviceId("DEVICE-1")
                .location(new GeoLocation(40.7128, -74.0060, "US"))
                .build();

        FraudDecision decision = engine.evaluate(tx, profile);

        assertEquals(Action.APPROVE, decision.action());
        assertEquals(RiskLevel.LOW, decision.riskLevel());
        assertEquals(0, decision.aggregateRiskScore());
        assertTrue(decision.triggeredRules().isEmpty());
    }

    @Test
    @DisplayName("Rule exceptions decline rather than allowing a clean decision")
    void testRuleFailureDeclines() {
        var failingRule = new AbstractFraudRule("R-FAIL", "Failing rule", 1, RuleCategory.BEHAVIORAL, false) {
            @Override
            protected RuleResult doEvaluate(FraudEvaluationContext context, long startNanos) {
                throw new IllegalStateException("internal failure detail");
            }
        };
        FraudEngine failingEngine = new DefaultFraudEngine(
                List.of(failingRule),
                enricher,
                new ThresholdDecisionEngine(),
                new FraudEngineConfig(false, false, false),
                List.of()
        );

        FraudDecision decision = failingEngine.evaluate(Transaction.builder()
                .id("TX-RULE-FAIL")
                .accountId("ACC-RULE-FAIL")
                .amount(50.00)
                .build());

        assertEquals(Action.DECLINE, decision.action());
        assertEquals(100, decision.aggregateRiskScore());
        assertTrue(decision.triggeredRules().getFirst().triggered());
        assertFalse(decision.primaryReason().contains("internal failure detail"));
    }

    @Test
    @DisplayName("Enrichment exceptions follow the configured fail-closed behavior")
    void testEnrichmentFailureDeclines() {
        TestContextEnricher failingEnricher = new TestContextEnricher() {
            @Override
            public EnrichedContext enrich(Transaction transaction, CustomerProfile profile) {
                throw new IllegalStateException("state store unavailable");
            }
        };
        FraudEngine failingEngine = DefaultFraudEngine.builder()
                .contextEnricher(failingEnricher)
                .build();

        FraudDecision decision = failingEngine.evaluate(Transaction.builder()
                .id("TX-ENRICH-FAIL")
                .accountId("ACC-ENRICH-FAIL")
                .amount(50.00)
                .build());

        assertEquals(Action.DECLINE, decision.action());
        assertEquals(100, decision.aggregateRiskScore());
    }
}
