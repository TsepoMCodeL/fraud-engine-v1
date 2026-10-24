package fraudengine.persistence;

import fraudengine.api.dto.EvaluateTransactionResponseDto;
import fraudengine.api.dto.TransactionEvaluationPageDto;
import fraudengine.api.dto.TransactionEvaluationResponseDto;
import fraudengine.api.dto.TransactionPageDto;
import fraudengine.domain.Transaction;

import java.util.Optional;

public interface FraudEvaluationRepository {

    void save(Transaction transaction, EvaluateTransactionResponseDto evaluation);

    boolean isAvailable();

    Optional<TransactionEvaluationResponseDto> findByEvaluationId(String evaluationId);

    Optional<Transaction> findTransactionById(String transactionId);

    TransactionPageDto findTransactionsByAccountId(String accountId, int page, int size);

    TransactionEvaluationPageDto findByTransactionId(String transactionId, int page, int size);

    TransactionEvaluationPageDto findByAccountId(String accountId, int page, int size);
}
