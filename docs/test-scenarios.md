# PayCore Connect — Test Documentation

## 1. Purpose

This document describes the current test strategy, automated coverage, integration-test coverage, manual Docker acceptance coverage, execution commands, and remaining test gaps for **PayCore Connect — Payment Orchestration Platform**.

PayCore Connect is a multi-service fintech backend platform that includes:

* payment orchestration
* merchant validation and Redis-backed API-key caching
* PostgreSQL-backed HTTP idempotency
* database-level duplicate-order protection
* transactional outbox publication
* Kafka payment domain events
* RabbitMQ notification jobs
* database-enforced idempotent consumers
* bounded consumer retry
* Kafka dead-letter topics
* RabbitMQ dead-letter exchange / queue
* fraud evaluation
* immutable ledger history and state reconstruction
* settlement calculation
* legacy SOAP bank integration
* API Gateway routing

The test strategy deliberately separates three levels of verification:

1. **Fast unit/controller tests**
2. **Automated PostgreSQL integration tests with Testcontainers**
3. **Manual Docker E2E acceptance against real Kafka, RabbitMQ, PostgreSQL, and the running service stack**

The current reliability model is intentionally:

```text
at-least-once delivery
        +
database-enforced idempotent business effects
```

The project does not claim exactly-once distributed processing.

---

## 2. Services Covered

| Service | Responsibility | Current Test Focus |
|---|---|---|
| `fraud-service` | Evaluates payment risk and stores fraud checks | Rule logic, persistence calls, controller validation |
| `merchant-service` | Manages merchants, API keys, status, and Redis cache | Service behavior, cache interaction, controller behavior |
| `payment-service` | Orchestrates idempotent payment initiation and transactional outbox publication | Payment workflow, HTTP idempotency, outbox correctness, producers, clients |
| `ledger-service` | Consumes Kafka events and stores immutable event history | Unit tests, consumer propagation, PostgreSQL concurrency idempotency IT, Kafka DLT acceptance |
| `settlement-service` | Consumes authorized Kafka events and creates settlement | Unit tests, consumer propagation, PostgreSQL concurrency idempotency IT, Kafka DLT acceptance |
| `notification-service` | Consumes RabbitMQ notification jobs and stores notification logs | Unit tests, consumer propagation, PostgreSQL concurrency idempotency IT, RabbitMQ DLQ acceptance |
| `legacy-bank-soap-service` | Simulates a legacy SOAP bank provider | SOAP endpoint business behavior |
| `api-gateway` | Routes requests and provides request logging/CORS configuration | Request logging test; application context smoke test currently disabled |

---

## 3. Test Strategy

### 3.1 Service Unit Tests

Unit tests validate business logic in isolation.

Typical dependencies are mocked:

* repositories
* Redis templates
* REST clients
* SOAP templates
* Kafka producers
* RabbitMQ templates
* outbox collaborators

These tests are intended to be fast and deterministic.

---

### 3.2 Controller Tests

Controller tests validate:

* endpoint mappings
* request validation
* response status codes
* JSON request/response handling
* exception-to-HTTP mappings

The services primarily use:

```java
MockMvcBuilders.standaloneSetup(...)
```

rather than loading a full web application context for focused controller behavior.

---

### 3.3 Payment Persistence and Outbox Coordination Tests

Payment Service tests verify that:

* payment state and outbox messages are coordinated through `PaymentPersistenceService`
* initiated payment persistence occurs before initiated outbox creation
* final payment state and final Kafka/RabbitMQ outbox rows are created together
* duplicate-order exceptions are scoped to the payment write
* outbox integrity errors are not incorrectly translated into duplicate-order errors

---

### 3.4 Outbox Reliability Tests

Unit coverage validates:

* stable message identity
* stored serialized payload
* claim/lease lifecycle
* stale claim recovery
* publication success handling
* retry metadata
* exponential backoff
* Kafka send completion
* RabbitMQ publisher confirms
* RabbitMQ mandatory returned-message handling

Manual Docker acceptance additionally verifies actual broker outage and recovery behavior.

---

### 3.5 Consumer Idempotency Tests

Consumer correctness is now tested at two levels.

**Unit level**

* duplicate writes are treated as normal no-op business outcomes
* unexpected processing exceptions propagate through consumer methods

**Real PostgreSQL integration level**

* Ledger: 10 concurrent writes of the same `event_id` produce exactly one row
* Settlement: concurrent duplicate event delivery produces one row
* Settlement: 10 distinct event IDs targeting one payment produce one row
* Notification: 10 concurrent deliveries of one `notification_id` produce one `SENT` row

These tests use Testcontainers and real PostgreSQL uniqueness semantics.

---

### 3.6 Consumer Retry and Dead-Letter Acceptance

Kafka and RabbitMQ terminal-failure handling has been manually validated against the real local broker containers.

The verified flows include:

* malformed Kafka JSON -> service-specific DLT
* persistent Ledger processing failure -> bounded retry -> Ledger DLT
* persistent Settlement processing failure -> bounded retry -> Settlement DLT
* transient Kafka consumer failure -> successful processing before retry budget is exhausted
* malformed RabbitMQ JSON -> notification DLQ
* persistent notification DB failure -> bounded retry -> DLQ
* transient notification DB failure -> later successful `SENT`
* terminal RabbitMQ failure does not cause infinite main-queue requeue

---

### 3.7 Client Wrapper Tests

Payment Service integration adapters are tested without starting external services.

Current wrappers:

* Merchant REST client
* Fraud REST client
* Legacy SOAP payment provider client

---

### 3.8 Manual Docker E2E Acceptance

Manual acceptance is used for behavior that currently depends on the complete local stack or real broker failure manipulation.

Verified areas include:

* payment HTTP idempotency
* concurrent same-key request behavior
* duplicate-order protection
* Kafka outbox outage and recovery
* RabbitMQ outbox outage and recovery
* Ledger duplicate Kafka delivery
* Settlement duplicate Kafka delivery
* Notification duplicate RabbitMQ delivery
* Kafka retry/DLT paths
* RabbitMQ retry/DLQ paths
* transient and permanent consumer failures

These scenarios are not yet fully automated in CI.

---

## 4. Test Environment

Recommended local environment:

| Component | Version / Notes |
|---|---|
| Java | Java 17+ |
| Build Tool | Maven |
| Unit Test Framework | JUnit 5 |
| Mocking | Mockito |
| Assertions | AssertJ |
| Spring Test | MockMvc, MockRestServiceServer, ReflectionTestUtils |
| Integration DB | Testcontainers PostgreSQL `16-alpine` |
| Serialization | Jackson / Java time support |
| Manual E2E | Docker Compose, PostgreSQL, Kafka, RabbitMQ, MongoDB, Redis |

The fast unit/controller suites generally do not require real infrastructure.

The following services additionally contain Testcontainers PostgreSQL integration tests:

```text
ledger-service
settlement-service
notification-service
```

Those integration tests are named `*IT` and are executed by Maven Failsafe during:

```powershell
mvn verify
```

The manual broker acceptance scenarios run against the Docker Compose stack.

---

## 5. Test Execution Commands

### 5.1 Fast Service Tests

Run standard unit/controller tests with:

```powershell
mvn test
```

Examples:

```powershell
cd "paycore-connect\backend\fraud-service"
mvn test
```

```powershell
cd "paycore-connect\backend\merchant-service"
mvn test
```

```powershell
cd "paycore-connect\backend\payment-service"
mvn test
```

```powershell
cd "paycore-connect\backend\ledger-service"
mvn test
```

```powershell
cd "paycore-connect\backend\settlement-service"
mvn test
```

```powershell
cd "paycore-connect\backend\notification-service"
mvn test
```

```powershell
cd "paycore-connect\backend\legacy-bank-soap-service"
mvn test
```

```powershell
cd "paycore-connect\backend\api-gateway"
mvn test
```

---

### 5.2 Unit + Testcontainers Integration Tests

For Ledger, Settlement, and Notification, use:

```powershell
mvn verify
```

This executes normal tests and Maven Failsafe `*IT` integration tests.

Examples:

```powershell
cd "paycore-connect\backend\ledger-service"
mvn verify
```

```powershell
cd "paycore-connect\backend\settlement-service"
mvn verify
```

```powershell
cd "paycore-connect\backend\notification-service"
mvn verify
```

Docker must be available because Testcontainers starts a real PostgreSQL container.

---

### 5.3 Targeted Unit Test Execution

Examples:

```powershell
mvn -Dtest=PaymentServiceTest test
```

```powershell
mvn -Dtest=PaymentControllerTest test
```

```powershell
mvn -Dtest=PaymentOutboxServiceTest,PaymentPersistenceServiceTest,OutboxClaimServiceTest,OutboxPublisherServiceTest test
```

```powershell
mvn -Dtest=PaymentEventConsumerTest test
```

```powershell
mvn -Dtest=PaymentNotificationConsumerTest test
```

---

### 5.4 Targeted Failsafe Integration Test Execution

Examples:

```powershell
mvn -Dit.test=LedgerConsumerIdempotencyIT verify
```

```powershell
mvn -Dit.test=SettlementConsumerIdempotencyIT verify
```

```powershell
mvn -Dit.test=NotificationConsumerIdempotencyIT verify
```

---

# 6. Detailed Test Coverage

## 6.1 fraud-service

### Test Classes

| Test Class | Target |
|---|---|
| `FraudServiceTest` | `FraudService` |
| `FraudControllerTest` | `FraudController` |

### FraudServiceTest Coverage

Covered scenarios include:

* low-risk payment produces `APPROVED`
* high amount increases risk
* card velocity increases risk
* IP velocity increases risk
* combined signals can produce high risk
* high risk produces `REJECTED`
* medium risk produces `REVIEW`
* fraud check is persisted
* payment/merchant identifiers are mapped correctly
* fraud checks can be queried by payment
* fraud checks can be queried by merchant

### Fraud Rules Covered

| Rule | Trigger |
|---|---|
| `HIGH_AMOUNT` | Amount exceeds configured threshold |
| `CARD_VELOCITY_LIMIT` | Recent card attempts exceed threshold |
| `IP_VELOCITY_LIMIT` | Recent IP attempts exceed threshold |

### FraudControllerTest Coverage

Endpoints:

```http
POST /api/fraud/check
GET /api/fraud/payments/{paymentId}
GET /api/fraud/merchants/{merchantId}
```

Covered behavior includes:

* successful fraud check
* invalid request -> `400`
* payment history lookup
* merchant history lookup
* invalid UUID -> `400`

### Current Test Gap

Fraud Service does not yet have idempotency by `paymentId`.

Because one invocation currently creates one new fraud document, automatic retry semantics are intentionally not tested as safe behavior yet.

This is part of a later reliability phase.

---

## 6.2 merchant-service

### Test Classes

| Test Class | Target |
|---|---|
| `MerchantServiceTest` | `MerchantService` |
| `MerchantControllerTest` | `MerchantController` |

### MerchantServiceTest Coverage

Current unit coverage includes:

* merchant creation
* duplicate-email service behavior
* API-key generation
* active merchant cache write
* merchant listing
* merchant lookup
* missing merchant
* merchant status update
* cache eviction during status change
* active merchant cache refresh
* API-key regeneration
* old key eviction
* new key caching for active merchant
* Redis cache hit
* malformed Redis cached value behavior
* PostgreSQL fallback on cache miss
* inactive merchant validation
* null/blank API-key handling

### MerchantControllerTest Coverage

Endpoints:

```http
POST /api/merchants
GET /api/merchants
GET /api/merchants/{merchantId}
PATCH /api/merchants/{merchantId}/status
POST /api/merchants/{merchantId}/api-key
GET /api/merchants/validate-api-key
```

Covered behavior includes:

* `201 Created`
* request validation
* duplicate-email `409`
* listing and lookup
* missing merchant `404`
* status update
* API-key regeneration
* validation response
* missing API-key header `400`

### Current Test Gaps

Merchant Service is the next persistence-hardening target.

Not yet covered by real PostgreSQL concurrency tests:

```text
concurrent same-email registration
```

Not yet covered by failure-mode integration tests:

```text
Redis unavailable during validation
Redis unavailable during merchant mutation
stale cache after status/API-key changes
```

Merchant Service also still uses Hibernate `ddl-auto: update`; Flyway migration tests will be added in the Merchant reliability phase.

---

## 6.3 payment-service

### Test Classes

| Test Class | Target |
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

The service test validates orchestration while collaborators are mocked.

Covered scenarios include:

* idempotency-key validation
* semantic request fingerprint calculation
* durable idempotency ownership handling
* completed-response replay
* replay skips new payment/fraud/provider side effects
* same key + changed payload rejection
* same key while `PROCESSING` rejection
* duplicate-order handling
* safe idempotency reservation release for duplicate order
* merchant API-key validation
* amount/currency/card normalization
* initiated persistence delegation
* fraud rejection -> `FAILED`
* provider approval -> `AUTHORIZED`
* provider rejection -> `FAILED`
* provider response field persistence
* idempotency completion for final outcomes
* payment lookup by payment ID
* merchant payment lookup

Kafka and RabbitMQ are intentionally not called directly by `PaymentService` during the HTTP request flow.

---

### PaymentControllerTest Coverage

Endpoints:

```http
POST /api/payments/initiate
GET /api/payments/{paymentId}
GET /api/payments/merchant/{merchantId}
```

Covered behavior includes:

* valid payment -> `201 Created`
* required `Idempotency-Key`
* key forwarding to service
* invalid key -> `400`
* reused key -> `409`
* in-progress key -> `409`
* missing `X-API-Key` -> `400`
* invalid merchant API key -> `401`
* duplicate order -> `409`
* request validation
* payment query endpoints

---

### Payment HTTP Idempotency Contract

Required headers:

```http
X-API-Key: <merchant-api-key>
Idempotency-Key: <unique-key-for-this-logical-payment>
```

Verified behavior:

| Situation | Expected Result |
|---|---|
| New valid key | Processing starts |
| Same key + same body after completion | Stored response replay |
| Same key + changed body | `409 Conflict` |
| Same key while owner is processing | `409 Conflict` |
| Missing/blank/oversized key | `400 Bad Request` |
| Different key + same merchant/order | duplicate-order `409` |

The idempotency scope is:

```text
merchant_id + PAYMENT_INITIATION + idempotency_key
```

---

### PaymentOutboxServiceTest Coverage

Covered behavior:

* requires an active transaction
* payment ID must already exist
* Kafka destination mapping
* RabbitMQ destination mapping
* stable logical ID
* stored payload serialized at enqueue time
* later entity mutation does not alter stored payload
* new outbox rows start `PENDING`
* serialization errors propagate

---

### PaymentPersistenceServiceTest Coverage

Covered behavior:

* initiated payment saved before initiated outbox enqueue
* payment and outbox share the transaction
* final state creates final Kafka and RabbitMQ outbox records
* payment uniqueness failure -> `DuplicateOrderException`
* outbox integrity failure is not translated as duplicate order
* outbox `DataIntegrityViolationException` propagates

---

### OutboxClaimServiceTest Coverage

Covered behavior:

* bounded batch claiming
* `PENDING -> PROCESSING`
* lock owner assignment
* processing timestamp
* attempt count increment
* last attempt timestamp
* active retry eligibility clearing
* owner-aware `PUBLISHED` transition
* failed publication -> `PENDING`
* `next_attempt_at`
* `last_error`
* stale `PROCESSING` recovery

---

### OutboxPublisherServiceTest Coverage

Covered behavior:

* stale claim release before publication
* exact stored Kafka topic/key/payload
* exact stored Rabbit exchange/routing key/payload/outbox ID
* broker success -> `PUBLISHED`
* broker failure -> retry metadata
* exponential backoff
* diagnostic root failure storage
* no hot-loop retry
* empty batch -> no broker interaction

---

### PaymentEventProducerTest Coverage

Target:

```java
PaymentEventProducer.publish(topic, messageKey, payload)
```

Covered behavior:

* supplied topic
* supplied payment message key
* exact stored JSON payload
* no regenerated ID
* no regenerated timestamp
* Kafka send future returned to outbox publisher

---

### PaymentNotificationProducerTest Coverage

Target:

```java
PaymentNotificationProducer.publish(exchange, routingKey, payload, outboxEventId)
```

Covered behavior:

* supplied exchange/routing key
* exact stored JSON
* stable outbox correlation data
* persistent JSON delivery
* positive publisher confirm
* negative confirm failure
* mandatory returned-message failure
* no regenerated notification identity

---

### Provider / Client Tests

`MockBankPaymentProviderClientTest` covers:

* provider type
* approved amount
* provider reference
* limit rejection
* `LIMIT_EXCEEDED`
* response mapping

`PaymentProviderFactoryTest` covers:

* correct provider lookup
* provider registration by type
* unsupported provider handling

`LegacyBankSoapPaymentProviderClientTest` covers:

* provider type
* request-to-SOAP mapping
* approved/rejected SOAP result mapping
* configured SOAP URI
* merchant/amount/currency/order/card values

`MerchantClientTest` covers:

* merchant validation GET
* `X-API-Key`
* response mapping
* server error propagation

`FraudClientTest` covers:

* fraud POST
* JSON request
* decision/rule mapping
* server error propagation

### Current Payment Integration-Test Gap

Payment HTTP idempotency and concurrency have been verified against the real Docker PostgreSQL database, but Payment Service does not yet contain a dedicated Testcontainers integration test equivalent to the consumer `*IT` classes.

Automating those acceptance scenarios is still planned.

---

## 6.4 ledger-service

### Test Classes

| Test Class | Target |
|---|---|
| `LedgerServiceTest` | `LedgerService` |
| `LedgerControllerTest` | `LedgerController` |
| `PaymentEventConsumerTest` | `PaymentEventConsumer` |
| `LedgerConsumerIdempotencyIT` | Real PostgreSQL concurrent idempotency |

### LedgerServiceTest Coverage

Covered scenarios:

* new event is inserted
* duplicate event is a normal no-op
* payment event mapping
* payment history lookup
* merchant history lookup
* state reconstruction from ordered history
* final authorized state
* final failed state
* empty history -> `PaymentLedgerNotFoundException`

Ledger writes ultimately use database-enforced uniqueness on:

```text
event_id
```

---

### LedgerControllerTest Coverage

Endpoints:

```http
GET /api/ledger/payments/{paymentId}/events
GET /api/ledger/merchants/{merchantId}/events
GET /api/ledger/payments/{paymentId}/state
```

Covered behavior:

* payment event history
* merchant event history
* state reconstruction
* missing history -> `404`
* invalid UUID -> `400`

---

### PaymentEventConsumerTest Coverage

Covered behavior:

* valid event is delegated to `LedgerService`
* null event follows current delegate behavior
* processing exception is propagated rather than swallowed

The last point is important because Kafka retry/DLT handling requires the exception to escape the listener.

---

### LedgerConsumerIdempotencyIT

Technology:

```text
@SpringBootTest
Testcontainers
postgres:16-alpine
Maven Failsafe
```

Kafka listener startup is disabled for this database-focused integration test.

Scenario:

```text
same PaymentEvent
same event_id
10 concurrent threads
        |
        v
LedgerService.savePaymentEvent(...)
        |
        v
INSERT ... ON CONFLICT (event_id) DO NOTHING
```

Database assertion:

```text
COUNT(*) WHERE event_id = <same-id> == 1
```

This verifies race-safe idempotency using the real PostgreSQL constraint rather than mocks.

---

### Manual Kafka Duplicate Delivery Acceptance

The exact same Kafka event was published repeatedly.

Verified outcome:

```text
multiple deliveries
        |
        v
one payment_ledger_events row
```

This confirms the real Kafka delivery path and database idempotency work together.

---

## 6.5 settlement-service

### Test Classes

| Test Class | Target |
|---|---|
| `SettlementServiceTest` | `SettlementService` |
| `SettlementControllerTest` | `SettlementController` |
| `PaymentEventConsumerTest` | `PaymentEventConsumer` |
| `SettlementConsumerIdempotencyIT` | Real PostgreSQL concurrent idempotency |

### SettlementServiceTest Coverage

Covered scenarios:

* only `PAYMENT_AUTHORIZED` is processed
* other event types ignored
* existing settlement becomes no-op
* gross/commission/net calculations
* monetary rounding
* status `CALCULATED`
* settlement date
* payment lookup
* missing payment settlement -> exception
* merchant settlement list
* zero-value empty summary
* calculated merchant totals

Database correctness uses both:

```text
UNIQUE (event_id)
UNIQUE (payment_id)
```

---

### SettlementControllerTest Coverage

Endpoints:

```http
GET /api/settlements/payments/{paymentId}
GET /api/settlements/merchants/{merchantId}
GET /api/settlements/merchants/{merchantId}/summary
```

Covered behavior:

* successful payment settlement lookup
* missing settlement -> `404`
* merchant list
* merchant summary
* empty summary
* invalid UUID -> `400`

---

### PaymentEventConsumerTest Coverage

Covered behavior:

* incoming event delegates to `SettlementService`
* null event follows current delegate behavior
* processing exception propagates to Kafka error handling

---

### SettlementConsumerIdempotencyIT

Technology:

```text
@SpringBootTest
Testcontainers
postgres:16-alpine
Maven Failsafe
```

Kafka listener startup is disabled.

#### Scenario A — Same event concurrently

```text
same event_id
same payment_id
10 concurrent calls
```

Assertions:

```text
settlements where payment_id = ... -> 1
settlements where event_id = ...   -> 1
```

#### Scenario B — Different events for same payment concurrently

```text
10 different event_id values
same payment_id
10 concurrent calls
```

Assertion:

```text
settlements where payment_id = ... -> 1
```

This verifies both uniqueness rules under actual concurrent PostgreSQL writes.

---

### Manual Kafka Duplicate Delivery Acceptance

Verified broker-level scenarios include:

```text
same authorized event repeated
        -> one settlement

different event IDs for same payment
        -> one settlement
```

The second case verifies the `payment_id` business invariant independently of Kafka event identity.

---

## 6.6 notification-service

### Test Classes

| Test Class | Target |
|---|---|
| `NotificationServiceTest` | `NotificationService` |
| `NotificationControllerTest` class | `NotificationController` |
| `PaymentNotificationConsumerTest` | `PaymentNotificationConsumer` |
| `NotificationConsumerIdempotencyIT` | Real PostgreSQL concurrent idempotency |

### Source Hygiene Note

The controller test class is currently:

```java
class NotificationControllerTest
```

but the source filename is:

```text
NotificationController.java
```

under `src/test`.

The test still compiles because the class is package-private, but renaming the file to `NotificationControllerTest.java` is a later test-hygiene cleanup.

---

### NotificationServiceTest Coverage

Covered behavior includes:

* new notification row creation
* stable `notificationId`
* type `PAYMENT_RESULT`
* initial `RECEIVED`
* merchant webhook-style simulated target
* authorized payment message construction
* failed payment message construction
* simulated send -> `SENT`
* `sent_at`
* duplicate notification -> normal no-op
* mark-sent failure propagates
* payment history lookup
* merchant history lookup

The mark-sent failure test is important because a failure must roll back/propagate instead of acknowledging a partially completed business effect.

---

### NotificationControllerTest Coverage

Endpoints:

```http
GET /api/notifications/payments/{paymentId}
GET /api/notifications/merchants/{merchantId}
```

Covered behavior:

* payment notification list
* merchant notification list
* empty lists
* invalid payment UUID -> `400`
* invalid merchant UUID -> `400`

---

### PaymentNotificationConsumerTest Coverage

Covered behavior:

* incoming message delegates to `NotificationService`
* null message follows current delegate behavior
* processing exception propagates to RabbitMQ listener infrastructure

---

### NotificationConsumerIdempotencyIT

Technology:

```text
@SpringBootTest
Testcontainers
postgres:16-alpine
Maven Failsafe
```

RabbitMQ listener startup is disabled so the test focuses on the service/database correctness boundary.

Scenario:

```text
same notificationId
10 concurrent calls
        |
        v
NotificationService.processPaymentNotification(...)
```

Assertions:

```text
notification row count == 1
status == SENT
sent_at != null
stored payment_id == expected payment_id
```

This validates race-safe notification idempotency against real PostgreSQL.

---

### Manual RabbitMQ Duplicate Delivery Acceptance

The same valid notification JSON was published multiple times.

Verified outcome:

```text
one notification row
status = SENT
DLQ = empty
```

This verifies idempotency across the actual RabbitMQ consumer path.

---

## 6.7 legacy-bank-soap-service

### Test Class

| Test Class | Target |
|---|---|
| `LegacyBankEndpointTest` | `LegacyBankEndpoint` |

Covered scenarios:

* valid authorization approved
* bank reference generated
* high amount rejected
* `LIMIT_EXCEEDED`
* blank card token rejected
* `INVALID_CARD_TOKEN`
* response message mapping

Verified SOAP rules:

| Rule | Result |
|---|---|
| Amount `> 100000` | `LIMIT_EXCEEDED` |
| Null/blank card token | `INVALID_CARD_TOKEN` |
| Valid amount/token | approved |

---

## 6.8 api-gateway

### Current Active Focused Test

| Test Class | Target |
|---|---|
| `RequestLoggingFilterTest` | `RequestLoggingFilter` |

Covered behavior:

* request method/URI logging behavior
* delegation to next filter
* request processing not blocked by logging

### Important Documentation Correction

The current source tree does **not** contain the previously documented `CorsConfigTest`.

`CorsConfig` exists in production code, but dedicated CORS assertions are not currently present in the active test source.

This is therefore a real test gap rather than covered behavior.

---

## 6.9 Disabled Application Context Smoke Tests

Each backend service contains an `*ApplicationTests.java` context test file.

These are currently annotated:

```java
@Disabled("Disabled during service level unit testing")
```

They are therefore not part of the active regression signal.

Several of these files also contain the copy/paste class name:

```java
FraudServiceApplicationTests
```

even when they belong to another service.

This does not affect the current focused suites because the tests are disabled, but it is a test-hygiene issue to clean up later.

---

# 7. Exception Handling Coverage

## merchant-service

| Exception | Expected HTTP Status |
|---|---|
| `MerchantNotFoundException` | `404 Not Found` |
| `DuplicateMerchantEmailException` | `409 Conflict` |
| validation exception | `400 Bad Request` |

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
| request validation exception | `400 Bad Request` |

## ledger-service

| Exception | Expected HTTP Status |
|---|---|
| `PaymentLedgerNotFoundException` | `404 Not Found` |

## settlement-service

| Exception | Expected HTTP Status |
|---|---|
| `SettlementNotFoundException` | `404 Not Found` |

For asynchronous consumers, unexpected processing exceptions are intentionally **not** swallowed. They must propagate so Kafka/RabbitMQ retry handling can execute.

---

# 8. Validation Coverage

### Fraud Request

Validated fields include:

```text
paymentId
merchantId
amount
currency
orderId
cardToken
```

### Merchant Requests

Validated fields include:

```text
name
email
status
```

### Payment Request

Validated fields include:

```text
amount
currency
orderId
cardToken
providerType
```

### Idempotency Header

Verified behavior includes:

* missing header rejected
* null/blank rejected
* key over 255 characters rejected
* valid key scoped by merchant and operation

---

# 9. Database Schema and Migration Test Context

Flyway currently manages relational schema for:

```text
payment-service
ledger-service
settlement-service
notification-service
```

Hibernate uses:

```text
ddl-auto: validate
```

for those services.

The Testcontainers consumer integration tests boot the actual service context against a clean PostgreSQL container, so Flyway migration plus Hibernate validation are exercised as part of those `mvn verify` runs.

Merchant Service is not yet in this group; it still uses:

```text
ddl-auto: update
```

and is the next persistence-hardening target.

---

# 10. Event-Driven Testing Coverage

## 10.1 Payment Transactional Outbox

| Component | Focus |
|---|---|
| `PaymentOutboxService` | Stable payload/message creation inside business TX |
| `PaymentPersistenceService` | Payment state + outbox coordination |
| `OutboxClaimService` | Claim, lease, stale recovery, retry state |
| `OutboxPublisherService` | Broker dispatch, acknowledgment, backoff |
| `PaymentEventProducer` | Exact stored Kafka payload |
| `PaymentNotificationProducer` | Exact stored Rabbit payload, confirm/return |

---

## 10.2 Kafka Consumers

| Service | Idempotency Key | Automated DB Concurrency | Manual Broker Duplicate | Retry/DLT |
|---|---|---|---|---|
| Ledger | `event_id` | Yes | Verified | Verified |
| Settlement | `event_id` + `payment_id` | Yes | Verified | Verified |

Kafka consumers use record-level acknowledgment with auto-commit disabled.

Listener exceptions propagate into `DefaultErrorHandler`.

---

## 10.3 RabbitMQ Consumer

| Service | Idempotency Key | Automated DB Concurrency | Manual Broker Duplicate | Retry/DLQ |
|---|---|---|---|---|
| Notification | `notification_id` | Yes | Verified | Verified |

The listener uses automatic acknowledgment with bounded stateless retry and terminal rejection without requeue.

---

# 11. Kafka Retry and DLT Verification

Ledger and Settlement use service-specific DLTs:

```text
payment-events.ledger.DLT
payment-events.settlement.DLT
```

Current configured retry policy:

```text
max retries:          3
initial interval:     1000 ms
multiplier:           2.0
maximum interval:     4000 ms
DLT publish timeout:  5 seconds
```

Effective retry failure sequence is:

```text
initial processing attempt
        |
       1s
        v
retry 1
        |
       2s
        v
retry 2
        |
       4s
        v
retry 3
        |
        v
DLT recovery
```

---

## 11.1 Malformed JSON

Verified behavior:

```text
invalid JSON
   |
   v
ErrorHandlingDeserializer
   |
   v
service-specific DLT
```

The business consumer does not process the invalid object.

The DLT producer supports `byte[]`, allowing the raw malformed payload to be preserved.

---

## 11.2 Permanent Ledger Failure

A valid payment event was consumed while Ledger persistence was deliberately unavailable.

Verified:

* original attempt executed
* bounded retries executed
* failure remained visible
* message reached `payment-events.ledger.DLT`
* no false successful ledger row was created

---

## 11.3 Permanent Settlement Failure

Settlement database persistence was deliberately broken while a valid `PAYMENT_AUTHORIZED` message was consumed.

Verified:

* attempt 1
* retry after approximately 1 second
* retry after approximately 2 seconds
* retry after approximately 4 seconds
* terminal DLT publication
* no settlement row created

---

## 11.4 Transient Recovery

A temporary processing failure was restored before the retry budget expired.

Verified behavior:

```text
initial failure
    |
bounded retry
    |
dependency recovers
    |
business write succeeds
    |
no matching terminal DLT record
```

This was explicitly validated for Settlement and as part of the consumer recovery acceptance pass.

---

## 11.5 DLT Producer Hardening

The dedicated Ledger and Settlement DLT producers use:

```text
acks=all
enable.idempotence=true
failIfSendResultIsError=true
bounded send-result wait
bounded Kafka max.block.ms
```

The DLT `max.block.ms` setting is aligned with the configured DLT publish timeout so producer metadata/buffer blocking is also bounded.

The latest Ledger and Settlement test suites completed successfully after this hardening change.

---

# 12. RabbitMQ Retry and DLQ Verification

Notification topology:

```text
notification.exchange
        |
        v
payment.notification.queue
        |
        v
Notification Service
```

Dead-letter topology:

```text
notification.dlx
        |
        v
payment.notification.dlq
```

Routing key:

```text
payment.notification.dead
```

Current retry configuration:

```text
enabled:           true
max retries:       3
initial interval:  1s
multiplier:        2.0
max interval:      4s
stateless:         true
```

Terminal rejected messages use:

```text
default-requeue-rejected: false
```

---

## 12.1 Malformed JSON -> DLQ

A malformed JSON message was published to the main notification queue.

Verified:

* message did not produce a valid notification row
* main queue drained
* message reached `payment.notification.dlq`
* original malformed raw payload was observable in the DLQ

---

## 12.2 Permanent DB Failure -> DLQ

Notification persistence was deliberately made unavailable.

Verified:

* valid message was routed to the main queue
* listener retry budget was exhausted
* message reached DLQ
* no notification row was created

---

## 12.3 Transient DB Recovery

A fresh notification was published while persistence was temporarily unavailable.

The database was restored before retry exhaustion.

Verified final state:

```text
notification count = 1
status = SENT
sent_at is populated
DLQ = 0 for the message
```

---

## 12.4 Duplicate Delivery After Success

The same successful notification payload was published repeatedly.

Verified:

```text
DB notification count remains 1
status remains SENT
DLQ remains empty
```

This confirms the interaction between RabbitMQ at-least-once delivery and PostgreSQL notification idempotency.

---

# 13. Important Test Design Decisions

## 13.1 Database Constraints Are the Final Idempotency Authority

Consumer tests do not rely on an unsafe:

```text
exists()
then
save()
```

sequence for correctness.

Instead:

### Ledger

```sql
ON CONFLICT (event_id) DO NOTHING
```

### Settlement

```sql
ON CONFLICT DO NOTHING
```

with unique constraints on:

```text
event_id
payment_id
```

### Notification

```sql
ON CONFLICT (notification_id) DO NOTHING
```

The Testcontainers concurrency tests verify those guarantees under actual concurrent writes.

---

## 13.2 Listener Exceptions Must Propagate

Ledger, Settlement, and Notification listener tests explicitly verify that unexpected service exceptions escape the listener.

This is necessary because swallowing exceptions would prevent:

```text
retry
DLT
DLQ
```

from working correctly.

Expected duplicate messages are normal no-op business outcomes; unexpected infrastructure/business processing failures remain exceptions.

---

## 13.3 Stored Payload Instead of Rebuilding Messages

The transactional outbox serializes the logical event/notification once.

Retries resend:

* same logical ID
* same destination
* same key/routing key
* same JSON payload

This makes downstream idempotency deterministic.

---

## 13.4 Claim/Lease Rather Than Long Database Transactions

Outbox rows are claimed with:

```sql
FOR UPDATE ... SKIP LOCKED
```

and moved to `PROCESSING`.

Broker network calls happen after the claim transaction commits.

This prevents network latency from holding long PostgreSQL locks.

---

## 13.5 At-Least-Once, Not Exactly-Once

An outbox publisher can experience:

```text
broker accepted message
        |
application failed before marking PUBLISHED
```

The logical message may then be sent again.

Tests therefore focus on:

```text
duplicate delivery
        +
idempotent database effect
```

rather than claiming exactly-once broker delivery.

---

# 14. Manual Docker E2E Acceptance — Payment and Outbox

The payment acceptance pass used the full Docker Compose stack through:

```text
http://localhost:8090
```

## 14.1 Completed Response Replay

Same key + same completed request:

```text
same payment ID
no second payment row
no second provider/fraud side effect
```

---

## 14.2 Same Key with Different Payload

Expected:

```text
409 Conflict
```

---

## 14.3 New Key with Existing Merchant Order

Expected:

```text
409 Conflict
```

PostgreSQL verification:

```text
one payment row
temporary duplicate-order idempotency reservation released
```

---

## 14.4 Missing Idempotency Header

Expected:

```text
400 Bad Request
```

---

## 14.5 Concurrent Same-Key Requests

Ten concurrent identical requests were executed.

Observed behavior included:

* owner request processing
* concurrent `409` while state remained `PROCESSING`
* completed replays returning `201`
* all successful responses referencing the same payment ID

Database result:

```text
payments             = 1
idempotency_records  = 1 COMPLETED
```

---

## 14.6 Kafka Outage

Kafka was unavailable while the payment request path remained available.

Verified:

```text
HTTP 201
payment = AUTHORIZED
payment row committed
Kafka outbox rows remained retryable
RabbitMQ notification could publish independently
```

---

## 14.7 Kafka Recovery

Kafka was restarted without resubmitting the payment.

Verified final state:

```text
PAYMENT_INITIATED    KAFKA      PUBLISHED
PAYMENT_AUTHORIZED   KAFKA      PUBLISHED
PAYMENT_NOTIFICATION RABBITMQ   PUBLISHED
```

---

## 14.8 RabbitMQ Outage

RabbitMQ was unavailable while Kafka remained available.

Verified:

```text
payment still successful
Kafka rows publish
RabbitMQ row remains retryable
```

After broker recovery:

```text
same notification outbox row -> PUBLISHED
```

No payment replay was needed.

---

## 14.9 Retry Metadata / Backoff

Broker failures populated:

```text
attempt_count
last_attempt_at
next_attempt_at
last_error
```

Retries used bounded exponential backoff rather than a hot scheduler loop.

---

# 15. Manual Docker E2E Acceptance — Consumers

## 15.1 Ledger

Verified:

* exact duplicate event deliveries -> one ledger row
* malformed JSON -> Ledger DLT
* persistent processing failure -> retries then Ledger DLT
* listener exceptions remain visible to Kafka error handling

---

## 15.2 Settlement

Verified:

* duplicate exact event -> one settlement
* different event IDs for one payment -> one settlement
* malformed JSON -> Settlement DLT
* persistent DB failure -> bounded retry -> DLT
* temporary DB failure -> recovery before retry exhaustion -> one settlement, no terminal DLT

---

## 15.3 Notification

Verified:

* duplicate notification deliveries -> one `SENT` row
* malformed JSON -> DLQ
* permanent DB failure -> retry -> DLQ
* temporary DB failure -> eventual `SENT`
* terminal failure does not infinitely requeue on main queue

---

# 16. Current Automated Integration Coverage

| Area | Test Type | Status |
|---|---|---|
| Ledger concurrent same-event idempotency | Testcontainers PostgreSQL | Automated |
| Settlement concurrent same-event idempotency | Testcontainers PostgreSQL | Automated |
| Settlement different-events/same-payment idempotency | Testcontainers PostgreSQL | Automated |
| Notification concurrent same-notification idempotency | Testcontainers PostgreSQL | Automated |
| Payment same-key concurrency | Real Docker PostgreSQL | Manual |
| Kafka retry / DLT | Real Docker Kafka | Manual |
| Rabbit retry / DLQ | Real Docker RabbitMQ | Manual |
| Kafka/Rabbit broker outage recovery | Real Docker stack | Manual |
| Redis failure behavior | — | Not yet |
| Mongo/Fraud idempotency | — | Not yet |

---

# 17. Known Non-Goals and Remaining Gaps

The following are no longer future work and should **not** be described as missing:

```text
Ledger database-enforced idempotency
Settlement database-enforced idempotency
Notification database-enforced idempotency
Consumer concurrent PostgreSQL tests
Kafka bounded retry
Ledger DLT
Settlement DLT
RabbitMQ bounded retry
Notification DLQ
```

Remaining gaps are:

* payment idempotency Testcontainers automation
* automated Kafka/RabbitMQ broker failure tests in CI
* Merchant concurrent registration integration test
* Merchant Redis outage/cache-consistency tests
* Fraud idempotency tests
* explicit REST/SOAP timeout tests after timeout configuration is introduced
* provider unknown-outcome/reconciliation tests
* cross-service automated Docker E2E
* Kafka/RabbitMQ Testcontainers integration
* contract tests
* performance/load tests
* security tests
* observability/metrics tests
* CI pipeline
* root Maven multi-module aggregation

---

# 18. Recommended Next Test Improvements

## 18.1 Merchant Reliability Tests — Next Phase

When Merchant Service is moved to Flyway and race-safe persistence, add:

### PostgreSQL integration

```text
10 concurrent same-email registrations
        |
        v
one merchant row
others receive deterministic duplicate result
```

### Redis failure

Test:

* Redis unavailable during API-key validation
* DB fallback still works where intended
* malformed cache entry falls back safely
* status suspension invalidates old active cache
* API-key rotation invalidates old key
* Redis failure does not corrupt PostgreSQL transaction semantics

---

## 18.2 Payment Testcontainers Acceptance

Move current manual payment/PostgreSQL tests into repeatable integration tests:

* new idempotency key
* completed replay
* changed-payload conflict
* `PROCESSING` conflict
* 10 concurrent same-key requests
* new key + duplicate order
* reservation cleanup
* one payment row / one completed idempotency row

---

## 18.3 Broker Integration Automation

Automate current manual Kafka/RabbitMQ scenarios:

### Kafka

* malformed JSON -> DLT
* persistent consumer DB failure -> DLT
* transient consumer failure -> eventual success
* duplicate event delivery
* broker outage/recovery for Payment outbox

### RabbitMQ

* malformed JSON -> DLQ
* persistent notification DB failure -> DLQ
* transient DB recovery
* duplicate delivery
* broker outage/recovery for Payment outbox

---

## 18.4 Full Cross-Service E2E

Automate:

```text
Merchant
   |
   v
Payment initiation
   |
   v
Fraud
   |
   v
Provider
   |
   v
Payment + Outbox commit
   |
   +----> Kafka -> Ledger
   |            -> Settlement
   |
   +----> RabbitMQ -> Notification
```

Assertions should use eventual polling rather than fixed sleeps because the final three outputs are asynchronous.

---

## 18.5 CI Pipeline

A later CI pipeline should include:

```text
compile
unit/controller tests
Testcontainers integration tests
package
Docker build
selected E2E reliability checks
```

The root Maven project is not yet a true multi-module aggregator, so CI will initially need either service-specific jobs or an aggregator change.

---

## 18.6 Coverage Reporting

JaCoCo is not yet configured.

Reasonable targets once reporting is introduced:

| Layer | Suggested Target |
|---|---:|
| Service logic | 80%+ |
| Controller | 70%+ |
| Adapter / persistence / messaging | 70%+ |
| Overall | 75%+ |

Coverage percentages should be treated as a signal, not as a substitute for concurrency/failure-mode tests.

---

# 19. Test Hygiene Items

The following are not reliability blockers, but should eventually be cleaned up:

* rename Notification controller test file from `NotificationController.java` to `NotificationControllerTest.java`
* rename copy/pasted disabled `FraudServiceApplicationTests` classes to the actual service names
* decide whether disabled context-load tests should be removed or replaced with meaningful smoke tests
* add a dedicated `CorsConfigTest` if CORS behavior is intended to be explicitly regression-tested
* standardize unit vs integration test naming and execution documentation

---

# 20. Overall Coverage Summary

| Area | Status |
|---|---|
| Business service logic | Covered |
| REST controller behavior | Covered |
| Payment HTTP idempotency unit/controller logic | Covered |
| Payment HTTP idempotency real PostgreSQL acceptance | Manually verified |
| Concurrent same-key payment protection | Manually verified |
| Payment transactional outbox | Covered |
| Outbox claim/lease/stale recovery | Covered |
| Outbox retry/backoff | Covered + manually verified |
| Kafka producer stored-payload behavior | Covered |
| Rabbit publisher confirm/return behavior | Covered |
| Kafka broker outage/recovery | Manually verified |
| Rabbit broker outage/recovery | Manually verified |
| Ledger consumer idempotency | Unit + Testcontainers + broker acceptance |
| Settlement consumer idempotency | Unit + Testcontainers + broker acceptance |
| Notification consumer idempotency | Unit + Testcontainers + broker acceptance |
| Ledger Kafka retry/DLT | Manually verified |
| Settlement Kafka retry/DLT | Manually verified |
| Notification Rabbit retry/DLQ | Manually verified |
| Consumer exception propagation | Covered |
| REST/SOAP client wrapper behavior | Covered |
| Legacy SOAP endpoint behavior | Covered |
| Gateway request logging | Covered |
| Dedicated CORS regression test | Not currently present |
| Merchant real DB concurrency | Next phase |
| Automated broker integration in CI | Future |
| Full cross-service automated E2E | Future |
| Contract testing | Future |
| Load/performance testing | Future |
| Security testing | Future |

---

# 21. Final Assessment

The current test architecture now validates both sides of the messaging reliability model:

```text
PRODUCER SIDE

payment state
    +
transactional outbox
    +
broker acknowledgment
    +
outbox retry/recovery
```

and:

```text
CONSUMER SIDE

at-least-once delivery
    +
database-enforced idempotency
    +
exception propagation
    +
bounded retry
    +
DLT / DLQ
```

The strongest currently verified correctness properties are:

* PostgreSQL-backed payment HTTP idempotency
* concurrent same-key protection
* database-level duplicate-order protection
* atomic payment/outbox persistence
* stable asynchronous message identity
* outbox claim/lease recovery
* broker-independent payment completion
* Kafka and RabbitMQ outage recovery
* Ledger `event_id` idempotency under real concurrent PostgreSQL writes
* Settlement `event_id` and `payment_id` idempotency under real concurrent PostgreSQL writes
* Notification `notification_id` idempotency under real concurrent PostgreSQL writes
* duplicate broker delivery resulting in one business row
* Kafka bounded retry and separate Ledger/Settlement DLTs
* RabbitMQ bounded retry and notification DLQ
* poison-message handling
* transient consumer recovery
* permanent consumer-failure isolation

The next testing focus should move to **Merchant Service persistence/cache correctness**, followed by automated synchronous timeout/resilience tests and broader CI automation.

The project should continue to describe its messaging semantics as:

```text
at-least-once delivery
+
idempotent business effects
```

rather than exactly-once processing.
