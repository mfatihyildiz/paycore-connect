ALTER TABLE notification_logs
    ADD COLUMN notification_id UUID;

UPDATE notification_logs
SET notification_id = gen_random_uuid()
WHERE notification_id IS NULL;

ALTER TABLE notification_logs
    ALTER COLUMN notification_id SET NOT NULL;

ALTER TABLE notification_logs
    ADD CONSTRAINT uk_notification_logs_notification_id
        UNIQUE (notification_id);