SELECT COUNT(DISTINCT transaction_id)
FROM fraud_evaluations
WHERE account_id = :accountId
