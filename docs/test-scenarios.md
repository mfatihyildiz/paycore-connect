# PayCore Connect — Test Documentation

## 1. Purpose

This document describes the test strategy, test scope, test coverage, execution commands, and verified scenarios for **PayCore Connect — Payment Orchestration Platform**.

PayCore Connect is structured as a multi service fintech backend platform. The system includes payment orchestration, merchant validation, PostgreSQL backed HTTP idempotency, transactional outbox based event publication, fraud checks, Kafka domain events, RabbitMQ notification delivery, ledger reconstruction, settlement calculation, legacy SOAP bank integration, and API gateway routing support.

The fast automated test suite validates business logic, REST controllers, transactional persistence coordination, outbox claim/publish/retry behavior, event driven adapters, provider clients, external service wrappers, and configuration components without requiring real infrastructure during unit level execution.

A separate manual Docker E2E acceptance pass validates the correctness properties that depend on real PostgreSQL and broker behavior, including concurrent payment idempotency, Kafka outage recovery, RabbitMQ outage recovery, outbox retry/backoff, and eventual publication after broker recovery.

---

## 2. Services Covered

The following backend services are covered by the test suite:

| Service | Responsibility                                                                                                                                          |
|---|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `fraud-service` | Evaluates payment risk and stores fraud check results                                                                                                   |
| `merchant-service` | Manages merchants, API keys, status updates, and API key validation                                                                                     |
| `payment-service` | Orchestrates idempotent payment initiation, merchant validation, fraud check, provider authorization, transactional payment persistence, outbox creation, and reliable Kafka/RabbitMQ publication |
| `ledger-service` | Consumes payment events and reconstructs payment state from event history                                                                               |
| `settlement-service` | Consumes authorized payment events and calculates merchant settlement amounts                                                                           |
| `notification-service` | Consumes payment notification messages and stores simulated notification logs                                                                           |
| `legacy-bank-soap-service` | Simulates a legacy SOAP bank authorization provider                                                                                                     |
| `api-gateway` | Provides gateway level CORS and request logging configuration                                                                                           |

---

## 3. Test Strategy

The overall strategy is layered:

1. **Service Unit Tests**
    - Validate business rules directly at service layer.
    - Dependencies such as repositories, Redis, clients, producers, and templates are mocked.
    - No Spring context is required unless absolutely necessary.

2. **Controller Tests**
    - Validate endpoint mapping, HTTP status codes, request validation, JSON serialization, and exception handling.
    - `MockMvcBuilders.standaloneSetup(...)` is used instead of `@WebMvcTest`.

3. **Persistence and Outbox Coordination Tests**
    - Validate payment persistence and outbox creation through the dedicated persistence layer.
    - Verify duplicate-order translation is scoped only to payment persistence failures.
    - Verify outbox persistence failures are propagated instead of being misclassified as duplicate orders.

4. **Outbox Reliability Tests**
    - Validate immutable payload creation, claim/lease handling, stale claim recovery, broker acknowledgment handling, retry registration, attempt metadata, and exponential backoff.
    - Kafka and RabbitMQ publishers are mocked for fast unit execution.

5. **Event Consumer Tests**
    - Validate Kafka/RabbitMQ listener methods delegate incoming messages to the correct service method.

6. **Producer Tests**
    - Validate stored serialized outbox payloads are sent to the supplied Kafka topic or RabbitMQ exchange/routing key.
    - Validate Kafka send completion and RabbitMQ publisher-confirm / returned-message behavior.

7. **Client Wrapper Tests**
    - Validate REST/SOAP wrappers and response mapping.

8. **Configuration Tests**
    - Validate CORS and gateway request logging behavior.

9. **Manual Docker E2E Acceptance**
    - Validate payment idempotency against real PostgreSQL.
    - Validate transactional outbox behavior against real Kafka and RabbitMQ outages.
    - Verify payment requests remain successful while a broker is unavailable, retry metadata/backoff is persisted, and events are eventually published after broker recovery.

---

## 4. Test Environment

Recommended local environment:

| Component | Version / Notes |
|---|---|
| Java | Java 17+ |
| Build Tool | Maven |
| Test Framework | JUnit 5 |
| Mocking | Mockito |
| Assertions | AssertJ |
| Spring Test | MockMvc, MockRestServiceServer, ReflectionTestUtils |
| Serialization | Jackson JavaTimeModule where needed |

The automated unit and controller tests are intentionally designed to run locally without requiring:

- PostgreSQL
- MongoDB
- Redis
- Kafka
- RabbitMQ
- External REST services
- External SOAP services

A separate manual Docker E2E acceptance pass is used for payment idempotency and transactional outbox resilience. That validation runs against the real Docker Compose environment, real PostgreSQL state, Kafka, and RabbitMQ rather than mocks.

---

## 5. Test Execution Commands

Run all tests service by service from the project root.

### fraud-service

```powershell
cd "paycore-connect\backend\fraud-service"
mvn test
```

### merchant-service

```powershell
cd "paycore-connect\backend\merchant-service"
mvn test
```

### payment-service

```powershell
cd "paycore-connect\backend\payment-service"
mvn test
```

### ledger-service

```powershell
cd "paycore-connect\backend\ledger-service"
mvn test
```

### settlement-service

```powershell
cd "paycore-connect\backend\settlement-service"
mvn test
```

### notification-service

```powershell
cd "paycore-connect\backend\notification-service"
mvn test
```

### legacy-bank-soap-service

```powershell
cd "paycore-connect\backend\legacy-bank-soap-service"
mvn test
```

### api-gateway

```powershell
cd "paycore-connect\backend\api-gateway"
mvn test
```

---

## 6. Single Test Execution Examples

For targeted execution, use:

```powershell
mvn -Dtest=PaymentServiceTest test
```

```powershell
mvn -Dtest=PaymentControllerTest test
```

```powershell
mvn -Dtest=PaymentEventProducerTest test
```

```powershell
mvn -Dtest=PaymentOutboxServiceTest,PaymentPersistenceServiceTest,OutboxClaimServiceTest,OutboxPublisherServiceTest test
```

---

# 7. Detailed Test Coverage

---

## 7.1 fraud-service

### Tested Classes

| Test Class | Target Class |
|---|---|
| `FraudServiceTest` | `FraudService` |
| `FraudControllerTest` | `FraudController` |

### FraudServiceTest Coverage

The fraud service test suite validates the payment risk scoring algorithm.

Covered scenarios:

- Low risk payment returns `APPROVED`
- High amount payment increases risk score
- Card velocity rule increases risk score
- IP velocity rule increases risk score
- Combined rules can produce `HIGH` risk
- `HIGH` risk produces `REJECTED` decision
- `MEDIUM` risk produces `REVIEW` decision
- Fraud check is persisted through `FraudCheckRepository`
- Payment ID and merchant ID are correctly converted and stored
- Fraud checks can be queried by payment ID
- Fraud checks can be queried by merchant ID

### Fraud Rules Verified

| Rule | Trigger |
|---|---|
| `HIGH_AMOUNT` | Payment amount exceeds configured threshold |
| `CARD_VELOCITY_LIMIT` | Recent card attempts exceed configured threshold |
| `IP_VELOCITY_LIMIT` | Recent IP attempts exceed configured threshold |

### FraudControllerTest Coverage

Covered endpoints:

```http
POST /api/fraud/check
GET /api/fraud/payments/{paymentId}
GET /api/fraud/merchants/{merchantId}
```

Covered scenarios:

- Valid fraud check request returns risk result
- Invalid request body returns `400 Bad Request`
- Payment fraud checks are returned by payment ID
- Merchant fraud checks are returned by merchant ID
- Invalid UUID path variable returns `400 Bad Request`

---

## 7.2 merchant-service

### Tested Classes

| Test Class | Target Class |
|---|---|
| `MerchantServiceTest` | `MerchantService` |
| `MerchantControllerTest` | `MerchantController` |

### MerchantServiceTest Coverage

Covered scenarios:

- Merchant creation succeeds with valid request
- Duplicate email throws `DuplicateMerchantEmailException`
- New merchant receives generated API key
- Active merchant API key is cached in Redis
- Merchant list is returned
- Merchant can be retrieved by ID
- Missing merchant throws `MerchantNotFoundException`
- Merchant status can be updated
- Updating merchant status evicts old API key cache
- Active merchant status update re-caches API key
- API key can be regenerated
- Old API key cache is evicted after regeneration
- New API key is cached if merchant is active
- Valid API key from Redis cache returns valid response
- Malformed Redis cache value returns invalid response
- Valid API key from database returns valid response and refreshes cache
- Inactive merchant API key returns invalid response
- Null or blank API key returns invalid response

### MerchantControllerTest Coverage

Covered endpoints:

```http
POST /api/merchants
GET /api/merchants
GET /api/merchants/{merchantId}
PATCH /api/merchants/{merchantId}/status
POST /api/merchants/{merchantId}/api-key
GET /api/merchants/validate-api-key
```

Covered scenarios:

- Merchant creation returns `201 Created`
- Invalid merchant creation request returns `400 Bad Request`
- Duplicate merchant email returns `409 Conflict`
- Merchant list returns `200 OK`
- Merchant by ID returns `200 OK`
- Missing merchant returns `404 Not Found`
- Merchant status update returns updated response
- Invalid status update request returns `400 Bad Request`
- API key regeneration returns new API key
- API key validation returns validation response
- Missing `X-API-Key` header returns `400 Bad Request`

---

## 7.3 payment-service

### Tested Classes

| Test Class | Target Class |
|---|---|
| `PaymentServiceTest` | `PaymentService` |
| `PaymentControllerTest` | `PaymentController` |
| `PaymentOutboxServiceTest` | `PaymentOutboxService` |
| `PaymentPersistenceServiceTest` | `PaymentPersistenceService` |
| `OutboxClaimServiceTest` | `OutboxClaimService` |
| `OutboxPublisherServiceTest` | `OutboxPublisherService` |
| `PaymentEventProducerTest` | `PaymentEventProducer` |
| `PaymentNotificationProducerTest` | `PaymentNotificationProducer` |
| `MockBankPaymentProviderClientTest` | `MockBankPaymentProviderClient` |
| `PaymentProviderFactoryTest` | `PaymentProviderFactory` |
| `LegacyBankSoapPaymentProviderClientTest` | `LegacyBankSoapPaymentProviderClient` |
| `MerchantClientTest` | `MerchantClient` |
| `FraudClientTest` | `FraudClient` |

---

### PaymentServiceTest Coverage

The payment service is the central orchestration layer. It validates idempotency keys and merchant API keys, acquires durable request ownership in PostgreSQL, replays completed responses, checks duplicate orders, coordinates initiated/final payment persistence through `PaymentPersistenceService`, calls fraud service, and authorizes payment through provider clients.

Kafka and RabbitMQ are no longer called directly from the payment request path. Domain events and notification jobs are written to the transactional outbox and published asynchronously.

Covered scenarios:

- Valid payment request creates initiated payment through `PaymentPersistenceService`
- Idempotency key is validated before payment processing
- Request fingerprint is calculated before idempotency acquisition
- Completed idempotency record replays the previously stored `PaymentResponse`
- Replay skips payment persistence, fraud checks, provider authorization, and idempotency completion
- Same idempotency key with a changed payload is rejected
- Same idempotency key while the original request is still `PROCESSING` is rejected
- Duplicate order throws `DuplicateOrderException`
- Duplicate order releases the temporary idempotency reservation
- Merchant API key validation is enforced
- Payment currency and card metadata are normalized
- Initiated payment persistence is delegated to the transactional persistence service
- Fraud rejection finalizes payment as `FAILED`
- Approved provider response finalizes payment as `AUTHORIZED`
- Failed provider response finalizes payment as `FAILED`
- Provider response fields are persisted
- Successful, fraud-rejected, and provider-rejected outcomes complete the idempotency record
- Payment retrieval by ID and merchant remains covered

### PaymentControllerTest Coverage

Covered endpoints:

```http
POST /api/payments/initiate
GET /api/payments/{paymentId}
GET /api/payments/merchant/{merchantId}
```

Covered scenarios:

- Valid payment initiation returns `201 Created`
- `Idempotency-Key` is required and forwarded to `PaymentService`
- Invalid/reused/in-progress idempotency keys return the expected `400/409`
- Missing `X-API-Key` returns `400 Bad Request`
- Invalid merchant API key returns `401 Unauthorized`
- Duplicate order returns `409 Conflict`
- Request validation and query endpoints remain covered

### Payment Service Suite Result

The latest verified payment-service run completed successfully after the transactional outbox, retry/backoff, Kafka/RabbitMQ outage recovery, and persistence exception-scope changes.

The suite has grown beyond the earlier fixed test count, so this document tracks verified coverage rather than preserving the stale `46 tests` total.

### Payment Idempotency Request Contract

`POST /api/payments/initiate` requires:

```http
X-API-Key: <merchant-api-key>
Idempotency-Key: <unique-key-for-this-logical-payment>
```

Verified semantics:

- Same key + same request after completion returns the previously stored payment response
- Same key + different request returns `409 Conflict`
- Same key while the first request is still processing returns `409 Conflict`
- Different keys remain independent
- Duplicate order handling is separate from HTTP idempotency

### PaymentInitiateRequest Validation Covered

Invalid body test verifies validation errors for:

- `amount`
- `currency`
- `orderId`
- `cardToken`
- `providerType`

---

### PaymentOutboxServiceTest Coverage

`PaymentOutboxService` creates immutable outbox messages inside an existing business transaction.

Covered scenarios:

- Outbox operations require an active transaction
- Persisted payment ID is required
- Kafka and RabbitMQ destination metadata is mapped correctly
- Outbox row ID matches the logical event/notification ID
- Payload is serialized once at enqueue time
- Stored payload is independent from later mutation of the `Payment` entity
- New rows start in `PENDING`
- Serialization failures propagate

### PaymentPersistenceServiceTest Coverage

Covered scenarios:

- Initiated payment is saved before its outbox event is enqueued
- Initiated payment and outbox creation share the business transaction
- Final payment state enqueues the final Kafka event and RabbitMQ notification
- Payment uniqueness violations are translated to `DuplicateOrderException`
- Outbox integrity failures are **not** translated to `DuplicateOrderException`
- An outbox `DataIntegrityViolationException` propagates unchanged

### OutboxClaimServiceTest Coverage

Covered scenarios:

- Pending rows are claimed in bounded batches
- Claimed rows transition to `PROCESSING`
- Claim owner and processing timestamp are stored
- `attempt_count` is incremented
- `last_attempt_at` is recorded
- Retry eligibility is cleared during active processing
- Successful publication transitions the owned row to `PUBLISHED`
- Failed publication returns the row to `PENDING`
- Failure stores `next_attempt_at` and `last_error`
- Stale `PROCESSING` claims can be recovered

### OutboxPublisherServiceTest Coverage

Covered scenarios:

- Stale claims are released before publishing
- Kafka events are sent using the stored topic, key, and payload
- RabbitMQ events are sent using the stored exchange, routing key, payload, and outbox ID
- Broker success is followed by `PUBLISHED`
- Kafka/RabbitMQ failures register retry metadata instead of marking the row published
- Failure registration uses exponential backoff
- Root failure information is retained for diagnostics
- Failed events do not enter a hot retry loop
- Empty claim batches cause no broker interactions

### PaymentEventProducerTest Coverage

Target:

```java
PaymentEventProducer.publish(topic, messageKey, payload)
```

Covered scenarios:

- Uses the supplied topic and message key
- Publishes the exact serialized payload stored in the outbox
- Does not generate a new event ID or timestamp
- Returns the Kafka send future to the outbox publisher

### PaymentNotificationProducerTest Coverage

Target:

```java
PaymentNotificationProducer.publish(exchange, routingKey, payload, outboxEventId)
```

Covered scenarios:

- Uses the supplied exchange and routing key
- Publishes the exact stored JSON payload
- Uses the outbox event ID as correlation data
- Publishes a persistent JSON message
- Positive publisher confirm completes successfully
- Negative confirms propagate as failures
- Unroutable mandatory returns are treated as failures
- Does not regenerate notification identity or timestamp

---

### MockBankPaymentProviderClientTest Coverage

Covered scenarios:

- Provider type is `MOCK_BANK`
- Amount within limit is approved
- Approved response includes provider reference ID
- Amount above limit is rejected
- Rejected response includes `LIMIT_EXCEEDED`
- Response message is correctly mapped

---

### PaymentProviderFactoryTest Coverage

Covered scenarios:

- Factory returns the correct client for `MOCK_BANK`
- Factory returns the correct client for configured providers
- Unsupported provider type throws `IllegalArgumentException`
- Provider clients are registered by `PaymentProviderType`

---

### LegacyBankSoapPaymentProviderClientTest Coverage

Covered scenarios:

- Provider type is `LEGACY_BANK_SOAP`
- `ProviderPaymentRequest` is mapped to SOAP `AuthorizePaymentRequest`
- SOAP approved/rejected responses map to `ProviderPaymentResponse`
- SOAP endpoint URL is passed to `WebServiceTemplate`
- Merchant ID, amount, currency, order ID, and card token are sent correctly

---

### MerchantClientTest Coverage

Target:

```java
MerchantClient.validateApiKey(apiKey)
```

Covered scenarios:

- Sends `GET` request to merchant validation endpoint
- Sets `X-API-Key`
- Maps valid/invalid merchant responses
- Server errors propagate

---

### FraudClientTest Coverage

Target:

```java
FraudClient.checkPaymentRisk(request)
```

Covered scenarios:

- Sends `POST` request to fraud check endpoint
- Sends JSON request body
- Maps `REVIEW` / `REJECTED` responses and triggered rules
- Server errors propagate

---

## 7.4 ledger-service

### Tested Classes

| Test Class | Target Class |
|---|---|
| `LedgerServiceTest` | `LedgerService` |
| `LedgerControllerTest` | `LedgerController` |
| `PaymentEventConsumerTest` | `PaymentEventConsumer` |

### LedgerServiceTest Coverage

Covered scenarios:

- Payment event is saved when event ID is new
- Duplicate event ID is ignored
- Event payload is mapped to `PaymentLedgerEvent`
- Payment events are returned by payment ID
- Merchant events are returned by merchant ID
- Payment state is reconstructed from ordered events
- Current state is derived from the latest event
- First event metadata is included
- Last event metadata is included
- Event count is calculated
- Empty event history throws `PaymentLedgerNotFoundException`

### LedgerControllerTest Coverage

Covered endpoints:

```http
GET /api/ledger/payments/{paymentId}/events
GET /api/ledger/merchants/{merchantId}/events
GET /api/ledger/payments/{paymentId}/state
```

Covered scenarios:

- Payment event history returns `200 OK`
- Merchant event history returns `200 OK`
- Payment state reconstruction returns `200 OK`
- Missing event history returns `404 Not Found`
- Invalid UUID path variable returns `400 Bad Request`

### PaymentEventConsumerTest Coverage

Target:

```java
PaymentEventConsumer.consumePaymentEvent(event)
```

Covered scenarios:

- Incoming Kafka event is delegated to `LedgerService.savePaymentEvent(event)`
- Null event is delegated according to current implementation behavior

---

## 7.5 settlement-service

### Tested Classes

| Test Class | Target Class |
|---|---|
| `SettlementServiceTest` | `SettlementService` |
| `SettlementControllerTest` | `SettlementController` |
| `PaymentEventConsumerTest` | `PaymentEventConsumer` |

### SettlementServiceTest Coverage

Covered scenarios:

- Only `PAYMENT_AUTHORIZED` events are processed
- Non authorized payment events are ignored
- Duplicate event ID is ignored
- Existing payment ID settlement is ignored
- Gross amount is calculated from event amount
- Commission amount is calculated using configured commission rate
- Net amount is calculated as gross minus commission
- Settlement status is set to `CALCULATED`
- Settlement date is set
- Settlement source event fields are stored
- Settlement can be retrieved by payment ID
- Missing settlement throws `SettlementNotFoundException`
- Merchant settlement list is returned
- Merchant settlement summary calculates totals correctly
- Empty merchant settlement summary returns zero totals

### SettlementControllerTest Coverage

Covered endpoints:

```http
GET /api/settlements/payments/{paymentId}
GET /api/settlements/merchants/{merchantId}
GET /api/settlements/merchants/{merchantId}/summary
```

Covered scenarios:

- Settlement by payment ID returns `200 OK`
- Missing settlement returns `404 Not Found`
- Merchant settlement list returns `200 OK`
- Merchant settlement summary returns `200 OK`
- Empty settlement summary returns zero values
- Invalid UUID path variable returns `400 Bad Request`

### Settlement PaymentEventConsumerTest Coverage

Target:

```java
PaymentEventConsumer.consumePaymentEvent(event)
```

Covered scenarios:

- Incoming Kafka event is delegated to `SettlementService.processPaymentEvent(event)`
- Null event is delegated according to current implementation behavior

---

## 7.6 notification-service

### Tested Classes

| Test Class | Target Class |
|---|---|
| `NotificationServiceTest` | `NotificationService` |
| `NotificationControllerTest` | `NotificationController` |
| `PaymentNotificationConsumerTest` | `PaymentNotificationConsumer` |

### NotificationServiceTest Coverage

Covered scenarios:

- Payment notification message creates a notification log
- Notification type is set to `PAYMENT_RESULT`
- Initial notification status is `RECEIVED`
- Simulated target is generated as `merchant-webhook://{merchantId}`
- Notification message text includes payment status, order, amount, currency, and provider response
- Simulated sending updates status to `SENT`
- Sent timestamp is set
- Notification logs can be queried by payment ID
- Notification logs can be queried by merchant ID

### NotificationControllerTest Coverage

Covered endpoints:

```http
GET /api/notifications/payments/{paymentId}
GET /api/notifications/merchants/{merchantId}
```

Covered scenarios:

- Payment notification list returns `200 OK`
- Merchant notification list returns `200 OK`
- Empty payment notification list returns `[]`
- Empty merchant notification list returns `[]`
- Invalid payment UUID returns `400 Bad Request`
- Invalid merchant UUID returns `400 Bad Request`

### PaymentNotificationConsumerTest Coverage

Target:

```java
PaymentNotificationConsumer.consumePaymentNotification(message)
```

Covered scenarios:

- Incoming RabbitMQ message is delegated to `NotificationService.processPaymentNotification(message)`
- Null message is delegated according to current implementation behavior

---

## 7.7 legacy-bank-soap-service

### Tested Classes

| Test Class | Target Class |
|---|---|
| `LegacyBankEndpointTest` | `LegacyBankEndpoint` |

### LegacyBankEndpointTest Coverage

Covered scenarios:

- Valid authorization request is approved
- Approved response includes bank reference ID
- Amount above limit is rejected
- Limit exceeded response returns `LIMIT_EXCEEDED`
- Blank card token is rejected
- Invalid card token response returns `INVALID_CARD_TOKEN`
- Response messages are correctly mapped

### Legacy SOAP Rules Verified

| Rule | Result |
|---|---|
| Amount greater than `100000` | Rejected with `LIMIT_EXCEEDED` |
| Null or blank card token | Rejected with `INVALID_CARD_TOKEN` |
| Valid amount and card token | Approved with generated reference ID |

---

## 7.8 api-gateway

### Tested Classes

| Test Class | Target Class |
|---|---|
| `CorsConfigTest` | `CorsConfig` |
| `RequestLoggingFilterTest` | `RequestLoggingFilter` |

### CorsConfigTest Coverage

Covered scenarios:

- CORS filter bean is created
- Allowed origins include frontend development origins
- Allowed methods include standard REST methods
- Allowed headers are configured
- Exposed headers include `X-API-Key`
- Credentials policy is configured

### RequestLoggingFilterTest Coverage

Covered scenarios:

- Filter logs request method and URI
- Filter delegates to the next filter chain
- Request processing is not blocked by logging

---

# 8. Exception Handling Coverage

The test suite validates exception handling across multiple services.

## merchant-service

| Exception | Expected HTTP Status |
|---|---|
| `MerchantNotFoundException` | `404 Not Found` |
| `DuplicateMerchantEmailException` | `409 Conflict` |
| `MethodArgumentNotValidException` | `400 Bad Request` |

## payment-service

| Exception | Expected HTTP Status |
|---|---|
| `PaymentNotFoundException` | `404 Not Found` |
| `InvalidMerchantApiKeyException` | `401 Unauthorized` |
| `InvalidIdempotencyKeyException` | `400 Bad Request` |
| `IdempotencyKeyReuseException` | `409 Conflict` |
| `IdempotencyRequestInProgressException` | `409 Conflict` |
| `DuplicateOrderException` | `409 Conflict` |
| `IllegalArgumentException` | `400 Bad Request` |
| `FraudRejectedPaymentException` | `403 Forbidden` |
| `MethodArgumentNotValidException` | `400 Bad Request` |

## ledger-service

| Exception | Expected HTTP Status |
|---|---|
| `PaymentLedgerNotFoundException` | `404 Not Found` |

## settlement-service

| Exception | Expected HTTP Status |
|---|---|
| `SettlementNotFoundException` | `404 Not Found` |

---

# 9. Validation Coverage

Request validation is verified through controller tests.

## Fraud Request Validation

Covered fields:

- `paymentId`
- `merchantId`
- `amount`
- `currency`
- `orderId`
- `cardToken`

## Merchant Request Validation

Covered fields:

- `name`
- `email`
- `status`

## Payment Request Validation

Covered fields:

- `amount`
- `currency`
- `orderId`
- `cardToken`
- `providerType`

## Payment Idempotency Header Validation

Covered behavior:

- Missing `Idempotency-Key` header returns `400 Bad Request`
- Null or blank idempotency key is rejected
- Idempotency keys longer than 255 characters are rejected
- Valid keys are scoped by merchant and payment initiation operation

---

# 10. Event Driven Testing Coverage

The platform uses Kafka for payment domain events and RabbitMQ for notification jobs. Payment Service publishes both through a PostgreSQL-backed transactional outbox.

## Transactional Outbox

| Component | Test Focus |
|---|---|
| `PaymentOutboxService` | Creates immutable Kafka/RabbitMQ payloads inside the business transaction |
| `PaymentPersistenceService` | Persists payment state and outbox rows together |
| `OutboxClaimService` | Claim/lease lifecycle, stale recovery, publication completion, retry metadata |
| `OutboxPublisherService` | Broker dispatch, acknowledgments, failure registration, exponential backoff |

## Kafka

| Service | Component | Test Focus |
|---|---|---|
| `payment-service` | `PaymentEventProducer` | Sends the exact stored outbox JSON payload and exposes send completion |
| `ledger-service` | `PaymentEventConsumer` | Delegates consumed events to ledger service |
| `settlement-service` | `PaymentEventConsumer` | Delegates consumed events to settlement service |

## RabbitMQ

| Service | Component | Test Focus |
|---|---|---|
| `payment-service` | `PaymentNotificationProducer` | Sends the exact stored outbox payload with publisher confirms and mandatory returns |
| `notification-service` | `PaymentNotificationConsumer` | Delegates consumed messages to notification service |

---

# 11. External Integration Wrapper Coverage

The platform has wrapper classes for REST and SOAP integrations. These tests validate adapter behavior without starting external services.

| Client | Protocol | Test Style |
|---|---|---|
| `MerchantClient` | REST | `MockRestServiceServer` |
| `FraudClient` | REST | `MockRestServiceServer` |
| `LegacyBankSoapPaymentProviderClient` | SOAP | Mocked `WebServiceTemplate` |

---

# 12. Important Test Design Decisions

## 12.1 Standalone MockMvc Instead of @WebMvcTest

Controller tests use `MockMvcBuilders.standaloneSetup(...)` for focused, fast controller behavior tests.

## 12.2 ReflectionTestUtils for @Value Fields

Configuration-backed values are injected in unit tests with `ReflectionTestUtils`, including outbox publisher timeout/backoff configuration.

## 12.3 Stored Payload Instead of Rebuilding Events on Retry

The transactional outbox serializes the logical event once when the business transaction creates the outbox row.

Retries publish the stored destination metadata, key/routing key, JSON payload, and logical event ID. They do not reconstruct an event from mutable payment state.

## 12.4 MockRestServiceServer for REST Clients

REST client wrappers are tested with `MockRestServiceServer` to verify HTTP method, URL, headers, response mapping, and error propagation.

## 12.5 Database-Backed Idempotency Acceptance

PostgreSQL-backed idempotency uses atomic `INSERT ... ON CONFLICT DO NOTHING`. Real Docker/PostgreSQL acceptance verifies ownership, replay, conflict behavior, row counts, and duplicate-order reservation cleanup.

## 12.6 Transactional Outbox Atomicity

Payment state and outbox rows are created inside short PostgreSQL transactions through `PaymentPersistenceService`.

The request path no longer publishes directly to Kafka/RabbitMQ:

```text
Payment transaction
    -> Payment row + Outbox row(s)
    -> PostgreSQL commit
    -> asynchronous outbox publisher
    -> Kafka / RabbitMQ
```

This removes the database/broker dual-write from the HTTP request transaction.

## 12.7 Claim/Lease and Stale Recovery

Eligible rows are claimed with `FOR UPDATE SKIP LOCKED`, moved to `PROCESSING`, and tagged with `lock_owner` / `processing_started_at`.

Broker network calls occur after the short claim transaction. Stale claims are later recoverable.

## 12.8 Per-Aggregate Event Ordering

A later event for the same payment and Kafka destination is not claimable while an earlier event is still non-published.

This preserves:

```text
PAYMENT_INITIATED
    -> PAYMENT_AUTHORIZED / PAYMENT_FAILED
```

RabbitMQ remains independent because it is a separate destination.

## 12.9 Retry Metadata and Exponential Backoff

Broker failures persist:

- `attempt_count`
- `last_attempt_at`
- `next_attempt_at`
- `last_error`

Retry eligibility respects `next_attempt_at`, preventing a hot scheduler retry loop.

## 12.10 Acknowledgment Semantics and At-Least-Once Delivery

An outbox row is marked `PUBLISHED` only after broker-side success:

- Kafka send future completes successfully
- RabbitMQ publisher confirm is positive
- RabbitMQ mandatory return is not present

The model is intentionally **at-least-once**, not exactly-once. A broker may accept a message before the application can persist `PUBLISHED`, so duplicate delivery remains possible.

Consumer-side idempotency is therefore the next reliability phase.

---

# 13. Manual Docker E2E Acceptance

Payment idempotency and transactional outbox resilience were validated against the running Docker Compose stack through:

```text
http://localhost:8090
```

The acceptance pass used the real Payment Service PostgreSQL database and real Kafka/RabbitMQ containers.

## 13.1 Completed Response Replay

Same key + same completed request replayed the stored response with the same payment ID and no second payment row.

## 13.2 Same Key with Different Payload

Reusing the same key with a changed amount returned:

```text
409 Conflict
```

## 13.3 New Key with Existing Order ID

A new idempotency key with an already processed merchant order returned `409 Conflict`.

Database verification confirmed one payment row and cleanup of the temporary duplicate-order idempotency reservation.

## 13.4 Missing Idempotency Header

Payment initiation without `Idempotency-Key` returned:

```text
400 Bad Request
```

## 13.5 Concurrent Same-Key Requests

Ten concurrent requests were executed with the same key/order/body.

The observed run produced a mix of `409` while the owner was `PROCESSING` and `201` after completion became replayable. All successful responses referenced the same payment ID; PostgreSQL contained one payment row and one final `COMPLETED` idempotency row.

## 13.6 Kafka Outage — Payment Request Independence

Kafka was stopped while the rest of the stack remained available.

A new payment still returned:

```text
HTTP 201 Created
Payment status = AUTHORIZED
```

PostgreSQL persisted the payment.

During the outage the expected outbox state was observed:

```text
PAYMENT_INITIATED      KAFKA      PENDING / transient PROCESSING
PAYMENT_AUTHORIZED     KAFKA      PENDING
PAYMENT_NOTIFICATION   RABBITMQ   PUBLISHED
```

This verified that Kafka availability does not control payment persistence and that RabbitMQ can continue independently.

## 13.7 Kafka Timeout and Retry/Backoff

Kafka producer metadata blocking was bounded to approximately the configured 5-second publish timeout.

Verified failure:

```text
TimeoutException: Topic payment-events not present in metadata after 5000 ms.
```

The same logical event ID and stored payload were retried. `attempt_count`, `last_attempt_at`, `next_attempt_at`, and `last_error` were populated, and retry timing followed exponential backoff.

## 13.8 Kafka Recovery

Kafka was restarted without resubmitting the payment request.

Final state:

```text
PAYMENT_INITIATED      KAFKA      PUBLISHED
PAYMENT_AUTHORIZED     KAFKA      PUBLISHED
PAYMENT_NOTIFICATION   RABBITMQ   PUBLISHED
```

This verified durable retention and automatic eventual publication.

## 13.9 RabbitMQ Outage and Recovery

RabbitMQ was stopped while Kafka remained available.

A new payment still returned `201 Created` / `AUTHORIZED`. Kafka events published normally while the notification row remained retryable with failure metadata.

After RabbitMQ restart, the existing notification row automatically reached `PUBLISHED` without replaying the payment request.

This verified independent Kafka/RabbitMQ failure domains.

## 13.10 Payment Persistence Exception-Scope Regression

`DataIntegrityViolationException` is translated to `DuplicateOrderException` only around the payment persistence operation.

A regression test verifies an outbox integrity failure propagates unchanged instead of being misreported as a duplicate order.

## 13.11 Final Regression Run

After retry/backoff, Kafka recovery, RabbitMQ recovery, and the persistence exception-scope correction, the full Payment Service automated suite completed successfully.

---

# 14. Known Non-Goals of the Current Test Suite

The fast automated suite does not aim to provide full end to end infrastructure testing.

Payment idempotency and transactional outbox broker-failure recovery have been manually validated against real PostgreSQL, Kafka, and RabbitMQ. The following remain outside repeatable automated infrastructure coverage:

- Automated Kafka/RabbitMQ outage and recovery tests in CI
- Automated Redis/MongoDB/PostgreSQL/Testcontainers integration
- Full cross-service Docker verification through ledger, settlement, and notification outputs
- Concurrent consumer duplicate-delivery tests against real databases
- Dead-letter handling for terminal/non-retriable messaging failures
- Contract testing
- Performance/load testing
- Security penetration testing

---

# 15. Recommended Future Improvements

## 15.1 Testcontainers Integration

Automate the currently manual PostgreSQL/Kafka/RabbitMQ acceptance checks with Testcontainers, including:

- Concurrent payment idempotency
- Payment commit while Kafka is unavailable
- Payment commit while RabbitMQ is unavailable
- Retry metadata/backoff progression
- Stale claim recovery
- Same-payment event ordering
- Automatic publication after broker recovery

## 15.2 Full Cross-Service End to End Payment Flow Test

Automate:

```text
Merchant API Key
-> Payment Initiation
-> Fraud Check
-> Provider Authorization
-> PostgreSQL Payment + Outbox Commit
-> Kafka Event
-> Ledger
-> Settlement
-> RabbitMQ Notification
-> Notification Log
```

## 15.3 Consumer Idempotency and Duplicate Delivery Tests

Because the outbox provides at-least-once delivery, add database-enforced duplicate protection and concurrency tests for:

- `ledger-service`
- `settlement-service`
- `notification-service`

## 15.4 Dead-Letter and Terminal Failure Tests

Add explicit handling/tests for terminal or non-retriable messaging failures. Retriable events currently remain durable in the outbox and use exponential backoff.

## 15.5 Contract Tests

Add contracts between Payment Service and merchant, fraud, and legacy SOAP provider services.

## 15.6 CI Pipeline

Recommended stages:

1. Compile
2. Unit tests
3. Controller tests
4. Adapter/outbox tests
5. Integration tests
6. Package
7. Docker build

## 15.7 Coverage Reporting

Add JaCoCo reporting. Suggested targets remain:

| Layer | Target |
|---|---|
| Service Layer | 80%+ |
| Controller Layer | 70%+ |
| Adapter / Outbox Layer | 70%+ |
| Overall | 75%+ |

---

# 16. Overall Coverage Summary

| Area | Status |
|---|---|
| Business service logic | Covered |
| REST controllers | Covered |
| Validation / exception handling | Covered |
| Payment idempotency unit/controller behavior | Covered |
| Payment idempotency Docker/PostgreSQL acceptance | Manually verified |
| Concurrent same-key payment protection | Manually verified |
| Transactional outbox creation/persistence coordination | Covered |
| Outbox claim/lease/stale recovery | Covered |
| Outbox retry metadata and exponential backoff | Covered + manually verified |
| Kafka stored-payload publishing | Covered |
| RabbitMQ confirm/return publishing | Covered |
| Kafka outage/recovery | Manually verified |
| RabbitMQ outage/recovery | Manually verified |
| Kafka consumers | Covered |
| RabbitMQ consumers | Covered |
| Ledger duplicate event behavior | Covered at current unit-test level |
| Settlement duplicate event behavior | Covered at current unit-test level |
| REST / SOAP clients | Covered |
| Gateway config/filter | Covered |
| Automated real-infrastructure integration | Future improvement |
| Full cross-service Docker E2E | Future improvement |
| Concurrent consumer idempotency against real DBs | Future improvement |

---

# 17. Final Assessment

The current test suite now covers both payment correctness and the reliability behavior around asynchronous broker publication.

Most important verified capabilities:

- PostgreSQL-backed HTTP idempotency and concurrent same-key protection
- Duplicate-order protection and idempotency reservation cleanup
- Transactional payment + outbox persistence
- Stable serialized event/message identity across retries
- Claim/lease handling and stale claim recovery
- Kafka send acknowledgment based publication
- RabbitMQ publisher-confirm and mandatory-return handling
- Retry attempt metadata and exponential backoff
- Successful payment processing while Kafka is unavailable
- Successful payment processing while RabbitMQ is unavailable
- Automatic eventual publication after broker recovery
- Same-payment Kafka lifecycle ordering
- Correct propagation of outbox integrity failures
- Existing fraud, provider, ledger, settlement, notification, REST, SOAP, and controller behavior

The verified outbox model provides **at-least-once** publication semantics. Duplicate delivery remains possible if a broker accepts a message but the application fails before PostgreSQL records `PUBLISHED`.

The next reliability phase is consumer-side idempotency and duplicate-delivery hardening, followed by automated infrastructure integration and dead-letter handling for terminal failures.

The project is now in a significantly stronger state for portfolio presentation, technical review, and CI integration.
