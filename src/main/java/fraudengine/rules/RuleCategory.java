package fraudengine.rules;

/**
 * Classification of fraud rules defining their operational intent.
 */
public enum RuleCategory {
    /**
     * Non-negotiable deterministic rules (blacklists, terminal policy breach).
     * Typically short-circuits the engine.
     */
    HARD_BLOCK,

    /**
     * Burst frequency, rapid transactions, high aggregate velocity.
     */
    VELOCITY,

    /**
     * Deviations from historical user baseline (e.g. amount 10x higher than normal).
     */
    BEHAVIORAL,

    /**
     * Physical impossibilities, geo-distance speed anomalies, foreign high-risk IP/country.
     */
    GEOGRAPHIC,

    /**
     * Machine learning inference score, predictive neural net or gradient-boosted trees.
     */
    ML_ANOMALY
}
