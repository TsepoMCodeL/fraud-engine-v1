CREATE TABLE fraud_evaluations (
    evaluation_id VARCHAR(36) PRIMARY KEY,
    transaction_id VARCHAR(255) NOT NULL,
    account_id VARCHAR(255) NOT NULL,
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    action VARCHAR(32) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    risk_score INTEGER NOT NULL,
    transaction_json TEXT NOT NULL,
    evaluation_json TEXT NOT NULL
);

CREATE INDEX idx_fraud_evaluations_transaction
    ON fraud_evaluations (transaction_id, evaluated_at DESC);

CREATE INDEX idx_fraud_evaluations_account_event
    ON fraud_evaluations (account_id, event_timestamp DESC, evaluated_at DESC);
