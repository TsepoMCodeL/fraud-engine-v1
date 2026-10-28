UPDATE fraud_event_outbox
SET published_at = CURRENT_TIMESTAMP,
    last_error = NULL,
    claim_token = NULL,
    claim_until = NULL
WHERE id = :id
  AND claim_token = :claimToken
