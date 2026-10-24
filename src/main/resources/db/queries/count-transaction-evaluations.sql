SELECT COUNT(*)
FROM fraud_evaluations
WHERE transaction_id = :lookupId
