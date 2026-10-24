package fraudengine.engine;

import fraudengine.domain.CustomerProfile;
import fraudengine.domain.FraudDecision;
import fraudengine.domain.Transaction;

/**
 * Primary entry point into the Fraud Detection Engine.
 */
public interface FraudEngine {

    /**
     * Evaluates a transaction using default or looked-up customer profile.
     */
    FraudDecision evaluate(Transaction transaction);

    /**
     * Evaluates a transaction with an explicitly supplied customer profile baseline.
     */
    FraudDecision evaluate(Transaction transaction, CustomerProfile profile);
}

