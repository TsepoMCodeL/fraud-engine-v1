package fraudengine.persistence;

import fraudengine.events.FraudEvaluationCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcFraudEvaluationOutboxRepository implements FraudEvaluationOutboxRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcFraudEvaluationOutboxRepository.class);
    private static final int PURGE_BATCH_SIZE = 1000;
    private static final int MAX_PURGE_BATCHES = 100;

    private final JdbcClient jdbcClient;
    private final ObjectWriter eventWriter;
    private final FraudEvaluationOutboxSql sql;

    public JdbcFraudEvaluationOutboxRepository(
            JdbcClient jdbcClient,
            ObjectMapper objectMapper,
            FraudEvaluationOutboxSql sql
    ) {
        this.jdbcClient = jdbcClient;
        this.eventWriter = objectMapper.writerFor(FraudEvaluationCompletedEvent.class);
        this.sql = sql;
    }

    @Override
    public void enqueue(FraudEvaluationCompletedEvent event) {
        try {
            jdbcClient.sql(sql.insert())
                    .param("eventId", event.eventId())
                    .param("accountId", event.accountId())
                    .param("payload", eventWriter.writeValueAsString(event))
                    .update();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to enqueue fraud evaluation event", ex);
        }
    }

    @Override
    @Transactional
    public List<OutboxMessage> claimPending(int batchSize, Instant claimUntil) {
        String claimToken = UUID.randomUUID().toString();
        List<OutboxMessage> messages = jdbcClient.sql(sql.claimPending())
                .param("batchSize", batchSize)
                .query((resultSet, rowNumber) -> new OutboxMessage(
                        resultSet.getLong("id"),
                        resultSet.getString("event_id"),
                        resultSet.getString("account_id"),
                        resultSet.getString("payload"),
                        resultSet.getInt("attempts"),
                        claimToken
                ))
                .list();

        if (!messages.isEmpty()) {
            List<Long> ids = messages.stream().map(OutboxMessage::id).toList();
            jdbcClient.sql(sql.markClaimed())
                    .param("ids", ids)
                    .param("claimToken", claimToken)
                    .param("claimUntil", Timestamp.from(claimUntil))
                    .update();
        }

        return messages;
    }

    @Override
    public void markPublished(long id, String claimToken) {
        int updated = jdbcClient.sql(sql.markPublished())
                .param("id", id)
                .param("claimToken", claimToken)
                .update();
        if (updated == 0) {
            log.warn("Outbox message {} was not marked as published; claim lease may have expired or token mismatch", id);
        }
    }

    @Override
    public void markFailed(long id, String claimToken, String error, Instant nextAttemptAt) {
        int updated = jdbcClient.sql(sql.markFailed())
                .param("id", id)
                .param("claimToken", claimToken)
                .param("lastError", error)
                .param("nextAttemptAt", Timestamp.from(nextAttemptAt))
                .update();
        if (updated == 0) {
            log.warn("Outbox message {} was not marked as failed; claim lease may have expired or token mismatch", id);
        }
    }

    @Override
    public void deletePublishedBefore(Instant publishedBefore) {
        int deleted;
        int iteration = 0;
        do {
            deleted = jdbcClient.sql(sql.deletePublished())
                    .param("publishedBefore", Timestamp.from(publishedBefore))
                    .param("batchSize", PURGE_BATCH_SIZE)
                    .update();
            iteration++;
        } while (deleted == PURGE_BATCH_SIZE && iteration < MAX_PURGE_BATCHES);
    }
}
