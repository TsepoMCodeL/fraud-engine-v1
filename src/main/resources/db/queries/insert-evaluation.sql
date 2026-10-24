INSERT INTO fraud_evaluations (
    evaluation_id,
    transaction_id,
    account_id,
    event_timestamp,
    evaluated_at,
    action,
    risk_level,
    risk_score,
    transaction_json,
    evaluation_json
) VALUES (
    :evaluationId,
    :transactionId,
    :accountId,
    :eventTimestamp,
    :evaluatedAt,
    :action,
    :riskLevel,
    :riskScore,
    :transactionJson,
    :evaluationJson
)
