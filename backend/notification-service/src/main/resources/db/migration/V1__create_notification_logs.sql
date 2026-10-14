CREATE TABLE notification_logs (
                                   id UUID PRIMARY KEY,
                                   amount NUMERIC(18, 2) NOT NULL,
                                   currency VARCHAR(3) NOT NULL,
                                   merchant_id UUID NOT NULL,
                                   merchant_name VARCHAR(150) NOT NULL,
                                   message VARCHAR(1000) NOT NULL,
                                   notification_type VARCHAR(40) NOT NULL,
                                   order_id VARCHAR(100) NOT NULL,
                                   payment_id UUID NOT NULL,
                                   payment_status VARCHAR(40) NOT NULL,
                                   provider_reference_id VARCHAR(120),
                                   provider_response_code VARCHAR(30),
                                   provider_response_message VARCHAR(255),
                                   received_at TIMESTAMP NOT NULL,
                                   sent_at TIMESTAMP,
                                   simulated_target VARCHAR(500) NOT NULL,
                                   status VARCHAR(30) NOT NULL,

                                   CONSTRAINT chk_notification_logs_notification_type
                                       CHECK (notification_type IN (
                                                                    'PAYMENT_RESULT',
                                                                    'REFUND_RESULT',
                                                                    'SETTLEMENT_RESULT'
                                           )),

                                   CONSTRAINT chk_notification_logs_status
                                       CHECK (status IN (
                                                         'RECEIVED',
                                                         'SENT',
                                                         'FAILED'
                                           ))
);