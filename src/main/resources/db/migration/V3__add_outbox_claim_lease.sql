ALTER TABLE fraud_event_outbox
    ADD COLUMN claim_token VARCHAR(36);

ALTER TABLE fraud_event_outbox
    ADD COLUMN claim_until TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_fraud_event_outbox_claim
    ON fraud_event_outbox (published_at, claim_until, next_attempt_at, id);
