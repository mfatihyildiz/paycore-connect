ALTER TABLE outbox_events
DROP CONSTRAINT chk_outbox_status;

ALTER TABLE outbox_events
    ADD CONSTRAINT chk_outbox_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED'));

ALTER TABLE outbox_events
    ADD COLUMN processing_started_at TIMESTAMP;

ALTER TABLE outbox_events
    ADD COLUMN lock_owner VARCHAR(100);

CREATE INDEX idx_outbox_processing_started_at
    ON outbox_events (processing_started_at)
    WHERE status = 'PROCESSING';