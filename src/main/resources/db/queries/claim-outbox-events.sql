SELECT id, event_id, account_id, payload, attempts
FROM fraud_event_outbox
WHERE published_at IS NULL
  AND next_attempt_at <= CURRENT_TIMESTAMP
  AND (claim_until IS NULL OR claim_until <= CURRENT_TIMESTAMP)
ORDER BY id
LIMIT :batchSize
FOR UPDATE SKIP LOCKED
