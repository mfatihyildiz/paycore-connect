CREATE TABLE outbox_events (
                               id UUID PRIMARY KEY,

                               aggregate_type VARCHAR(50) NOT NULL,
                               aggregate_id UUID NOT NULL,
                               event_type VARCHAR(100) NOT NULL,

                               destination_type VARCHAR(20) NOT NULL,
                               destination VARCHAR(255) NOT NULL,

                               message_key VARCHAR(255),
                               routing_key VARCHAR(255),

                               payload TEXT NOT NULL,

                               status VARCHAR(20) NOT NULL,

                               created_at TIMESTAMP NOT NULL,
                               published_at TIMESTAMP,

                               CONSTRAINT chk_outbox_destination_type
                                   CHECK (destination_type IN ('KAFKA', 'RABBITMQ')),

                               CONSTRAINT chk_outbox_status
                                   CHECK (status IN ('PENDING', 'PUBLISHED'))
);

CREATE INDEX idx_outbox_pending_created_at
    ON outbox_events (created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_outbox_aggregate
    ON outbox_events (aggregate_type, aggregate_id);