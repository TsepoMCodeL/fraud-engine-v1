package fraudengine.config;

import fraudengine.enrichment.ContextEnricher;
import fraudengine.persistence.FraudEvaluationRepository;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Custom Actuator health indicator verifying fraud engine dependencies (relational store and enrichment store).
 */
@Component("fraudEngineHealth")
public class FraudEngineHealthIndicator implements HealthIndicator {

    private final FraudEvaluationRepository evaluationRepository;
    private final ContextEnricher contextEnricher;

    public FraudEngineHealthIndicator(
            FraudEvaluationRepository evaluationRepository,
            ContextEnricher contextEnricher
    ) {
        this.evaluationRepository = Objects.requireNonNull(
                evaluationRepository,
                "evaluationRepository cannot be null"
        );
        this.contextEnricher = Objects.requireNonNull(contextEnricher, "contextEnricher cannot be null");
    }

    @Override
    public Health health() {
        boolean persistenceAvailable = checkPersistence();
        boolean enrichmentAvailable = checkEnrichment();

        Health.Builder builder = (persistenceAvailable && enrichmentAvailable)
                ? Health.up()
                : Health.down();

        return builder
                .withDetail("persistence", persistenceAvailable ? "Available" : "Unavailable")
                .withDetail("enrichmentStore", enrichmentAvailable ? "Available" : "Unavailable")
                .build();
    }

    private boolean checkPersistence() {
        try {
            return evaluationRepository.isAvailable();
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean checkEnrichment() {
        try {
            return contextEnricher.isAvailable();
        } catch (Exception ex) {
            return false;
        }
    }
}
