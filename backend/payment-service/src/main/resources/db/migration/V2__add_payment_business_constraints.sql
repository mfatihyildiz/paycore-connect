ALTER TABLE payments
    ADD CONSTRAINT uk_payments_merchant_order
        UNIQUE (merchant_id, order_id);

CREATE INDEX idx_payments_merchant_created_at
    ON payments (merchant_id, created_at DESC);