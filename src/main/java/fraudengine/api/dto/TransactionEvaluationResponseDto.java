package fraudengine.api.dto;

import fraudengine.domain.Transaction;

public record TransactionEvaluationResponseDto(
        Transaction transaction,
        EvaluateTransactionResponseDto evaluation
) {
}
