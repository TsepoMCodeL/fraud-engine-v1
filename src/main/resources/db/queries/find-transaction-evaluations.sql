SELECT transaction_json, evaluation_json
FROM fraud_evaluations
WHERE transaction_id = :lookupId
ORDER BY event_timestamp DESC, evaluated_at DESC, evaluation_id
LIMIT :limit OFFSET :offset
