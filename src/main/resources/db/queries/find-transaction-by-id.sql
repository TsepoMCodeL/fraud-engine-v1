SELECT transaction_json
FROM fraud_evaluations
WHERE transaction_id = :transactionId
ORDER BY evaluated_at DESC, evaluation_id DESC
LIMIT 1
