package fraudengine.api.dto;

import fraudengine.domain.Transaction;

import java.util.Objects;

public record TransactionEvaluationResponseDto(
        Transaction transaction,
        EvaluateTransactionResponseDto evaluation
) {
    public TransactionEvaluationResponseDto {
        Objects.requireNonNull(transaction, "transaction cannot be null");
        Objects.requireNonNull(evaluation, "evaluation cannot be null");
    }
}
