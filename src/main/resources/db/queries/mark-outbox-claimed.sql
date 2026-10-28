UPDATE fraud_event_outbox
SET claim_token = :claimToken,
    claim_until = :claimUntil
WHERE id IN (:ids)
