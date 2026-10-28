INSERT INTO fraud_event_outbox (
    event_id,
    account_id,
    payload
) VALUES (
    :eventId,
    :accountId,
    :payload
)
