DELETE FROM fraud_event_outbox
WHERE id IN (
    SELECT id FROM fraud_event_outbox
    WHERE published_at < :publishedBefore
    ORDER BY published_at ASC, id ASC
    LIMIT :batchSize
)
