package fraudengine.persistence;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class FraudEvaluationSql {

    private final String availability;
    private final String insertEvaluation;
    private final String findEvaluationById;
    private final String findTransactionById;
    private final String findAccountTransactions;
    private final String countAccountTransactions;
    private final String findTransactionEvaluations;
    private final String countTransactionEvaluations;
    private final String findAccountEvaluations;
    private final String countAccountEvaluations;

    public FraudEvaluationSql() {
        availability = load("availability.sql");
        insertEvaluation = load("insert-evaluation.sql");
        findEvaluationById = load("find-evaluation-by-id.sql");
        findTransactionById = load("find-transaction-by-id.sql");
        findAccountTransactions = load("find-account-transactions.sql");
        countAccountTransactions = load("count-account-transactions.sql");
        findTransactionEvaluations = load("find-transaction-evaluations.sql");
        countTransactionEvaluations = load("count-transaction-evaluations.sql");
        findAccountEvaluations = load("find-account-evaluations.sql");
        countAccountEvaluations = load("count-account-evaluations.sql");
    }

    public String availability() {
        return availability;
    }

    public String insertEvaluation() {
        return insertEvaluation;
    }

    public String findEvaluationById() {
        return findEvaluationById;
    }

    public String findTransactionById() {
        return findTransactionById;
    }

    public String findAccountTransactions() {
        return findAccountTransactions;
    }

    public String countAccountTransactions() {
        return countAccountTransactions;
    }

    public String findTransactionEvaluations() {
        return findTransactionEvaluations;
    }

    public String countTransactionEvaluations() {
        return countTransactionEvaluations;
    }

    public String findAccountEvaluations() {
        return findAccountEvaluations;
    }

    public String countAccountEvaluations() {
        return countAccountEvaluations;
    }

    private String load(String filename) {
        try {
            return new ClassPathResource("db/queries/" + filename)
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load SQL query: " + filename, ex);
        }
    }
}
