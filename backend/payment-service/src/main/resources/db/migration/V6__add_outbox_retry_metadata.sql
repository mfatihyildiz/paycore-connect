ALTER TABLE outbox_events
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_attempt_at TIMESTAMP,
    ADD COLUMN next_attempt_at TIMESTAMP,
    ADD COLUMN last_error TEXT;

ALTER TABLE outbox_events
    ADD CONSTRAINT chk_outbox_attempt_count
        CHECK (attempt_count >= 0);

CREATE INDEX idx_outbox_pending_retry
    ON outbox_events (next_attempt_at, created_at)
    WHERE status = 'PENDING';