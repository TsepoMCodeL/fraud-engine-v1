package fraudengine.persistence;

import fraudengine.events.FraudEvaluationCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
public class JdbcFraudEvaluationOutboxRepository implements FraudEvaluationOutboxRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcFraudEvaluationOutboxRepository.class);
    private static final int PURGE_BATCH_SIZE = 1000;
    private static final int MAX_PURGE_BATCHES = 100;

    private final JdbcClient jdbcClient;
    private final ObjectWriter eventWriter;
    private final FraudEvaluationOutboxQueries sql;

    public JdbcFraudEvaluationOutboxRepository(
            JdbcClient jdbcClient,
            ObjectMapper objectMapper,
            FraudEvaluationOutboxQueries sql
    ) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient cannot be null");
        this.eventWriter = Objects.requireNonNull(objectMapper, "objectMapper cannot be null")
                .writerFor(FraudEvaluationCompletedEvent.class);
        this.sql = Objects.requireNonNull(sql, "sql cannot be null");
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
                    .param("claimUntil", OffsetDateTime.ofInstant(claimUntil, ZoneOffset.UTC))
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
                .param("nextAttemptAt", OffsetDateTime.ofInstant(nextAttemptAt, ZoneOffset.UTC))
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
                    .param("publishedBefore", OffsetDateTime.ofInstant(publishedBefore, ZoneOffset.UTC))
                    .param("batchSize", PURGE_BATCH_SIZE)
                    .update();
            iteration++;
        } while (deleted == PURGE_BATCH_SIZE && iteration < MAX_PURGE_BATCHES);
    }
}
