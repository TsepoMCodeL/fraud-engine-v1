SELECT transaction_json, evaluation_json
FROM fraud_evaluations
WHERE evaluation_id = :evaluationId
