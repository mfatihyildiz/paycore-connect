CREATE TABLE payment_ledger_events (
                                       id UUID PRIMARY KEY,
                                       event_id UUID NOT NULL,
                                       event_type VARCHAR(80) NOT NULL,
                                       payment_id UUID NOT NULL,
                                       merchant_id UUID NOT NULL,
                                       amount NUMERIC(18, 2) NOT NULL,
                                       currency VARCHAR(3) NOT NULL,
                                       order_id VARCHAR(100) NOT NULL,
                                       payment_status VARCHAR(40) NOT NULL,
                                       provider_type VARCHAR(60) NOT NULL,
                                       provider_reference_id VARCHAR(120),
                                       provider_response_code VARCHAR(30),
                                       provider_response_message VARCHAR(255),
                                       occurred_at TIMESTAMP NOT NULL,
                                       consumed_at TIMESTAMP NOT NULL,

                                       CONSTRAINT uk_payment_ledger_events_event_id
                                           UNIQUE (event_id)
);