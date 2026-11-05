package fraudengine.persistence;

import fraudengine.api.dto.EvaluateTransactionResponseDto;
import fraudengine.domain.Transaction;
import fraudengine.events.FraudEvaluationCompletedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class FraudEvaluationPersistenceService {

    private final FraudEvaluationRepository evaluationRepository;
    private final FraudEvaluationOutboxRepository outboxRepository;

    public FraudEvaluationPersistenceService(
            FraudEvaluationRepository evaluationRepository,
            FraudEvaluationOutboxRepository outboxRepository
    ) {
        this.evaluationRepository = Objects.requireNonNull(
                evaluationRepository,
                "evaluationRepository cannot be null"
        );
        this.outboxRepository = Objects.requireNonNull(outboxRepository, "outboxRepository cannot be null");
    }

    @Transactional
    public void save(Transaction transaction, EvaluateTransactionResponseDto evaluation) {
        Objects.requireNonNull(transaction, "transaction cannot be null");
        Objects.requireNonNull(evaluation, "evaluation cannot be null");
        evaluationRepository.save(transaction, evaluation);
        outboxRepository.enqueue(FraudEvaluationCompletedEvent.from(transaction, evaluation));
    }
}
