package fraudengine.events;

import fraudengine.api.dto.EvaluateTransactionResponseDto;
import fraudengine.domain.Action;
import fraudengine.domain.RiskLevel;
import fraudengine.domain.Transaction;

import java.time.Instant;

public record FraudEvaluationCompletedEvent(
        int schemaVersion,
        String eventId,
        String transactionId,
        String accountId,
        Instant evaluatedAt,
        Action action,
        RiskLevel riskLevel,
        int riskScore
) {
    public static FraudEvaluationCompletedEvent from(
            Transaction transaction,
            EvaluateTransactionResponseDto evaluation
    ) {
        return new FraudEvaluationCompletedEvent(
                1,
                evaluation.evaluationId(),
                transaction.id(),
                transaction.accountId(),
                evaluation.evaluatedAt(),
                evaluation.action(),
                evaluation.riskLevel(),
                evaluation.riskScore()
        );
    }
}
