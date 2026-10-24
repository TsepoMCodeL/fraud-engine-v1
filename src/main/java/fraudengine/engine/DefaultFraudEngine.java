package fraudengine.engine;

import fraudengine.decision.DecisionEngine;
import fraudengine.decision.ThresholdDecisionEngine;
import fraudengine.domain.Action;
import fraudengine.domain.CustomerProfile;
import fraudengine.domain.EnrichedContext;
import fraudengine.domain.FraudDecision;
import fraudengine.domain.FraudEvaluationContext;
import fraudengine.domain.RiskLevel;
import fraudengine.domain.RuleResult;
import fraudengine.domain.Transaction;
import fraudengine.enrichment.ContextEnricher;
import fraudengine.rules.BlacklistRule;
import fraudengine.rules.DeviceTrustRule;
import fraudengine.rules.FraudRule;
import fraudengine.rules.GeoVelocityAnomalyRule;
import fraudengine.rules.HighAmountAnomalyRule;
import fraudengine.rules.ShortWindowVelocityRule;
import fraudengine.rules.TransactionVelocityRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Production-ready default orchestrator for the Fraud Engine.
 * Encapsulates the complete pipeline:
 * 1. State Enrichment (Feature Store)
 * 2. Prioritized Rule Pipeline Execution with Short-Circuiting
 * 3. Decision Aggregation
 * 4. Audit & Telemetry Dispatch
 * 5. State Persistence
 */
public class DefaultFraudEngine implements FraudEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultFraudEngine.class);

    private final List<FraudRule> rules;
    private final ContextEnricher contextEnricher;
    private final DecisionEngine decisionEngine;
    private final FraudEngineConfig config;
    private final List<FraudAuditListener> auditListeners;

    public DefaultFraudEngine(
            List<FraudRule> rules,
            ContextEnricher contextEnricher,
            DecisionEngine decisionEngine,
            FraudEngineConfig config,
            List<FraudAuditListener> auditListeners
    ) {
        Objects.requireNonNull(rules, "rules cannot be null");
        Objects.requireNonNull(contextEnricher, "contextEnricher cannot be null");
        Objects.requireNonNull(decisionEngine, "decisionEngine cannot be null");
        this.config = config != null ? config : FraudEngineConfig.defaultConfig();
        this.contextEnricher = contextEnricher;
        this.decisionEngine = decisionEngine;

        // Sort rules by priority
        List<FraudRule> sorted = new ArrayList<>(rules);
        Collections.sort(sorted);
        this.rules = Collections.unmodifiableList(sorted);

        this.auditListeners = new CopyOnWriteArrayList<>(auditListeners != null ? auditListeners : Collections.emptyList());
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public FraudDecision evaluate(Transaction transaction) {
        return evaluate(transaction, CustomerProfile.defaultProfile(transaction.accountId()));
    }

    @Override
    public FraudDecision evaluate(Transaction transaction, CustomerProfile profile) {
        long startNanos = System.nanoTime();
        String evaluationId = UUID.randomUUID().toString();

        FraudEvaluationContext context = null;
        try {
            EnrichedContext enrichment = contextEnricher.enrich(transaction, profile);
            context = new FraudEvaluationContext(transaction, profile, enrichment);

            List<RuleResult> ruleResults = evaluateRules(context);
            long elapsedNanos = System.nanoTime() - startNanos;
            FraudDecision decision = decisionEngine.decide(evaluationId, transaction, ruleResults, elapsedNanos);

            if (config.autoRecordTransactions() && decision.isApproved()) {
                contextEnricher.recordTransaction(transaction);
            }

            dispatchAudit(context, decision);
            return decision;

        } catch (Exception ex) {
            long elapsedNanos = System.nanoTime() - startNanos;
            log.error("Fraud evaluation {} failed for transaction {}", evaluationId, transaction.id(), ex);
            FraudDecision fallbackDecision = createFallbackDecision(evaluationId, transaction, elapsedNanos);

            if (context != null) {
                dispatchAudit(context, fallbackDecision);
            }

            return fallbackDecision;
        }
    }

    private List<RuleResult> evaluateRules(FraudEvaluationContext context) {
        List<RuleResult> results = new ArrayList<>();
        for (FraudRule rule : rules) {
            if (!rule.isEnabled()) {
                continue;
            }

            RuleResult result = rule.evaluate(context);
            results.add(result);

            if (shouldShortCircuit(rule, result)) {
                break;
            }
        }
        return results;
    }

    private boolean shouldShortCircuit(FraudRule rule, RuleResult result) {
        return config.shortCircuitEnabled()
                && rule.isShortCircuit()
                && result.triggered()
                && result.recommendedAction() == Action.DECLINE;
    }

    private FraudDecision createFallbackDecision(
            String evaluationId,
            Transaction transaction,
            long elapsedNanos
    ) {
        boolean failOpen = config.failOpenOnFatalError();
        return new FraudDecision(
                evaluationId,
                transaction.id(),
                transaction.accountId(),
                failOpen ? Action.APPROVE : Action.DECLINE,
                failOpen ? RiskLevel.LOW : RiskLevel.CRITICAL,
                failOpen ? 0 : 100,
                Collections.emptyList(),
                Collections.emptyList(),
                Instant.now(),
                elapsedNanos,
                "FATAL engine execution failure"
        );
    }

    private void dispatchAudit(FraudEvaluationContext context, FraudDecision decision) {
        for (FraudAuditListener listener : auditListeners) {
            try {
                listener.onDecision(context, decision);
            } catch (Exception ex) {
                log.warn("Fraud audit listener {} failed for evaluation {}",
                        listener.getClass().getName(), decision.evaluationId(), ex);
            }
        }
    }

    public List<FraudRule> getRegisteredRules() {
        return rules;
    }

    public static class Builder {
        private ContextEnricher contextEnricher;
        private DecisionEngine decisionEngine;
        private FraudEngineConfig config = FraudEngineConfig.defaultConfig();
        private final List<FraudAuditListener> auditListeners = new ArrayList<>();

        public Builder contextEnricher(ContextEnricher enricher) {
            this.contextEnricher = enricher;
            return this;
        }

        public Builder decisionEngine(DecisionEngine decisionEngine) {
            this.decisionEngine = decisionEngine;
            return this;
        }

        public Builder config(FraudEngineConfig config) {
            this.config = config;
            return this;
        }

        public Builder addAuditListener(FraudAuditListener listener) {
            this.auditListeners.add(listener);
            return this;
        }

        public DefaultFraudEngine build() {
            Objects.requireNonNull(contextEnricher, "contextEnricher must be configured (e.g. RedisContextEnricher)");
            if (decisionEngine == null) {
                decisionEngine = new ThresholdDecisionEngine();
            }

            List<FraudRule> defaultRules = List.of(
                    new BlacklistRule(),
                    new ShortWindowVelocityRule(),
                    new TransactionVelocityRule(),
                    new GeoVelocityAnomalyRule(),
                    new HighAmountAnomalyRule(),
                    new DeviceTrustRule()
            );
            return new DefaultFraudEngine(defaultRules, contextEnricher, decisionEngine, config, auditListeners);
        }
    }
}
