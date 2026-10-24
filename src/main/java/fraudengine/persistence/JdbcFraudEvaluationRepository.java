package fraudengine.persistence;

import fraudengine.api.dto.EvaluateTransactionResponseDto;
import fraudengine.api.dto.TransactionEvaluationPageDto;
import fraudengine.api.dto.TransactionEvaluationResponseDto;
import fraudengine.api.dto.TransactionPageDto;
import fraudengine.domain.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcFraudEvaluationRepository implements FraudEvaluationRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcFraudEvaluationRepository.class);

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final FraudEvaluationSql sql;

    public JdbcFraudEvaluationRepository(
            JdbcClient jdbcClient,
            ObjectMapper objectMapper,
            FraudEvaluationSql sql
    ) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.sql = sql;
    }

    @Override
    public boolean isAvailable() {
        try {
            return jdbcClient.sql(sql.availability()).query(Integer.class).single() == 1;
        } catch (RuntimeException ex) {
            log.warn("PostgreSQL readiness check failed", ex);
            return false;
        }
    }

    @Override
    public void save(Transaction transaction, EvaluateTransactionResponseDto evaluation) {
        try {
            jdbcClient.sql(sql.insertEvaluation())
                    .param("evaluationId", evaluation.evaluationId())
                    .param("transactionId", transaction.id())
                    .param("accountId", transaction.accountId())
                    .param("eventTimestamp", Timestamp.from(transaction.timestamp()))
                    .param("evaluatedAt", Timestamp.from(evaluation.evaluatedAt()))
                    .param("action", evaluation.action().name())
                    .param("riskLevel", evaluation.riskLevel().name())
                    .param("riskScore", evaluation.riskScore())
                    .param("transactionJson", objectMapper.writeValueAsString(transaction))
                    .param("evaluationJson", objectMapper.writeValueAsString(evaluation))
                    .update();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to persist fraud evaluation", ex);
        }
    }

    @Override
    public Optional<TransactionEvaluationResponseDto> findByEvaluationId(String evaluationId) {
        return jdbcClient.sql(sql.findEvaluationById())
                .param("evaluationId", evaluationId)
                .query(this::mapEvaluation)
                .optional();
    }

    @Override
    public Optional<Transaction> findTransactionById(String transactionId) {
        return jdbcClient.sql(sql.findTransactionById())
                .param("transactionId", transactionId)
                .query(String.class)
                .optional()
                .map(this::mapTransaction);
    }

    @Override
    public TransactionPageDto findTransactionsByAccountId(String accountId, int page, int size) {
        long offset = (long) page * size;
        List<Transaction> content = jdbcClient.sql(sql.findAccountTransactions())
                .param("accountId", accountId)
                .param("limit", size)
                .param("offset", offset)
                .query(String.class)
                .list()
                .stream()
                .map(this::mapTransaction)
                .toList();
        Long total = jdbcClient.sql(sql.countAccountTransactions())
                .param("accountId", accountId)
                .query(Long.class)
                .single();
        int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return new TransactionPageDto(content, page, size, total, totalPages);
    }

    @Override
    public TransactionEvaluationPageDto findByTransactionId(String transactionId, int page, int size) {
        return findPage(
                sql.findTransactionEvaluations(),
                sql.countTransactionEvaluations(),
                transactionId,
                page,
                size
        );
    }

    @Override
    public TransactionEvaluationPageDto findByAccountId(String accountId, int page, int size) {
        return findPage(
                sql.findAccountEvaluations(),
                sql.countAccountEvaluations(),
                accountId,
                page,
                size
        );
    }

    private TransactionEvaluationPageDto findPage(
            String selectQuery,
            String countQuery,
            String parameterValue,
            int page,
            int size
    ) {
        long offset = (long) page * size;
        List<TransactionEvaluationResponseDto> content = jdbcClient.sql(selectQuery)
                .param("lookupId", parameterValue)
                .param("limit", size)
                .param("offset", offset)
                .query(this::mapEvaluation)
                .list();
        Long total = jdbcClient.sql(countQuery)
                .param("lookupId", parameterValue)
                .query(Long.class)
                .single();
        int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return new TransactionEvaluationPageDto(content, page, size, total, totalPages);
    }

    private TransactionEvaluationResponseDto mapEvaluation(ResultSet resultSet, int rowNumber) throws SQLException {
        try {
            Transaction transaction = mapTransaction(resultSet.getString("transaction_json"));
            EvaluateTransactionResponseDto evaluation = objectMapper.readValue(
                    resultSet.getString("evaluation_json"), EvaluateTransactionResponseDto.class);
            return new TransactionEvaluationResponseDto(transaction, evaluation);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read persisted fraud evaluation", ex);
        }
    }

    private Transaction mapTransaction(String transactionJson) {
        try {
            return objectMapper.readValue(transactionJson, Transaction.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read persisted transaction", ex);
        }
    }
}
