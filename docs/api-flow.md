# PayCore Connect - API Flow Documentation

This document explains the current API and messaging flows in PayCore Connect.

It focuses on how requests move through the system, which services are involved, where transactional boundaries exist, how asynchronous publication is performed, how duplicate requests/messages are handled, and what happens during retry or dead-letter scenarios.

---

## 1. Base URLs

### Frontend

```text
http://localhost:5173
```

### API Gateway

```text
http://localhost:8090
```

All browser-facing REST API calls should be sent through the API Gateway.

The Legacy Bank SOAP Service is an internal provider simulation and is not used directly by the frontend.

---

## 2. API Gateway Routing

| Gateway Path | Target Service | Target Port |
|---|---|---:|
| `/api/merchants/**` | Merchant Service | 8081 |
| `/api/payments/**` | Payment Service | 8082 |
| `/api/ledger/**` | Ledger Service | 8083 |
| `/api/settlements/**` | Settlement Service | 8085 |
| `/api/fraud/**` | Fraud Service | 8086 |
| `/api/notifications/**` | Notification Service | 8087 |

The API Gateway is the external entry point for the dashboard and external REST callers.

Internal service-to-service calls do not need to go back through API Gateway. For example, Payment Service calls Merchant Service and Fraud Service directly through their configured internal base URLs.

---

## 3. Merchant Creation Flow

### Purpose

Creates a merchant record and generates an API key.

### Endpoint

```http
POST /api/merchants
```

### Request

```json
{
  "name": "Docker Demo Merchant",
  "email": "docker-demo@merchant.com"
}
```

### Flow

```text
Client
  |
  v
API Gateway
  |
  v
Merchant Service
  |
  +----> PostgreSQL
  |
  +----> Redis cache
```

### What Happens

1. Client sends merchant creation request to API Gateway.
2. API Gateway routes the request to Merchant Service.
3. Merchant Service validates the request.
4. Merchant Service checks whether the email already exists.
5. Merchant Service generates a unique API key.
6. Merchant Service stores the merchant in PostgreSQL.
7. If the merchant is active, the API key lookup data is cached in Redis.
8. Merchant Service returns the merchant response.

### Expected Output

The response includes:

* Merchant ID
* Name
* Email
* API key
* Status
* Created timestamp
* Updated timestamp

### Current Correctness Note

The database model treats merchant email and API key as unique values.

Merchant creation currently performs an application-level existence check before saving. Race-safe duplicate registration handling is planned for the Merchant persistence-hardening phase.

---

## 4. Merchant Listing and Lookup Flow

### Endpoints

```http
GET /api/merchants
GET /api/merchants/{merchantId}
```

### Flow

```text
Client
  |
  v
API Gateway
  |
  v
Merchant Service
  |
  v
PostgreSQL
```

### What Happens

1. Client requests merchant information.
2. API Gateway routes the request to Merchant Service.
3. Merchant Service reads the merchant data from PostgreSQL.
4. Merchant Service returns either one merchant or the merchant list.

---

## 5. Merchant Status Update Flow

### Purpose

Changes a merchant's active/inactive status.

### Endpoint

```http
PATCH /api/merchants/{merchantId}/status
```

### Flow

```text
Client
  |
  v
API Gateway
  |
  v
Merchant Service
  |
  +----> PostgreSQL update
  |
  +----> Redis cache eviction/update
```

### What Happens

1. Merchant Service loads the merchant from PostgreSQL.
2. The status is updated.
3. The merchant is saved.
4. The existing API key cache entry is evicted.
5. If the merchant is active after the update, the current API key is cached again.
6. The updated merchant response is returned.

Redis is intended to be a derived cache. PostgreSQL remains the persistent merchant store.

---

## 6. API Key Regeneration Flow

### Purpose

Rotates a merchant API key.

### Endpoint

```http
POST /api/merchants/{merchantId}/api-key
```

### Flow

```text
Client
  |
  v
API Gateway
  |
  v
Merchant Service
  |
  +----> PostgreSQL
  |
  +----> evict old Redis cache key
  |
  +----> cache new key when merchant is ACTIVE
```

### What Happens

1. Merchant Service loads the merchant.
2. A new unique API key is generated.
3. The new key is persisted in PostgreSQL.
4. The old API key cache entry is removed.
5. The new key is cached for an active merchant.
6. The new API key is returned.

---

## 7. API Key Validation Flow

### Purpose

Validates whether a merchant API key belongs to an active merchant.

### Endpoint

```http
GET /api/merchants/validate-api-key
```

### Required Header

```http
X-API-Key: <merchant-api-key>
```

### External Flow

When called by a browser/client:

```text
Client
  |
  v
API Gateway
  |
  v
Merchant Service
```

### Internal Payment Flow

During payment initiation, Payment Service calls Merchant Service directly:

```text
Payment Service
  |
  | REST
  v
Merchant Service
  |
  +----> Redis
  |
  +----> PostgreSQL on cache miss
```

### What Happens

1. Merchant Service receives `X-API-Key`.
2. Blank keys are treated as invalid.
3. Merchant Service checks Redis first.
4. A valid cached entry is returned immediately.
5. On cache miss, Merchant Service queries PostgreSQL.
6. Only an `ACTIVE` merchant is considered valid.
7. A valid PostgreSQL result is cached for future lookup.
8. Merchant Service returns merchant ID, merchant name, status, and validation result.

### Why Redis Is Used

API key validation is part of the payment hot path.

Redis reduces repeated PostgreSQL reads, while PostgreSQL remains the persistent source of merchant data.

Cache-failure and stale-cache hardening are planned for a later Merchant reliability phase.

---

## 8. Payment Initiation Flow

### Purpose

Creates a payment, performs fraud and provider authorization, persists payment state, and durably schedules Kafka/RabbitMQ messages.

### Endpoint

```http
POST /api/payments/initiate
```

### Required Headers

```http
Content-Type: application/json
X-API-Key: <merchant-api-key>
Idempotency-Key: <unique-key-for-this-logical-payment>
```

Optional client IP forwarding header:

```http
X-Forwarded-For: <client-ip-address>
```

When `X-Forwarded-For` is absent, Payment Service uses `unknown` as the client IP passed to Fraud Service.

### Request Body

```json
{
  "amount": 1000.00,
  "currency": "TRY",
  "orderId": "ORDER-DOCKER-1001",
  "cardToken": "card_token_1234567890123456",
  "providerType": "MOCK_BANK"
}
```

### High-Level Flow

```text
Client
  |
  v
API Gateway
  |
  v
Payment Service
  |
  +---- REST ----> Merchant Service
  |
  +---- PostgreSQL ----> idempotency_records
  |
  +---- PostgreSQL ----> payments + outbox_events
  |
  +---- REST ----> Fraud Service
  |
  +---- Provider Authorization
  |        |
  |        +---- MOCK_BANK internal logic
  |        |
  |        +---- SOAP ----> Legacy Bank SOAP Service
  |
  +---- PostgreSQL ----> final payment + final outbox rows
  |
  v
HTTP response

Later, asynchronously:

outbox_events
  |
  v
Outbox Publisher
  |
  +----> Kafka
  |
  +----> RabbitMQ
```

### What Happens

1. Client sends the payment request through API Gateway.
2. API Gateway routes the request to Payment Service.
3. Payment Service validates the `Idempotency-Key` format.
4. Payment Service validates `X-API-Key` by calling Merchant Service.
5. Payment Service calculates a semantic SHA-256 request fingerprint.
6. Payment Service atomically acquires the idempotency key in PostgreSQL.
7. A previously completed matching request is replayed immediately.
8. Reuse of the same idempotency key with a different request is rejected.
9. A concurrent request using a key that is still `PROCESSING` is rejected.
10. Payment Service checks the separate `(merchant_id, order_id)` business invariant.
11. Payment Service creates the `INITIATED` payment.
12. `PAYMENT_INITIATED` is serialized and inserted into `outbox_events` in the same PostgreSQL transaction.
13. That short PostgreSQL transaction commits.
14. Payment Service calls Fraud Service outside the payment transaction.
15. Fraud Service calculates and stores the fraud result in MongoDB.
16. If fraud returns `REJECTED`, Payment Service finalizes the payment as `FAILED`.
17. Otherwise Payment Service calls the selected provider outside the database transaction.
18. Provider approval produces `AUTHORIZED`; provider rejection produces `FAILED`.
19. Final payment state, final Kafka event, and RabbitMQ notification are written in one PostgreSQL transaction.
20. Payment Service marks the idempotency record `COMPLETED` and stores the payment ID and serialized response.
21. HTTP response is returned without waiting for Kafka or RabbitMQ publication.
22. The outbox publisher later sends stored messages asynchronously.

### Important Transaction Boundary

Fraud and provider network calls are deliberately not executed inside long payment database transactions.

The request path uses short database transactions for durable state transitions and performs external I/O between those transactions.

---

## 9. Payment HTTP Idempotency Flow

### Scope

The idempotency database key is:

```text
merchant_id + operation + idempotency_key
```

For payment initiation:

```text
operation = PAYMENT_INITIATION
```

### Fingerprint

The semantic request hash is calculated from:

```text
normalized amount
normalized currency
orderId
cardToken
providerType
```

### Acquisition Flow

```text
Payment request
  |
  v
INSERT idempotency record as PROCESSING
ON CONFLICT DO NOTHING
  |
  +---- inserted ----> request owns processing
  |
  +---- conflict ----> load existing record
                         |
                         +---- same hash + COMPLETED -> replay
                         |
                         +---- different hash -> 409
                         |
                         +---- same hash + PROCESSING -> 409
```

### Response Behavior

| Situation | Result |
|---|---|
| New key | Payment processing begins |
| Same key + same request after completion | Stored `PaymentResponse` is replayed |
| Same key + different request | `409 Conflict` |
| Same key while original request is `PROCESSING` | `409 Conflict` |
| Missing/blank/invalid key | `400 Bad Request` |
| Different merchant using same textual key | Independent scope |

### Safe Reservation Release

A known-safe duplicate-order failure can release the temporary `PROCESSING` idempotency reservation.

If execution has already reached a point where an external provider may have produced a side effect, Payment Service does not blindly release the idempotency record.

This avoids encouraging an unsafe automatic retry when the provider outcome may be uncertain.

---

## 10. Duplicate Order Flow

HTTP idempotency and order uniqueness protect different invariants.

Payment Service enforces:

```text
UNIQUE (merchant_id, order_id)
```

### Flow

```text
Payment Service
  |
  +---- service-level duplicate pre-check
  |
  v
INSERT payment
  |
  +---- succeeds
  |
  +---- DB unique violation -> DuplicateOrderException -> 409
```

The PostgreSQL constraint is the final race-condition backstop.

A different `Idempotency-Key` cannot be used to create a second payment for the same merchant/order pair.

---

## 11. Payment Provider Flow - MOCK_BANK

### Purpose

Simulates a modern provider adapter without requiring an external bank.

### Provider Type

```text
MOCK_BANK
```

### Flow

```text
Payment Service
  |
  v
PaymentProviderFactory
  |
  v
Mock Bank Provider
  |
  v
ProviderPaymentResponse
```

### What Happens

1. Payment Service resolves the provider implementation from `PaymentProviderFactory`.
2. The mock provider receives merchant, amount, currency, order ID, and card token.
3. The provider returns an approval/failure result.
4. Payment Service translates the result into `AUTHORIZED` or `FAILED`.
5. Final state and final outbox rows are persisted atomically.

The provider call occurs outside the Payment Service database transaction.

---

## 12. Payment Provider Flow - LEGACY_BANK_SOAP

### Purpose

Simulates authorization through a SOAP-based legacy banking system.

### Provider Type

```text
LEGACY_BANK_SOAP
```

### Internal SOAP Endpoint

```text
http://legacy-bank-soap-service:8084/ws
```

Local host equivalent:

```text
http://localhost:8084/ws
```

### WSDL

```text
http://localhost:8084/ws/legacyBank.wsdl
```

### Flow

```text
Payment Service
  |
  v
PaymentProviderFactory
  |
  v
Legacy SOAP Provider Client
  |
  | SOAP
  v
Legacy Bank SOAP Service
  |
  v
ProviderPaymentResponse
```

### What Happens

1. Payment Service resolves the SOAP provider adapter.
2. The provider adapter builds a SOAP authorization request.
3. Payment Service calls Legacy Bank SOAP Service outside the payment DB transaction.
4. SOAP service returns an authorization response.
5. Payment Service stores the provider reference/code/message in the payment result.
6. Final payment state and final outbox rows are committed.
7. Kafka/RabbitMQ publication still happens asynchronously through the outbox relay.

### Current Reliability Boundary

A network timeout after a provider-side authorization can create an uncertain provider outcome.

The transactional outbox does not solve this external-provider ambiguity.

Provider-side idempotency and reconciliation are planned future improvements.

---

## 13. Fraud Check Flow

### Purpose

Evaluates payment risk before provider authorization.

### Endpoint

```http
POST /api/fraud/check
```

This endpoint is normally called internally by Payment Service.

### Flow

```text
Payment Service
  |
  | REST
  v
Fraud Service
  |
  v
MongoDB
```

### Fraud Request Context

The request contains payment and merchant context including:

```text
paymentId
merchantId
amount
currency
orderId
card token
client IP
```

### Fraud Rules

Example scoring inputs include:

```text
High amount threshold
Card velocity
IP velocity
```

### Possible Decisions

```text
APPROVED
REVIEW
REJECTED
```

### Rejected Flow

```text
Fraud Service -> REJECTED
        |
        v
Payment Service
        |
        v
finalize payment = FAILED
        |
        +----> final PAYMENT_FAILED Kafka outbox row
        |
        +----> failed-payment notification outbox row
```

Settlement ignores the resulting failed payment event.

### Current Reliability Boundary

Fraud Service currently stores a new fraud-check document for every invocation.

Automatic retries are therefore not yet enabled around this POST because duplicate calls can create duplicate fraud records and affect velocity-based risk calculation.

---

## 14. Transactional Outbox Creation Flow

### Purpose

Removes the database-to-broker dual-write from the payment request path.

### Initiated Payment Transaction

```text
BEGIN

INSERT payment(status = INITIATED)

INSERT outbox_event(
    destination = Kafka,
    event = PAYMENT_INITIATED
)

COMMIT
```

### Finalization Transaction

```text
BEGIN

UPDATE payment(status = AUTHORIZED / FAILED)

INSERT outbox_event(
    destination = Kafka,
    event = PAYMENT_AUTHORIZED / PAYMENT_FAILED
)

INSERT outbox_event(
    destination = RabbitMQ,
    event = PAYMENT_NOTIFICATION
)

COMMIT
```

`PaymentOutboxService` requires an existing transaction.

This prevents a payment state transition and its logical asynchronous message from being persisted independently.

### Stable Message Identity

The payload is serialized once when the outbox row is created.

The same stored payload is reused on every publication retry.

Kafka events and RabbitMQ notifications therefore retain a stable logical ID across retries.

---

## 15. Outbox Publication Flow

### Scheduler

The outbox scheduler periodically asks the publisher to process pending rows.

Default polling interval:

```text
1000 ms
```

### Claim Flow

```text
PENDING rows
  |
  v
SELECT eligible rows
FOR UPDATE ... SKIP LOCKED
  |
  v
mark PROCESSING
set lock_owner
set processing_started_at
increment attempt_count
commit claim transaction
```

Broker network I/O occurs after the claim transaction commits.

### Publication Flow

```text
PROCESSING outbox event
  |
  +---- destination = KAFKA ----> Kafka producer
  |
  +---- destination = RABBITMQ -> RabbitMQ producer
```

### Success

```text
broker success
  |
  v
mark outbox row PUBLISHED
```

### Failure

```text
broker/network failure
  |
  v
register failure
  |
  +---- status -> PENDING
  +---- last_error
  +---- last_attempt_at
  +---- next_attempt_at
```

Retry delay uses exponential backoff.

Current service defaults are based on:

```text
base delay: 5 seconds
maximum delay: 60 seconds
```

### Stale Claim Recovery

A publisher crash can leave a row in `PROCESSING`.

The publisher periodically releases stale claims after the configured lease timeout.

Default lease timeout:

```text
120 seconds
```

---

## 16. Kafka Publication Flow

### Purpose

Publishes payment lifecycle domain events.

### Source

```text
Payment Service outbox relay
```

### Topic

```text
payment-events
```

### Consumers

```text
Ledger Service
Settlement Service
```

### Example Events

```text
PAYMENT_INITIATED
PAYMENT_AUTHORIZED
PAYMENT_FAILED
```

### Flow

```text
Payment transaction
  |
  v
outbox_events
  |
  v
Outbox Publisher
  |
  | Kafka send stored JSON
  v
payment-events
  |
  +----> Ledger Service
  |
  +----> Settlement Service
```

### Publication Success Condition

The outbox publisher waits for the Kafka send future to complete successfully.

Only then can the row be marked `PUBLISHED`.

### Ordering

For the same payment and Kafka destination, later lifecycle messages are not claimable while an earlier message is still non-published.

The intended order is therefore:

```text
PAYMENT_INITIATED
        |
        v
PAYMENT_AUTHORIZED / PAYMENT_FAILED
```

### Delivery Semantics

The outbox is intentionally **at-least-once**.

If Kafka accepts a message but Payment Service crashes before the outbox row is marked `PUBLISHED`, the same logical message can be published again.

Downstream consumers therefore enforce idempotent business effects.

---

## 17. Ledger Consumer Flow

### Purpose

Stores an immutable payment event history and reconstructs payment state.

### Read Endpoints

```http
GET /api/ledger/payments/{paymentId}/events
GET /api/ledger/payments/{paymentId}/state
GET /api/ledger/merchants/{merchantId}/events
```

### Consumer Flow

```text
Kafka payment-events
  |
  v
Ledger Consumer
  |
  v
INSERT ledger event
ON CONFLICT (event_id) DO NOTHING
  |
  v
PostgreSQL
```

### Duplicate Handling

The database enforces uniqueness by:

```text
event_id
```

Repeated or concurrent delivery of the same event becomes a no-op.

### State Reconstruction

Example history:

```text
PAYMENT_INITIATED
PAYMENT_AUTHORIZED
```

Reconstructed state:

```text
AUTHORIZED
```

Ledger is an immutable event ledger with state reconstruction.

It is not used as the authoritative source for all application state.

---

## 18. Ledger Kafka Retry and DLT Flow

### Normal Processing Failure

```text
payment-events
  |
  v
Ledger Consumer
  |
  X processing failure
  |
  +---- retry after ~1s
  |
  +---- retry after ~2s
  |
  +---- retry after ~4s
  |
  v
payment-events.ledger.DLT
```

Current configuration uses three retries after the initial processing attempt.

### Malformed JSON

```text
malformed Kafka payload
  |
  v
ErrorHandlingDeserializer
  |
  v
payment-events.ledger.DLT
```

The business consumer method does not need to accept malformed JSON.

The DLT publisher supports raw `byte[]` payloads so the original poison payload can be retained.

### DLT Publication

The Ledger DLT producer uses:

```text
acks = all
Kafka producer idempotence
fail if DLT send fails
bounded DLT send wait
bounded producer max.block.ms
```

A DLT failure is not silently treated as successful recovery.

---

## 19. Settlement Consumer Flow

### Purpose

Creates settlement data for authorized payments.

### Read Endpoints

```http
GET /api/settlements/payments/{paymentId}
GET /api/settlements/merchants/{merchantId}
GET /api/settlements/merchants/{merchantId}/summary
```

### Consumer Flow

```text
Kafka payment-events
  |
  v
Settlement Consumer
  |
  +---- event != PAYMENT_AUTHORIZED -> ignore
  |
  +---- PAYMENT_AUTHORIZED
            |
            v
       calculate settlement
            |
            v
       INSERT settlement
       ON CONFLICT DO NOTHING
            |
            v
       PostgreSQL
```

### Business Invariants

Settlement enforces both:

```text
UNIQUE (event_id)
UNIQUE (payment_id)
```

This protects against:

* repeated delivery of one event
* different authorized event IDs for the same payment

A payment therefore produces at most one settlement record under the current schema.

### Calculation Example

```text
Payment amount:      1000.00 TRY
Commission rate:     2.5%
Commission amount:   25.00 TRY
Net settlement:      975.00 TRY
```

---

## 20. Settlement Kafka Retry and DLT Flow

### Retryable Failure

```text
payment-events
  |
  v
Settlement Consumer
  |
  X processing failure
  |
  +---- retry after ~1s
  |
  +---- retry after ~2s
  |
  +---- retry after ~4s
  |
  v
payment-events.settlement.DLT
```

### Malformed Payload

A JSON deserialization failure is handled before normal settlement business processing.

The raw payload can be published to:

```text
payment-events.settlement.DLT
```

### Independent Consumer Failure Domains

Ledger and Settlement use different Kafka consumer groups and different DLTs.

Therefore the same `payment-events` record can:

```text
succeed in Ledger
fail in Settlement
```

or the opposite.

One consumer's failure does not imply that the other consumer failed.

### DLT Publication

Settlement uses the same DLT reliability policy as Ledger:

```text
acks = all
producer idempotence
fail on DLT send error
bounded DLT result wait
bounded max.block.ms
```

---

## 21. RabbitMQ Notification Publication Flow

### Purpose

Publishes background notification jobs without blocking payment completion.

### Source

```text
Payment Service outbox relay
```

### Exchange

```text
notification.exchange
```

### Routing Key

```text
payment.notification.created
```

### Main Queue

```text
payment.notification.queue
```

### Flow

```text
final payment transaction
  |
  v
RabbitMQ outbox row
  |
  v
Outbox Publisher
  |
  v
notification.exchange
  |
  | payment.notification.created
  v
payment.notification.queue
  |
  v
Notification Service
```

### Publisher Success Condition

The Payment Service RabbitMQ publisher uses:

* persistent JSON message delivery
* publisher confirms
* mandatory publishing
* returned-message detection

The outbox row is not considered successfully published unless the publisher confirms success and the message was not returned as unroutable.

---

## 22. Notification Consumer Flow

### Read Endpoints

```http
GET /api/notifications/payments/{paymentId}
GET /api/notifications/merchants/{merchantId}
```

### Consumer Flow

```text
payment.notification.queue
  |
  v
Notification Consumer
  |
  v
INSERT notification log as RECEIVED
ON CONFLICT (notification_id) DO NOTHING
  |
  +---- duplicate -> no-op
  |
  +---- new row
          |
          v
     simulated send
          |
          v
     mark SENT
```

### Duplicate Handling

Notification messages contain a stable:

```text
notificationId
```

PostgreSQL enforces:

```text
UNIQUE (notification_id)
```

The same notification can therefore be delivered multiple times without creating duplicate notification logs.

### Transaction Behavior

For a new message:

1. Notification row is inserted.
2. Simulated delivery is performed.
3. Row is updated to `SENT`.

If the `SENT` transition fails, processing throws and the transaction fails so RabbitMQ retry handling can take over.

---

## 23. Notification Retry and DLQ Flow

### Main Retry Flow

```text
payment.notification.queue
  |
  v
Notification Consumer
  |
  X processing failure
  |
  +---- bounded listener retry
  |
  v
reject without requeue
  |
  v
notification.dlx
  |
  | payment.notification.dead
  v
payment.notification.dlq
```

Current listener configuration includes:

```text
max retries:       3
initial interval:  1s
multiplier:        2.0
max interval:      4s
stateless retry:   true
```

### Infinite Requeue Protection

The listener uses:

```text
default-requeue-rejected: false
```

Terminal listener failures therefore do not loop forever in the main queue.

### Malformed JSON

A malformed RabbitMQ JSON payload can fail conversion before the business consumer method runs.

The message is rejected and can be dead-lettered through:

```text
notification.dlx
        |
        v
payment.notification.dlq
```

### DLQ Contents

RabbitMQ dead-lettered messages retain broker metadata such as `x-death`, which can be used to inspect why and from where a message was dead-lettered.

---

## 24. Successful Payment Flow

```text
React Dashboard
  |
  v
API Gateway
  |
  v
Payment Service
  |
  +----> Merchant Service
  |
  +----> acquire HTTP idempotency record
  |
  +----> transaction:
  |        payment = INITIATED
  |        PAYMENT_INITIATED outbox row
  |
  +----> Fraud Service
  |
  +----> Mock Bank or Legacy SOAP Bank
  |
  +----> transaction:
  |        payment = AUTHORIZED
  |        PAYMENT_AUTHORIZED outbox row
  |        notification outbox row
  |
  +----> complete idempotency record
  |
  v
201 Created

Asynchronously:

outbox_events
  |
  +----> Kafka payment-events
  |          |
  |          +----> Ledger Service
  |          |
  |          +----> Settlement Service
  |
  +----> RabbitMQ
             |
             v
        Notification Service
```

The HTTP payment response does not need to wait for Ledger, Settlement, or Notification processing.

---

## 25. Fraud-Rejected Payment Flow

```text
React Dashboard
  |
  v
API Gateway
  |
  v
Payment Service
  |
  +----> Merchant Service validates API key
  |
  +----> INITIATED payment + outbox
  |
  +----> Fraud Service returns REJECTED
  |
  +----> finalization transaction:
  |        payment = FAILED
  |        PAYMENT_FAILED outbox row
  |        failed-payment notification outbox row
  |
  +----> complete idempotency record
  |
  v
payment FAILED response

Asynchronously:

PAYMENT_INITIATED / PAYMENT_FAILED
  |
  +----> Ledger records lifecycle
  |
  +----> Settlement ignores non-authorized event

Notification job
  |
  v
Notification Service
```

No provider authorization occurs after a fraud rejection.

---

## 26. Provider-Rejected Payment Flow

```text
Payment Service
  |
  +----> Fraud allows processing
  |
  +----> Provider authorization
  |
  +----> provider rejects
  |
  +----> finalization transaction:
           payment = FAILED
           PAYMENT_FAILED outbox
           notification outbox
```

Ledger receives the failed lifecycle event.

Settlement ignores the failed payment.

Notification Service can create a failed-payment notification record.

---

## 27. Broker Outage Flow

Kafka or RabbitMQ unavailability does not roll back an already committed payment state.

### Kafka Unavailable

```text
payment commits
  |
  v
Kafka outbox row remains PENDING/retryable
  |
  v
RabbitMQ row may still publish independently
  |
  v
Kafka recovers
  |
  v
pending Kafka row publishes automatically
```

### RabbitMQ Unavailable

```text
payment commits
  |
  v
RabbitMQ outbox row remains PENDING/retryable
  |
  v
Kafka events may still publish
  |
  v
RabbitMQ recovers
  |
  v
notification row publishes automatically
```

Broker outages are isolated from payment database correctness.

---

## 28. Consumer Transient Failure Flow

### Kafka Example

```text
valid Kafka event
  |
  v
consumer DB temporarily unavailable
  |
  v
listener throws
  |
  v
bounded retry
  |
  +---- DB recovers before retry budget exhausted
  |
  v
business write succeeds
  |
  v
no DLT record
```

### RabbitMQ Example

```text
valid notification
  |
  v
notification DB temporarily unavailable
  |
  v
listener retry
  |
  +---- DB recovers
  |
  v
notification becomes SENT
  |
  v
no DLQ record
```

These flows have been manually verified against real local broker/database containers.

---

## 29. Consumer Permanent Failure Flow

### Ledger

```text
processing repeatedly fails
  |
  v
payment-events.ledger.DLT
```

### Settlement

```text
processing repeatedly fails
  |
  v
payment-events.settlement.DLT
```

### Notification

```text
processing repeatedly fails
  |
  v
notification.dlx
  |
  v
payment.notification.dlq
```

Terminal failure is isolated rather than causing an infinite blocking/requeue cycle.

---

## 30. Duplicate Delivery Flow

Asynchronous delivery is intentionally treated as at-least-once.

### Ledger

```text
same eventId delivered multiple times
  |
  v
first insert succeeds
later inserts conflict
  |
  v
one ledger row
```

### Settlement

```text
same eventId multiple times
or
different eventIds for same paymentId
  |
  v
database uniqueness
  |
  v
one settlement row
```

### Notification

```text
same notificationId multiple times
  |
  v
first insert succeeds
duplicates no-op
  |
  v
one notification row
```

This is why PayCore does not depend on an exactly-once broker guarantee.

---

## 31. Frontend Observation Flow

After payment creation, the React dashboard fetches distributed outputs through API Gateway:

```http
GET /api/payments/{paymentId}
GET /api/ledger/payments/{paymentId}/state
GET /api/settlements/payments/{paymentId}
GET /api/fraud/payments/{paymentId}
GET /api/notifications/payments/{paymentId}
```

The asynchronous views may become available slightly after the original payment response because Ledger, Settlement, and Notification are processed independently.

---

## 32. Current Failure Boundaries

The current API flow deliberately distinguishes between solved and unsolved reliability problems.

### Solved in the Current Flow

```text
HTTP duplicate payment request
    -> PostgreSQL idempotency

duplicate merchant order
    -> DB unique invariant

payment DB + broker dual-write
    -> transactional outbox

broker outage
    -> durable outbox retry

duplicate Kafka/Rabbit delivery
    -> DB-enforced consumer idempotency

consumer processing failure
    -> bounded retry + DLT/DLQ
```

### Still Planned

```text
Merchant concurrent-create hardening
Merchant Redis failure/cache-consistency hardening
Explicit REST/SOAP timeout policy
Fraud POST idempotency
Provider-side idempotency
Uncertain provider outcome reconciliation
Circuit breakers / operation-safe retries
```

Retries are not added blindly to side-effecting synchronous operations until those operations have safe idempotency semantics.

---

## 33. Summary

The current PayCore API flow demonstrates:

* Centralized external routing through API Gateway
* Direct internal REST service-to-service calls
* Required HTTP payment idempotency
* Semantic request fingerprinting and completed-response replay
* Database-level duplicate-order enforcement
* Fraud evaluation before provider authorization
* Mock and SOAP provider adapters
* Short payment database transactions
* External network calls outside payment database transactions
* Transactional outbox creation
* Asynchronous Kafka and RabbitMQ publication
* Durable broker retry and stale-claim recovery
* Kafka-based ledger and settlement processing
* RabbitMQ-based notification processing
* Database-enforced idempotent consumers
* Bounded Kafka retry with separate Ledger/Settlement DLTs
* Bounded RabbitMQ retry with DLX/DLQ handling
* At-least-once delivery with idempotent business effects
* Broker outage recovery
* Distributed result observation through the React dashboard

The central reliability flow is:

```text
Client request
  |
  v
HTTP idempotency
  |
  v
Payment state + outbox transaction
  |
  v
HTTP response
  |
  v
Durable asynchronous publication
  |
  +----> Kafka consumers
  |        |
  |        +---- idempotent write
  |        +---- bounded retry
  |        +---- DLT if terminal
  |
  +----> RabbitMQ consumer
           |
           +---- idempotent write
           +---- bounded retry
           +---- DLQ if terminal
```
