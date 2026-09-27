CREATE TABLE payments (
                          id UUID PRIMARY KEY,
                          merchant_id UUID NOT NULL,
                          amount NUMERIC(18, 2) NOT NULL,
                          currency VARCHAR(3) NOT NULL,
                          status VARCHAR(30) NOT NULL,
                          provider_type VARCHAR(50) NOT NULL,
                          order_id VARCHAR(100) NOT NULL,
                          provider_reference_id VARCHAR(100),
                          provider_response_code VARCHAR(20),
                          provider_response_message VARCHAR(255),
                          card_last_four_digits VARCHAR(10),
                          created_at TIMESTAMP NOT NULL,
                          updated_at TIMESTAMP NOT NULL
);