CREATE TABLE settlements (
                             id UUID PRIMARY KEY,
                             event_id UUID NOT NULL,
                             payment_id UUID NOT NULL,
                             merchant_id UUID NOT NULL,
                             gross_amount NUMERIC(18, 2) NOT NULL,
                             commission_rate NUMERIC(8, 5) NOT NULL,
                             commission_amount NUMERIC(18, 2) NOT NULL,
                             net_amount NUMERIC(18, 2) NOT NULL,
                             currency VARCHAR(3) NOT NULL,
                             status VARCHAR(30) NOT NULL,
                             settlement_date DATE NOT NULL,
                             order_id VARCHAR(100) NOT NULL,
                             source_event_type VARCHAR(80) NOT NULL,
                             source_event_occurred_at TIMESTAMP NOT NULL,
                             created_at TIMESTAMP NOT NULL,
                             updated_at TIMESTAMP NOT NULL,

                             CONSTRAINT uk_settlements_event_id UNIQUE (event_id),
                             CONSTRAINT uk_settlements_payment_id UNIQUE (payment_id),

                             CONSTRAINT chk_settlements_status
                                 CHECK (status IN ('CALCULATED', 'PAID', 'FAILED'))
);