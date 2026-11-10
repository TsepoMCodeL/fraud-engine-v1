package fraudengine.api.dto;

import fraudengine.domain.Transaction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

@Schema(description = "Transaction and corresponding fraud evaluation pair")
public record TransactionEvaluationResponseDto(
        @Schema(description = "Underlying submitted transaction")
        Transaction transaction,

        @Schema(description = "Fraud evaluation decision and rule audit record")
        EvaluateTransactionResponseDto evaluation
) {
    public TransactionEvaluationResponseDto {
        Objects.requireNonNull(transaction, "transaction cannot be null");
        Objects.requireNonNull(evaluation, "evaluation cannot be null");
    }
}
