UPDATE fraud_event_outbox
SET attempts = attempts + 1,
    last_error = :lastError,
    next_attempt_at = :nextAttemptAt,
    claim_token = NULL,
    claim_until = NULL
WHERE id = :id
  AND claim_token = :claimToken
