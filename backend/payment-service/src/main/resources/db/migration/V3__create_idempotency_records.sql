CREATE TABLE idempotency_records (
                                     id UUID PRIMARY KEY,

                                     merchant_id UUID NOT NULL,
                                     operation VARCHAR(50) NOT NULL,
                                     idempotency_key VARCHAR(255) NOT NULL,
                                     request_hash VARCHAR(64) NOT NULL,

                                     status VARCHAR(20) NOT NULL,

                                     payment_id UUID,
                                     response_body TEXT,

                                     created_at TIMESTAMP NOT NULL,
                                     completed_at TIMESTAMP,

                                     CONSTRAINT uk_idempotency_merchant_operation_key
                                         UNIQUE (merchant_id, operation, idempotency_key),

                                     CONSTRAINT fk_idempotency_payment
                                         FOREIGN KEY (payment_id)
                                             REFERENCES payments(id),

                                     CONSTRAINT chk_idempotency_status
                                         CHECK (status IN ('PROCESSING', 'COMPLETED'))
);

CREATE INDEX idx_idempotency_created_at
    ON idempotency_records (created_at);