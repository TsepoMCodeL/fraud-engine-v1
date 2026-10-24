package fraudengine.engine;

import fraudengine.domain.FraudDecision;
import fraudengine.domain.FraudEvaluationContext;

/**
 * Port contract for telemetry, logging, and asynchronous event streaming
 * (e.g. publishing decisions to Apache Kafka, Elasticsearch, or data lakes).
 */
@FunctionalInterface
public interface FraudAuditListener {

    /**
     * Invoked when an evaluation completes. Must be non-blocking or asynchronous.
     */
    void onDecision(FraudEvaluationContext context, FraudDecision decision);
}

