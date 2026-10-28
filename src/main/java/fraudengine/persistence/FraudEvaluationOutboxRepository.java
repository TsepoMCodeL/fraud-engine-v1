package fraudengine.persistence;

import fraudengine.events.FraudEvaluationCompletedEvent;

import java.time.Instant;
import java.util.List;

public interface FraudEvaluationOutboxRepository {

    void enqueue(FraudEvaluationCompletedEvent event);

    List<OutboxMessage> claimPending(int batchSize, Instant claimUntil);

    void markPublished(long id, String claimToken);

    void markFailed(long id, String claimToken, String error, Instant nextAttemptAt);

    void deletePublishedBefore(Instant publishedBefore);

    record OutboxMessage(
            long id,
            String eventId,
            String accountId,
            String payload,
            int attempts,
            String claimToken
    ) {}
}
