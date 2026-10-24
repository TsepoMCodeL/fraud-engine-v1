SELECT transaction_json
FROM (
    SELECT transaction_json, event_timestamp, evaluated_at, evaluation_id,
           ROW_NUMBER() OVER (
               PARTITION BY transaction_id
               ORDER BY evaluated_at DESC, evaluation_id DESC
           ) AS row_number
    FROM fraud_evaluations
    WHERE account_id = :accountId
) latest
WHERE row_number = 1
ORDER BY event_timestamp DESC, evaluated_at DESC, evaluation_id DESC
LIMIT :limit OFFSET :offset
