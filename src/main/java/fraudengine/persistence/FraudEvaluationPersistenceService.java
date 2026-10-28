package fraudengine.persistence;

import fraudengine.api.dto.EvaluateTransactionResponseDto;
import fraudengine.domain.Transaction;
import fraudengine.events.FraudEvaluationCompletedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FraudEvaluationPersistenceService {

    private final FraudEvaluationRepository evaluationRepository;
    private final FraudEvaluationOutboxRepository outboxRepository;

    public FraudEvaluationPersistenceService(
            FraudEvaluationRepository evaluationRepository,
            FraudEvaluationOutboxRepository outboxRepository
    ) {
        this.evaluationRepository = evaluationRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public void save(Transaction transaction, EvaluateTransactionResponseDto evaluation) {
        evaluationRepository.save(transaction, evaluation);
        outboxRepository.enqueue(FraudEvaluationCompletedEvent.from(transaction, evaluation));
    }
}
