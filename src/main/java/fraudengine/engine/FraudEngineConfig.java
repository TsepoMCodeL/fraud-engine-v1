package fraudengine.engine;

/**
 * Global operational configuration for the fraud engine orchestrator.
 */
public record FraudEngineConfig(
        boolean shortCircuitEnabled,
        boolean autoRecordTransactions,
        boolean failOpenOnFatalError
) {
    public static FraudEngineConfig defaultConfig() {
        return new FraudEngineConfig(
                true,   // Halt on hard block decline rules to optimize latency
                true,   // Auto-record transactions to update real-time velocity metrics
                false   // Fail closed (Decline/Review) by default for financial safety
        );
    }
}

