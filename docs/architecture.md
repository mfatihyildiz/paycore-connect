# PayCore Connect - Architecture Documentation

This document explains the current architecture of PayCore Connect in detail, including service responsibilities, communication patterns, persistence boundaries, payment correctness mechanisms, asynchronous delivery guarantees, consumer reliability, deployment model, and known architectural gaps.

---

## 1. Architectural Goal

PayCore Connect is designed as a microservices based payment orchestration platform.

The main architectural goal is to simulate how a real world payment infrastructure works when multiple independent services must cooperate to complete a payment lifecycle while preserving correctness across synchronous calls, database transactions, and asynchronous message delivery.

The system separates responsibilities into independent services:

* Merchant management and API key validation
* Payment orchestration
* Fraud analysis
* Legacy bank authorization
* Immutable payment event history
* Settlement calculation
* Notification processing
* Frontend access through API Gateway

Each service owns a clearly defined business responsibility and, where applicable, its own persistence model.

The architecture intentionally demonstrates both the normal payment path and distributed-system reliability concerns such as:

* HTTP idempotency
* Database business invariants
* Transactional outbox messaging
* At-least-once broker delivery
* Idempotent consumers
* Bounded retries
* Dead-letter handling
* Broker outage recovery
* Concurrent consumer safety

The project does not claim distributed exactly-once processing. Reliability is achieved through durable state, explicit transaction boundaries, at-least-once messaging, and idempotent business effects.

---

## 2. High-Level Architecture

```text
+--------------------------------------------------------------------------------------+
|                                   Client Layer                                       |
|--------------------------------------------------------------------------------------|
| React Merchant Dashboard                                                            |
+------------------------------------------+-------------------------------------------+
                                           |
                                           | HTTP
                                           v
+--------------------------------------------------------------------------------------+
|                                   Gateway Layer                                      |
|--------------------------------------------------------------------------------------|
| API Gateway                                                                          |
| - Single external entry point                                                        |
| - Routes requests to backend services                                                |
| - Hides internal service topology                                                    |
+------------------------------------------+-------------------------------------------+
                                           |
                                           v
+--------------------------------------------------------------------------------------+
|                                Microservices Layer                                   |
|--------------------------------------------------------------------------------------|
| Merchant Service          | Merchant and API key management                          |
| Payment Service           | Payment orchestration, idempotency, transactional outbox |
| Fraud Service             | Fraud scoring and fraud history                          |
| Legacy Bank SOAP Service  | SOAP based legacy bank simulation                       |
| Ledger Service            | Immutable event ledger and state reconstruction          |
| Settlement Service        | Merchant payout calculation                              |
| Notification Service      | Asynchronous notification processing                     |
+------------------------------------------+-------------------------------------------+
                                           |
                                           v
+--------------------------------------------------------------------------------------+
|                                Infrastructure Layer                                  |
|--------------------------------------------------------------------------------------|
| PostgreSQL | MongoDB | Redis | Kafka | RabbitMQ                                      |
+--------------------------------------------------------------------------------------+
```

The most important architectural distinction is that Payment Service does not directly treat Kafka or RabbitMQ publication as part of the payment database transaction.

Instead:

```text
Payment request
      |
      v
Payment Service
      |
      +--> PostgreSQL payment state
      |
      +--> PostgreSQL idempotency state
      |
      +--> PostgreSQL outbox_events
              |
              | after DB commit
              v
        Outbox Publisher
          |         |
          v         v
        Kafka    RabbitMQ
```

This removes the fragile database-to-broker dual-write from the request path.

---

## 3. Service Responsibility Overview

| Service | Responsibility | Storage / Infrastructure |
|---|---|---|
| API Gateway | Routes external requests to internal services | None |
| Merchant Service | Manages merchants, API keys, merchant status, and API key cache | PostgreSQL, Redis |
| Payment Service | Orchestrates payment lifecycle, HTTP idempotency, payment persistence, and outbox publication | PostgreSQL, Kafka, RabbitMQ |
| Fraud Service | Calculates fraud risk and stores fraud results | MongoDB |
| Legacy Bank SOAP Service | Simulates a SOAP based legacy bank provider | None |
| Ledger Service | Stores immutable payment events idempotently and reconstructs payment state | PostgreSQL, Kafka |
| Settlement Service | Calculates one settlement per authorized payment | PostgreSQL, Kafka |
| Notification Service | Processes notification jobs idempotently and stores notification logs | PostgreSQL, RabbitMQ |
| React Dashboard | Provides UI for testing and observing the flow | Browser runtime |

---

## 4. Runtime Architecture

```text
                                  +----------------------------+
                                  | React Merchant Dashboard   |
                                  | Port: 5173                 |
                                  +-------------+--------------+
                                                |
                                                | HTTP
                                                v
                                  +----------------------------+
                                  | API Gateway                |
                                  | Port: 8090                 |
                                  +-------------+--------------+
                                                |
             +----------------------------------+----------------------------------+
             |                                  |                                  |
             v                                  v                                  v
+------------------------+          +------------------------+          +------------------------+
| Merchant Service       |          | Payment Service        |          | Fraud Service          |
| Port: 8081             |          | Port: 8082             |          | Port: 8086             |
| PostgreSQL             |<--REST---| Orchestrator           |---REST-->| MongoDB                |
| Redis cache            |          | HTTP idempotency       |          | Risk scoring           |
+------------------------+          | Transactional outbox   |          +------------------------+
                                    +-----------+------------+
                                                |
                                                | SOAP
                                                v
                                    +------------------------+
                                    | Legacy Bank SOAP       |
                                    | Port: 8084             |
                                    +------------------------+

                                    +------------------------+
                                    | Payment PostgreSQL     |
                                    |------------------------|
                                    | payments               |
                                    | idempotency_records    |
                                    | outbox_events          |
                                    +-----------+------------+
                                                |
                                                | scheduled outbox relay
                              +-----------------+-----------------+
                              |                                   |
                              v                                   v
                    +----------------------+            +----------------------+
                    | Kafka                |            | RabbitMQ             |
                    | payment-events       |            | notification.exchange|
                    +----------+-----------+            +----------+-----------+
                               |                                   |
                  +------------+------------+                      v
                  |                         |            +----------------------+
                  v                         v            | payment.notification |
        +--------------------+    +--------------------+ | .queue               |
        | Ledger Service     |    | Settlement Service | +----------+-----------+
        | Port: 8083         |    | Port: 8085         |            |
        | PostgreSQL         |    | PostgreSQL         |            v
        +---------+----------+    +---------+----------+  +----------------------+
                  |                         |             | Notification Service |
                  | terminal failures       | terminal    | Port: 8087           |
                  v                         v             | PostgreSQL           |
    +---------------------------+  +---------------------------+------------------+
    | payment-events.ledger.DLT |  | payment-events.settlement.DLT              |
    +---------------------------+  +---------------------------+------------------+

                                                            terminal / fatal failure
                                                                    |
                                                                    v
                                                        +------------------------+
                                                        | notification.dlx       |
                                                        +-----------+------------+
                                                                    |
                                                                    v
                                                        +------------------------+
                                                        | payment.notification   |
                                                        | .dlq                   |
                                                        +------------------------+
```

Kafka and RabbitMQ are both asynchronous infrastructure components, but they serve different architectural roles:

* Kafka carries payment lifecycle domain events to independent event consumers.
* RabbitMQ carries task-oriented notification jobs to a worker-style consumer.

---

## 5. Layer Details

### 5.1 Client Layer

The client layer is represented by the React Merchant Dashboard.

The dashboard is used to:

* List merchants
* Select a merchant
* Display merchant API key
* Initiate payment requests
* Select payment provider
* Observe payment result
* Observe ledger state
* Observe fraud result
* Observe settlement result
* Observe notification logs

The dashboard does not call backend services directly. It sends all API requests to the API Gateway.

---

### 5.2 Gateway Layer

The API Gateway is the single external backend entry point.

Frontend base API URL:

```text
http://localhost:8090
```

The gateway routes requests based on path patterns:

| Path | Target Service |
|---|---|
| `/api/merchants/**` | Merchant Service |
| `/api/payments/**` | Payment Service |
| `/api/ledger/**` | Ledger Service |
| `/api/settlements/**` | Settlement Service |
| `/api/fraud/**` | Fraud Service |
| `/api/notifications/**` | Notification Service |

The API Gateway prevents the frontend from depending on internal service addresses and provides a future enforcement point for authentication, rate limiting, request logging, and other cross-cutting policies.

---

### 5.3 Business Services Layer

The business services layer contains domain-specific microservices.

The design intentionally avoids placing merchant, payment, fraud, ledger, settlement, and notification responsibilities in one application.

Each service owns a specific business capability and communicates through REST, SOAP, Kafka, or RabbitMQ depending on whether the interaction is synchronous or asynchronous.

---

### 5.4 Infrastructure Layer

The infrastructure layer provides relational persistence, document persistence, caching, event streaming, and task queueing.

| Technology | Used For |
|---|---|
| PostgreSQL | Payments, idempotency records, outbox events, merchant data, ledger events, settlements, notification logs |
| MongoDB | Fraud check documents and evolving fraud metadata |
| Redis | Merchant API key cache |
| Kafka | Payment lifecycle domain events |
| RabbitMQ | Background notification jobs and notification dead-letter routing |

---

## 6. Main Payment Flow

The main business operation is payment initiation.

`POST /api/payments/initiate` requires both a merchant API key and an `Idempotency-Key`.

```text
1. User initiates a payment from React Dashboard.

2. React sends the request to API Gateway.

3. API Gateway routes the request to Payment Service.

4. Payment Service validates the Idempotency-Key format.

5. Payment Service validates the merchant API key through Merchant Service.

6. Payment Service creates a semantic SHA-256 fingerprint from:
   - normalized amount
   - normalized currency
   - orderId
   - cardToken
   - providerType

7. Payment Service atomically acquires the idempotency key in PostgreSQL using:
   merchant_id + operation + idempotency_key

8. Existing idempotency state is handled as follows:
   - same key + same request + COMPLETED -> replay stored PaymentResponse
   - same key + different request -> 409 Conflict
   - same key + same request + PROCESSING -> 409 Conflict

9. Payment Service checks the business-level duplicate order invariant.

10. Payment Service creates the INITIATED payment and PAYMENT_INITIATED Kafka
    outbox row in the same PostgreSQL transaction.

11. The database transaction commits.

12. Payment Service calls Fraud Service outside the payment database transaction.

13. Fraud Service calculates the risk score and stores the fraud result in MongoDB.

14. If fraud rejects the payment:
    - Payment Service finalizes the payment as FAILED.
    - The same transaction stores the final Kafka event and notification outbox row.

15. If fraud allows the payment to continue:
    - Payment Service calls MOCK_BANK or LEGACY_BANK_SOAP outside the database transaction.

16. Payment Service finalizes the payment as AUTHORIZED or FAILED.

17. The finalization transaction atomically stores:
    - final payment state
    - final Kafka outbox event
    - RabbitMQ notification outbox event

18. Payment Service marks the idempotency record COMPLETED and stores:
    - payment ID
    - serialized PaymentResponse

19. The HTTP request can complete without waiting for Kafka or RabbitMQ availability.

20. The scheduled outbox publisher claims eligible PENDING rows.

21. Broker I/O occurs outside the claim transaction.

22. Successful messages become PUBLISHED.

23. Failed messages return to PENDING with retry metadata and exponential backoff.

24. Ledger and Settlement consume Kafka payment events independently.

25. Notification Service consumes RabbitMQ notification jobs.

26. React Dashboard retrieves payment, fraud, ledger, settlement, and notification outputs.
```

---

## 7. Communication Patterns

PayCore Connect uses multiple communication patterns because different interactions have different consistency and latency requirements.

### 7.1 REST

REST is used for synchronous request-response communication.

```text
React Dashboard -> API Gateway
API Gateway -> Backend services
Payment Service -> Merchant Service
Payment Service -> Fraud Service
```

REST is used when the caller requires an immediate result before it can continue.

Current synchronous REST clients are deliberately kept outside Payment Service database transactions.

---

### 7.2 SOAP

SOAP is used for legacy banking integration.

```text
Payment Service -> Legacy Bank SOAP Service
```

The SOAP provider adapter demonstrates how a modern Spring Boot service can integrate with a legacy XML/WSDL based banking interface.

The provider call is also kept outside the payment database transaction.

---

### 7.3 Transactional Outbox

Kafka and RabbitMQ messages are not published directly as part of payment-state transactions.

Instead:

```text
BEGIN
  write payment state
  write outbox row
COMMIT

        |
        v

Outbox Publisher
  |         |
  v         v
Kafka    RabbitMQ
```

This provides atomicity between the local payment state transition and the logical message that must later be published.

The outbox supports both destinations through one durable `outbox_events` table.

Each outbox row contains, among other fields:

* stable event/notification ID
* aggregate type and aggregate ID
* event type
* destination type
* destination
* Kafka message key or RabbitMQ routing key
* exact serialized payload
* publication status
* claim/lease metadata
* retry metadata

---

### 7.4 Kafka

Kafka is used for payment domain events.

```text
Payment Service outbox
        |
        v
payment-events
   |           |
   v           v
Ledger      Settlement
Service      Service
```

Kafka message keys use the payment ID so payment lifecycle events for the same payment are consistently keyed.

Ledger and Settlement use independent Kafka consumer groups, so each service observes the event stream independently.

The current consumer groups are:

```text
ledger-service-group
settlement-service-group
```

---

### 7.5 RabbitMQ

RabbitMQ is used for task-oriented notification processing.

```text
Payment Service outbox
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

The payment path does not wait for notification processing.

The Payment outbox publisher uses persistent JSON messages, publisher confirms, mandatory publishing, and returned-message checks before marking a RabbitMQ outbox row as successfully published.

---

## 8. Payment Correctness and HTTP Idempotency

Payment initiation uses PostgreSQL-backed idempotency because duplicate payment processing is a correctness problem rather than only a caching problem.

### 8.1 Idempotency Scope

The idempotency uniqueness scope is:

```text
merchant_id + operation + idempotency_key
```

For payment initiation:

```text
operation = PAYMENT_INITIATION
```

This means two different merchants may safely use the same textual idempotency key.

---

### 8.2 Atomic Acquisition

Payment Service does not use an unsafe:

```text
exists()
then
insert()
```

pattern.

The idempotency reservation is acquired with an atomic PostgreSQL insert:

```sql
INSERT ...
ON CONFLICT (merchant_id, operation, idempotency_key)
DO NOTHING
```

The acquisition runs in a separate `REQUIRES_NEW` transaction so ownership is durably established before the payment workflow continues.

---

### 8.3 Request Fingerprint

The request fingerprint is a semantic SHA-256 hash.

The canonical input includes:

* normalized amount
* uppercase currency
* order ID
* card token
* provider type

The hash is stored in `request_hash`.

This allows the system to distinguish:

```text
same key + same logical request
```

from:

```text
same key + different request
```

---

### 8.4 Idempotency States

```text
PROCESSING -> COMPLETED
```

A completed record stores the payment ID and serialized response.

Behavior:

| Situation | Result |
|---|---|
| New idempotency key | Caller acquires ownership |
| Same key + same payload + COMPLETED | Stored response replayed |
| Same key + changed payload | `409 Conflict` |
| Same key while PROCESSING | `409 Conflict` |

For known-safe failures before an external provider outcome may have occurred, the reservation can be released.

If execution fails after provider authorization may already have happened, the record is intentionally not automatically released because blind retry could cause duplicate provider-side effects.

---

### 8.5 Duplicate Order Business Invariant

HTTP idempotency and duplicate-order protection are separate concerns.

The `payments` table enforces:

```text
UNIQUE (merchant_id, order_id)
```

This is the final database-level business invariant ensuring one payment record per merchant order.

A friendly pre-check exists in the service layer, but the database unique constraint is the final race-condition backstop.

---

## 9. Transactional Outbox Reliability

The Payment Service uses a PostgreSQL-backed transactional outbox.

### 9.1 Atomic Persistence

For payment initiation:

```text
BEGIN
  INSERT payment(status=INITIATED)
  INSERT outbox(PAYMENT_INITIATED)
COMMIT
```

For payment finalization:

```text
BEGIN
  UPDATE payment(status=AUTHORIZED/FAILED)
  INSERT outbox(final Kafka event)
  INSERT outbox(notification)
COMMIT
```

`PaymentOutboxService` requires an existing transaction, which prevents accidental outbox writes outside the payment state transaction.

---

### 9.2 Stable Payload and Stable Logical ID

The Kafka event or RabbitMQ notification is serialized once when the outbox row is created.

The outbox row ID is also the stable logical event or notification ID.

Retries therefore resend the stored JSON rather than rebuilding a new payload from mutable payment state.

This is important for downstream idempotency.

---

### 9.3 Claim and Lease Model

Outbox rows use:

```text
PENDING -> PROCESSING -> PUBLISHED
```

Eligible rows are claimed with:

```sql
FOR UPDATE OF e SKIP LOCKED
```

The claim stores:

* `lock_owner`
* `processing_started_at`

The claim transaction is short.

Kafka or RabbitMQ network I/O happens after that transaction completes.

This avoids holding database locks while waiting for brokers.

---

### 9.4 Stale Claim Recovery

If a publisher process crashes after claiming a row, the row can remain `PROCESSING`.

A lease timeout allows stale claims to be returned to `PENDING`.

The default configured lease timeout is:

```text
120 seconds
```

---

### 9.5 Publication Retry Metadata

Failed outbox publication records:

* `attempt_count`
* `last_attempt_at`
* `next_attempt_at`
* `last_error`

Retry delay uses exponential backoff.

Current defaults:

```text
base delay: 5 seconds
maximum delay: 60 seconds
```

The scheduler only reclaims rows whose `next_attempt_at` is due.

---

### 9.6 Same-Payment Kafka Ordering

For the same aggregate and Kafka destination, a later event is not claimable while an earlier event remains non-published.

This preserves the intended lifecycle order:

```text
PAYMENT_INITIATED
        |
        v
PAYMENT_AUTHORIZED / PAYMENT_FAILED
```

This ordering rule is scoped by aggregate and destination. RabbitMQ notification publication is independent because it uses a different destination.

---

### 9.7 Broker Success Conditions

Kafka publication is considered successful only after the Kafka send future succeeds.

RabbitMQ publication requires:

* persistent message
* publisher confirm
* no mandatory returned message

Only after the broker-specific success condition is met does the outbox row become `PUBLISHED`.

---

### 9.8 Delivery Semantics

The outbox provides **at-least-once publication**.

A broker may accept a message and the application may fail before PostgreSQL records the outbox row as `PUBLISHED`.

The same logical message can therefore be published again.

The architecture handles this intentionally through idempotent consumers.

It does not claim exactly-once delivery.

---

## 10. Consumer Reliability and Idempotency

All three asynchronous consumers are designed around the same principle:

```text
at-least-once delivery
        +
database-enforced idempotent business effect
```

Expected duplicate delivery is treated as a no-op.

Unexpected processing failures are allowed to propagate so the messaging framework can retry or dead-letter the message.

---

### 10.1 Ledger Service

Ledger consumes all payment lifecycle events.

The ledger stores an immutable event history and supports state reconstruction.

The business uniqueness key is:

```text
event_id
```

The write path uses:

```sql
INSERT ...
ON CONFLICT (event_id) DO NOTHING
```

This removes the race condition of an application-level:

```text
exists -> save
```

sequence.

Multiple concurrent deliveries of the same event therefore produce one ledger row.

The service is best described as:

**Immutable event ledger with state reconstruction**

It is not presented as full event sourcing for the entire platform.

---

### 10.2 Settlement Service

Settlement only creates payout data for:

```text
PAYMENT_AUTHORIZED
```

Two separate database invariants are enforced:

```text
UNIQUE (event_id)
UNIQUE (payment_id)
```

This protects against both:

* repeated delivery of the same Kafka event
* different authorized event IDs being delivered for the same payment

The writer uses:

```sql
INSERT ...
ON CONFLICT DO NOTHING
```

A payment therefore produces at most one settlement record under the current schema.

---

### 10.3 Notification Service

Notification messages contain a stable:

```text
notificationId
```

The notification database enforces:

```text
UNIQUE (notification_id)
```

The write path uses an insert-if-absent operation.

If a duplicate notification is delivered, processing becomes a no-op rather than creating another notification log.

For a new notification, the service creates the log as `RECEIVED` and then marks it `SENT` within the service transaction.

If the `SENT` transition fails, the transaction fails so the broker can retry the message instead of leaving a silently accepted partial effect.

---

## 11. Kafka Retry and Dead-Letter Architecture

Ledger and Settlement use bounded Kafka retries followed by service-specific dead-letter topics.

### 11.1 Common Consumer Configuration

Both consumers use:

* `enable-auto-commit: false`
* record-level acknowledgment
* `ErrorHandlingDeserializer`
* Jackson JSON deserialization
* `DefaultErrorHandler`
* exponential backoff

Current retry policy:

```text
initial interval: 1 second
multiplier:       2.0
maximum interval: 4 seconds
max retries:      3
```

For a retryable processing failure the effective progression is:

```text
initial attempt
    |
    +-- 1s --> retry
    |
    +-- 2s --> retry
    |
    +-- 4s --> retry
    |
    v
dead-letter recovery if still failing
```

---

### 11.2 Ledger DLT

Ledger terminal failures are routed to:

```text
payment-events.ledger.DLT
```

The DLT publisher supports both:

* deserialized `PaymentEvent`
* raw `byte[]` payloads

This allows poison messages that fail deserialization to preserve their raw payload when dead-lettered.

---

### 11.3 Settlement DLT

Settlement terminal failures are routed to:

```text
payment-events.settlement.DLT
```

Ledger and Settlement deliberately use different DLTs because they have independent consumer groups and independent business responsibilities.

The same source Kafka record can fail for one service and succeed for another.

---

### 11.4 DLT Publication Reliability

The dedicated DLT producers use:

* `acks=all`
* Kafka producer idempotence
* fail-on-DLT-send-error behavior
* bounded wait for the DLT send result
* Kafka `max.block.ms` aligned with the configured DLT publish timeout

This prevents dead-letter publication from being treated as successful when the DLT producer itself fails and limits producer-side blocking around metadata/buffer availability.

---

### 11.5 Deserialization Failures

Malformed Kafka JSON is treated differently from a normal business-processing failure.

A malformed payload cannot be converted to `PaymentEvent`.

`ErrorHandlingDeserializer` allows that failure to enter the error-handling path, and the raw payload can be sent to the service-specific DLT without executing the consumer business method.

---

## 12. RabbitMQ Retry and Dead-Letter Architecture

Notification Service uses bounded listener retry and RabbitMQ dead-letter routing.

### 12.1 Main Topology

```text
notification.exchange
        |
        | payment.notification.created
        v
payment.notification.queue
        |
        v
Notification Service
```

The main queue is configured with:

```text
x-dead-letter-exchange    = notification.dlx
x-dead-letter-routing-key = payment.notification.dead
```

---

### 12.2 Retry Policy

Notification listener retry is bounded.

Current configuration:

```text
retry enabled:     true
max retries:       3
initial interval:  1 second
multiplier:        2.0
maximum interval:  4 seconds
stateless retry:   true
```

The listener uses automatic acknowledgment.

A successful listener invocation is acknowledged.

A processing exception remains visible to the container and enters retry handling.

---

### 12.3 Dead-Letter Topology

After terminal rejection, RabbitMQ routes the message through:

```text
notification.dlx
        |
        | payment.notification.dead
        v
payment.notification.dlq
```

The main listener also uses:

```text
default-requeue-rejected: false
```

so terminal failures do not enter an infinite broker requeue loop.

---

### 12.4 Malformed Notification Messages

A malformed JSON payload can fail message conversion before the business listener method executes.

Such poison messages are rejected and routed through the configured dead-letter exchange rather than being processed as valid notification jobs.

---

## 13. Database Ownership and Schema Management

Each service owns its own logical data model.

| Service | Database | Schema Management |
|---|---|---|
| Merchant Service | PostgreSQL | Hibernate `ddl-auto: update` currently |
| Payment Service | PostgreSQL | Flyway + Hibernate `validate` |
| Ledger Service | PostgreSQL | Flyway + Hibernate `validate` |
| Settlement Service | PostgreSQL | Flyway + Hibernate `validate` |
| Notification Service | PostgreSQL | Flyway + Hibernate `validate` |
| Fraud Service | MongoDB | MongoDB document model |

In local Docker Compose, the relational services share one PostgreSQL server instance but use separate databases.

This is a simplified local-development implementation of database-per-service ownership.

---

### 13.1 Payment Schema

Payment Service currently manages migrations for:

```text
payments
idempotency_records
outbox_events
```

Important relational guarantees include:

```text
UNIQUE (merchant_id, order_id)
UNIQUE (merchant_id, operation, idempotency_key)
```

The outbox schema also contains claim/lease and retry metadata.

---

### 13.2 Ledger Schema

Ledger Service uses Flyway and validates its Hibernate mapping against the database.

The ledger schema enforces:

```text
UNIQUE (event_id)
```

---

### 13.3 Settlement Schema

Settlement Service uses Flyway and validates its schema.

It enforces:

```text
UNIQUE (event_id)
UNIQUE (payment_id)
```

and a settlement-status check constraint.

---

### 13.4 Notification Schema

Notification Service uses Flyway.

The original notification table was followed by a migration that introduced a stable `notification_id` and enforced:

```text
UNIQUE (notification_id)
```

This database constraint is the source of truth for duplicate notification protection.

---

### 13.5 Merchant Schema

Merchant Service still uses:

```text
ddl-auto: update
```

and does not yet use Flyway.

The JPA model marks merchant email and API key as unique, but schema ownership and concurrent registration handling are planned for the next persistence-hardening phase.

---

## 14. Why PostgreSQL?

PostgreSQL is used where transactional consistency and relational invariants matter.

It is suitable for:

* Merchant records
* Payment transactions
* HTTP idempotency state
* Transactional outbox state
* Immutable ledger events
* Settlement records
* Notification logs

The project relies on PostgreSQL features such as:

* unique constraints
* check constraints
* transactions
* `INSERT ... ON CONFLICT`
* `FOR UPDATE SKIP LOCKED`
* indexes for retry and lookup paths

PostgreSQL is not only storage in this architecture; it is also an important correctness boundary.

---

## 15. Why MongoDB?

MongoDB is used by Fraud Service.

Fraud data can contain flexible or evolving metadata such as:

* risk score
* triggered rules
* IP velocity
* card velocity
* fraud reasons
* future risk signals

A document model is useful because fraud metadata can evolve independently of a rigid relational schema.

The current Fraud Service persists a new fraud-check document for each check invocation.

---

## 16. Why Redis?

Redis is used by Merchant Service for API key caching.

Payment initiation validates the merchant API key before beginning the payment workflow.

Caching active merchant API-key information reduces repeated PostgreSQL lookups.

Current architectural intent:

```text
PostgreSQL = persistent merchant source of truth
Redis      = derived lookup cache
```

The current implementation still has cache-consistency and cache-failure behavior that will be hardened in a later phase.

---

## 17. Why Kafka?

Kafka is used for payment lifecycle domain events.

Examples:

```text
PAYMENT_INITIATED
PAYMENT_AUTHORIZED
PAYMENT_FAILED
```

Kafka is suitable because:

* payment events represent domain facts
* Ledger and Settlement consume independently
* consumers use separate consumer groups
* services do not need direct synchronous coupling
* event history can be inspected
* at-least-once delivery works with database-enforced idempotent consumers

Kafka is not used for notification jobs because notification delivery has a task/worker consumption model rather than a multi-consumer domain-event model.

---

## 18. Why RabbitMQ?

RabbitMQ is used for task-oriented notification jobs.

Notification processing should not block the payment response.

RabbitMQ provides:

* durable queueing
* publisher confirms
* mandatory-return detection
* listener retry
* dead-letter exchange routing
* dead-letter queue inspection

This makes it a good fit for background notification processing.

---

## 19. Why API Gateway?

The API Gateway provides:

* a single frontend entry point
* cleaner routing
* hidden internal service addresses
* a future place for authentication
* a future place for rate limiting
* a future place for centralized request policies

Without the gateway, the frontend would need to know every backend service address.

The gateway currently focuses on routing rather than security enforcement.

---

## 20. Why SOAP Integration?

SOAP integration is included because many financial institutions still expose legacy SOAP interfaces.

The Legacy Bank SOAP Service demonstrates:

* SOAP endpoint exposure
* WSDL generation
* XSD based contracts
* SOAP client usage from Payment Service
* integration of legacy protocols into a modern service architecture

The SOAP provider follows the same Payment Provider abstraction as the mock provider.

---

## 21. Deployment Model

The current deployment model is local Docker Compose orchestration.

Docker Compose starts:

* PostgreSQL
* Redis
* Kafka
* MongoDB
* RabbitMQ
* Merchant Service
* Payment Service
* Ledger Service
* Legacy Bank SOAP Service
* Settlement Service
* Fraud Service
* Notification Service
* API Gateway
* React dashboard

Main command:

```bash
docker compose up -d --build
```

The current Kafka deployment is a single-node local KRaft setup.

The current PostgreSQL deployment is one local server container hosting separate PayCore databases.

Persistent Docker volumes are used for PostgreSQL, Redis, Kafka, MongoDB, RabbitMQ, and frontend dependencies.

The current Compose file contains a PostgreSQL health check. The remaining service dependencies are primarily startup-order dependencies rather than full readiness orchestration.

---

## 22. Current Reliability Guarantees

The current architecture provides the following guarantees within the modeled system.

### Payment request boundary

```text
Required Idempotency-Key
PostgreSQL-backed atomic acquisition
Same-request completed response replay
Changed-payload conflict detection
Concurrent PROCESSING conflict detection
```

### Payment database boundary

```text
UNIQUE (merchant_id, order_id)
Payment state + logical broker message in same transaction
Short DB transactions
External network calls outside payment DB transactions
```

### Outbox boundary

```text
Durable PENDING rows
Concurrent-safe claiming with SKIP LOCKED
Lease recovery
Persistent retry metadata
Exponential backoff
Stable serialized payload
At-least-once broker publication
```

### Kafka consumer boundary

```text
Ledger event_id uniqueness
Settlement event_id uniqueness
Settlement payment_id uniqueness
Bounded retry
Service-specific DLTs
Poison-message dead-lettering
```

### RabbitMQ consumer boundary

```text
notification_id uniqueness
Bounded listener retry
No infinite rejected-message requeue
DLX + DLQ routing
```

These guarantees intentionally rely on database constraints and idempotent effects rather than exactly-once messaging claims.

---

## 23. Current Architectural Boundaries and Known Gaps

Several areas remain intentionally unfinished and are part of the next architecture phases.

### 23.1 Merchant Persistence Hardening

Merchant Service still uses Hibernate:

```text
ddl-auto: update
```

rather than Flyway-managed schema ownership.

Merchant creation currently performs an application-level email existence check before save.

The database unique constraint protects the schema, but concurrent same-email registration still needs explicit domain-level race handling and concurrency testing.

---

### 23.2 Redis Cache Correctness

Redis is intended to be a cache rather than the merchant source of truth.

The current implementation performs cache reads, writes, and evictions directly in merchant flows.

Future hardening should ensure:

* Redis failure does not unnecessarily make PostgreSQL-backed merchant operations unavailable
* cache reads fall back safely to PostgreSQL
* malformed cache entries do not become authoritative
* status changes and API-key rotation cannot leave unsafe stale positive cache entries

---

### 23.3 Synchronous Client Timeouts

Payment Service currently uses synchronous REST and SOAP clients without an explicit project-level timeout policy.

Explicit connect and response/read timeouts are still required for:

* Merchant Service REST calls
* Fraud Service REST calls
* Legacy Bank SOAP calls

Retries must not be added blindly before the side-effect semantics of each operation are safe.

---

### 23.4 Fraud Check Idempotency

Fraud Service currently persists a new fraud-check document for each `POST /api/fraud/check`.

A blind retry can therefore:

* create duplicate fraud records
* affect card velocity
* affect IP velocity
* potentially change the resulting risk score

Fraud checks should become idempotent per payment before automatic retries are introduced.

---

### 23.5 External Provider Unknown Outcome

The transactional outbox solves:

```text
Payment database -> Kafka / RabbitMQ
```

reliability.

It does not solve:

```text
Payment Service -> external payment provider -> Payment database
```

unknown outcomes.

Example:

```text
provider authorizes
      |
response is lost or application crashes
      |
Payment Service cannot safely know whether authorization occurred
```

Blind provider retry could create duplicate authorization.

Future provider resilience requires:

* provider idempotency key / request identity
* provider reference correlation
* explicit UNKNOWN or uncertain-outcome handling where appropriate
* reconciliation

---

### 23.6 Build and CI Structure

The root `pom.xml` currently defines project coordinates and Java properties but is not yet a Maven multi-module aggregator.

A root-level `mvn verify` therefore does not currently represent a full backend test build.

CI/CD and service-level integration-test orchestration are future work.

---

### 23.7 Observability

The services expose basic actuator health/info endpoints, but the architecture does not yet include a complete observability stack.

Still planned:

* structured correlation IDs
* distributed tracing
* OpenTelemetry
* Prometheus metrics
* Grafana dashboards
* centralized logs
* reliability-specific metrics for outbox, retries, DLTs, and DLQ

---

### 23.8 Security

The current API Gateway does not yet provide a full authentication/authorization layer.

Still planned:

* JWT based authentication
* role based access control
* gateway rate limiting
* secret-management improvements
* broader API-key exposure hardening

---

### 23.9 Production Deployment

The current platform is Docker Compose based.

It does not yet include:

* Kubernetes manifests
* readiness/liveness strategy for every service
* resource requests and limits
* production secret/config management
* multi-node Kafka/PostgreSQL production topology

---

## 24. Future Architecture Roadmap

The next architectural improvements should follow correctness dependencies rather than adding resilience mechanisms blindly.

### Phase 2 - Merchant Reliability

* Introduce Flyway for Merchant Service
* Change Merchant Hibernate schema mode from `update` to `validate`
* Make duplicate merchant registration race-safe
* Add PostgreSQL concurrency tests
* Treat Redis as a failure-tolerant derived cache
* Harden API-key invalidation and rotation consistency

### Phase 3 - Synchronous Integration Boundaries

* Add explicit connect timeouts
* Add explicit response/read timeouts
* Apply consistent timeout configuration to REST and SOAP clients

### Phase 4 - Side-Effect-Safe Resilience

* Make Fraud Service idempotent by payment
* Design provider-side idempotency
* Add uncertain provider outcome handling
* Add reconciliation
* Introduce operation-safe retries
* Add circuit breakers only where semantics are safe

### Phase 5 - Test Architecture and CI

* Convert root Maven project into an aggregator or add an equivalent build strategy
* Expand Testcontainers coverage
* Automate Kafka and RabbitMQ failure/recovery scenarios
* Automate duplicate-delivery and concurrency scenarios
* Add GitHub Actions or another CI pipeline
* Add service contract testing where useful

### Phase 6 - Observability

* Micrometer metrics
* Prometheus
* Grafana
* OpenTelemetry tracing
* Structured logging
* Correlation IDs
* Outbox backlog/retry metrics
* Consumer retry/DLT/DLQ metrics
* Provider and fraud latency metrics

### Phase 7 - Performance Engineering

* k6 load tests
* concurrent payment initiation tests
* outbox throughput tests
* consumer throughput tests
* failure-under-load scenarios

### Phase 8 - Security

* JWT authentication
* RBAC
* gateway rate limiting
* secret management
* API-key handling review
* sensitive-data review

### Phase 9 - Deployment

* Docker readiness improvements
* Kubernetes manifests
* probes
* resource limits
* config/secrets
* deployment documentation

---

## 25. Architectural Summary

PayCore Connect currently demonstrates a payment architecture with:

```text
Microservice separation
        +
REST and SOAP integration
        +
PostgreSQL transactional correctness
        +
HTTP idempotency
        +
Transactional outbox
        +
Kafka domain events
        +
RabbitMQ background jobs
        +
At-least-once publication
        +
Database-enforced idempotent consumers
        +
Bounded retries
        +
Kafka DLTs
        +
RabbitMQ DLQ
        +
Broker outage recovery
```

The main reliability model is:

```text
Do not depend on exactly-once delivery.

Persist important state durably.
Make ownership atomic.
Keep external I/O outside long DB transactions.
Publish through a durable outbox.
Assume asynchronous messages may be delivered more than once.
Enforce idempotent business effects at the database boundary.
Bound retries.
Dead-letter terminal failures.
```

The next architectural focus is Merchant Service persistence/cache correctness, followed by safe synchronous resilience and external-provider reconciliation.
