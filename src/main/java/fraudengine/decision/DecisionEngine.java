package fraudengine.decision;

import java.util.List;

import fraudengine.domain.FraudDecision;
import fraudengine.domain.RuleResult;
import fraudengine.domain.Transaction;

/**
 * Strategy interface for synthesizing multiple RuleResults into a single final FraudDecision.
 */
public interface DecisionEngine {

    /**
     * Synthesizes rule outputs into a definitive actionable decision.
     *
     * @param evaluationId Unique ID for this evaluation trace.
     * @param transaction The transaction evaluated.
     * @param ruleResults Results from all executed rules.
     * @param totalExecutionTimeNanos Elapsed engine execution time.
     * @return Final fraud decision.
     */
    FraudDecision decide(
            String evaluationId,
            Transaction transaction,
            List<RuleResult> ruleResults,
            long totalExecutionTimeNanos
    );
}

