# PayCore Connect

**PayCore Connect** is a microservices based payment orchestration platform built with Java, Spring Boot, React, PostgreSQL, MongoDB, Redis, Kafka, RabbitMQ, SOAP, REST, and Docker Compose.

The project simulates a real world payment infrastructure where merchants can initiate payments, validate API keys, route payment requests to mock or legacy banking providers, run fraud checks, publish payment events, create ledger records, calculate settlements, and generate notification logs.

The main purpose of this project is to demonstrate a production oriented backend architecture using synchronous and asynchronous communication patterns, multiple persistence technologies, API Gateway routing, event driven processing, SOAP integration, Flyway database migrations, PostgreSQL backed HTTP idempotency, transactional outbox messaging, broker outage recovery, and a React based dashboard.

---

## Table of Contents

* [Project Overview](#project-overview)
* [Architecture Summary](#architecture-summary)
* [Technology Stack](#technology-stack)
* [Microservices](#microservices)
* [System Architecture](#system-architecture)
* [Main Business Flow](#main-business-flow)
* [Communication Patterns](#communication-patterns)
* [Data Storage Design](#data-storage-design)
* [Payment Idempotency](#payment-idempotency)
* [Transactional Outbox](#transactional-outbox)
* [Event-Driven Architecture](#event-driven-architecture)
* [Fraud Detection Flow](#fraud-detection-flow)
* [SOAP Legacy Bank Integration](#soap-legacy-bank-integration)
* [React Merchant Dashboard](#react-merchant-dashboard)
* [Docker Compose Infrastructure](#docker-compose-infrastructure)
* [Project Structure](#project-structure)
* [How to Run](#how-to-run)
* [API Gateway Endpoints](#api-gateway-endpoints)
* [Example API Requests](#example-api-requests)
* [Service Ports](#service-ports)
* [Future Improvements](#future-improvements)

---

## Project Overview

PayCore Connect is designed as a simplified payment orchestration system.

In a real payment ecosystem, a merchant does not directly communicate with every bank, fraud system, ledger system, settlement engine, and notification provider separately. Instead, the merchant sends a payment request to a central payment orchestration platform.

This project follows the same idea.

The system allows merchants to:

* Register as a merchant.
* Generate and use API keys.
* Initiate payment requests with a required `Idempotency-Key`.
* Safely retry the same logical payment request without creating duplicate payments.
* Route payments to different payment providers.
* Run fraud checks before authorization.
* Store payment state and asynchronous broker messages atomically in PostgreSQL.
* Publish payment domain events to Kafka through a transactional outbox relay.
* Consume payment events for ledger and settlement processes.
* Publish notification jobs to RabbitMQ through the same outbox mechanism.
* Recover pending broker messages automatically after Kafka or RabbitMQ outages.
* Monitor the full flow from a React dashboard.

The platform includes both synchronous and asynchronous communication:

* **Synchronous REST calls** are used when an immediate response is required.
* **SOAP calls** are used to simulate legacy bank integration.
* **Kafka events** are used for event driven ledger and settlement processing and are published asynchronously from the PostgreSQL transactional outbox.
* **RabbitMQ messages** are used for asynchronous notification processing and are published through the same durable outbox relay.

---

## Architecture Summary

The system consists of the following main layers:

1. **Frontend Layer**

    * React Merchant Dashboard

2. **Gateway Layer**

    * API Gateway

3. **Core Business Services**

    * Merchant Service
    * Payment Service
    * Fraud Service
    * Ledger Service
    * Settlement Service
    * Notification Service
    * Legacy Bank SOAP Service

4. **Infrastructure Layer**

    * PostgreSQL
    * MongoDB
    * Redis
    * Kafka
    * RabbitMQ

---

## Technology Stack

### Backend

* Java 17
* Spring Boot
* Spring Web
* Spring Data JPA
* Spring Data MongoDB
* Spring Data Redis
* Spring Kafka
* Spring AMQP
* Spring Web Services
* Hibernate
* Flyway
* Maven

### Frontend

* React
* Vite
* Axios
* CSS

### Databases and Messaging

* PostgreSQL
* MongoDB
* Redis
* Apache Kafka
* RabbitMQ

### DevOps / Runtime

* Docker
* Docker Compose

---

## Microservices

### 1. Merchant Service

**Port:** `8081`

The Merchant Service manages merchant data and API key validation.

It is responsible for:

* Creating merchants.
* Listing merchants.
* Fetching merchant details.
* Updating merchant status.
* Generating merchant API keys.
* Validating API keys for payment requests.

This service uses:

* **PostgreSQL** for persistent merchant data.
* **Redis** for API key caching.

Redis is used because API key validation is a frequent operation. Instead of querying PostgreSQL on every payment request, the system can cache API key information and improve lookup performance.

Main endpoints:

```http
POST /api/merchants
GET /api/merchants
GET /api/merchants/{merchantId}
PATCH /api/merchants/{merchantId}/status
POST /api/merchants/{merchantId}/api-key
GET /api/merchants/validate-api-key
```

---

### 2. Payment Service

**Port:** `8082`

The Payment Service is the central orchestration service of the system.

It is responsible for:

* Receiving payment initiation requests.
* Requiring and validating an `Idempotency-Key` for payment initiation.
* Preventing duplicate processing for retried or concurrent requests.
* Replaying the previously completed payment response for a matching retry.
* Rejecting idempotency key reuse when the request payload changes.
* Validating merchant API keys through Merchant Service.
* Calling Fraud Service before payment authorization.
* Routing payments to the selected provider.
* Supporting mock and legacy SOAP bank providers.
* Persisting payment state in PostgreSQL.
* Enforcing one payment per `(merchant_id, order_id)` business key.
* Persisting Kafka and RabbitMQ messages into a transactional outbox in the same database transaction as payment state changes.
* Claiming and publishing pending outbox rows asynchronously.
* Preserving same-payment Kafka lifecycle ordering.
* Retrying broker failures with exponential backoff and durable retry metadata.
* Recovering stale publisher claims after a publisher crash or interruption.

This service uses:

* **PostgreSQL** for payment transaction records, durable idempotency state, and transactional outbox rows.
* **Flyway** for versioned payment database migrations.
* **REST** for merchant validation and fraud checks.
* **SOAP** for legacy bank authorization.
* **Kafka** for payment domain events.
* **RabbitMQ** for asynchronous notification jobs.

Kafka and RabbitMQ are not part of the request transaction. The request path commits payment state and outbox records first; a scheduled outbox publisher performs broker I/O afterwards.

Supported provider types:

```text
MOCK_BANK
LEGACY_BANK_SOAP
```

Main endpoints:

```http
POST /api/payments/initiate
GET /api/payments/{paymentId}
GET /api/payments/merchant/{merchantId}
```

---

### 3. Ledger Service

**Port:** `8083`

The Ledger Service consumes payment events from Kafka and stores an immutable event history.

It is responsible for:

* Consuming payment lifecycle events.
* Persisting event records.
* Reconstructing payment state from events.
* Providing event history per payment or merchant.

This service implements an **immutable event ledger with state reconstruction**.

Instead of only storing the current payment state, it stores the sequence of events that happened during the payment lifecycle and can reconstruct the latest state from that history.

Example events:

```text
PAYMENT_INITIATED
PAYMENT_AUTHORIZED
PAYMENT_FAILED
```

This service uses:

* **Kafka** as the event source.
* **PostgreSQL** as persistent event storage.

Main endpoints:

```http
GET /api/ledger/payments/{paymentId}/events
GET /api/ledger/payments/{paymentId}/state
GET /api/ledger/merchants/{merchantId}/events
```

---

### 4. Legacy Bank SOAP Service

**Port:** `8084`

The Legacy Bank SOAP Service simulates an old banking system that still exposes SOAP based integration.

It is responsible for:

* Receiving SOAP payment authorization requests.
* Returning approval or rejection responses.
* Simulating legacy financial system behavior.

This service is used by Payment Service when the provider type is:

```text
LEGACY_BANK_SOAP
```

SOAP endpoint:

```http
/ws
```

WSDL endpoint:

```http
/ws/legacyBank.wsdl
```

The service approves payments below the configured threshold and rejects high value transactions with a limit related response.

---

### 5. Settlement Service

**Port:** `8085`

The Settlement Service consumes authorized payment events and calculates merchant payouts.

It is responsible for:

* Listening to Kafka payment events.
* Processing only authorized payments.
* Calculating commission amounts.
* Calculating net settlement amounts.
* Storing settlement records.
* Providing settlement summaries per merchant.

This service uses:

* **Kafka** to consume payment events.
* **PostgreSQL** to store settlement records.

Example settlement calculation:

```text
Gross Amount: 1000.00 TRY
Commission Rate: 2.5%
Commission Amount: 25.00 TRY
Net Amount: 975.00 TRY
```

Main endpoints:

```http
GET /api/settlements/payments/{paymentId}
GET /api/settlements/merchants/{merchantId}
GET /api/settlements/merchants/{merchantId}/summary
```

---

### 6. Fraud Service

**Port:** `8086`

The Fraud Service performs risk checks before payment authorization.

It is responsible for:

* Evaluating payment risk.
* Checking high payment amounts.
* Checking card velocity.
* Checking IP velocity.
* Returning fraud decisions.
* Storing fraud check results.

This service uses:

* **MongoDB** for flexible fraud check documents.

MongoDB is used here because fraud check records can evolve over time. Fraud metadata may include dynamic fields, risk reasons, scoring details, velocity data, and additional signals. A document database is a better fit for this type of semi structured data.

Fraud decision examples:

```text
APPROVED
REVIEW
REJECTED
```

Main endpoints:

```http
POST /api/fraud/check
GET /api/fraud/payments/{paymentId}
GET /api/fraud/merchants/{merchantId}
```

---

### 7. Notification Service

**Port:** `8087`

The Notification Service processes asynchronous payment notification jobs.

It is responsible for:

* Consuming RabbitMQ messages.
* Processing payment notification requests.
* Storing notification logs.
* Providing notification history per payment or merchant.

This service uses:

* **RabbitMQ** as the message broker.
* **PostgreSQL** as notification log storage.

RabbitMQ is used because notification delivery is a background task. The payment flow should not wait for notification processing to finish.

Main endpoints:

```http
GET /api/notifications/payments/{paymentId}
GET /api/notifications/merchants/{merchantId}
```

---

### 8. API Gateway

**Port:** `8090`

The API Gateway is the single entry point for external clients and the React dashboard.

It is responsible for:

* Routing requests to internal microservices.
* Hiding internal service ports from the client.
* Providing a unified API entry point.
* Supporting frontend integration through a single base URL.

The frontend communicates only with:

```http
http://localhost:8090
```

Instead of calling each backend service directly.

Example route mappings:

```text
/api/merchants/**       -> merchant-service:8081
/api/payments/**        -> payment-service:8082
/api/ledger/**          -> ledger-service:8083
/api/settlements/**     -> settlement-service:8085
/api/fraud/**           -> fraud-service:8086
/api/notifications/**   -> notification-service:8087
```

---

## System Architecture

PayCore Connect follows a microservices-based architecture where each service owns a specific business capability and communicates with other services through REST, SOAP, Kafka, or RabbitMQ depending on the use case.

The system is designed around the following architectural layers:

```text
+----------------------------------------------------------------------------------+
|                                Client Layer                                      |
|----------------------------------------------------------------------------------|
| React Merchant Dashboard                                                         |
| - Used by merchants/admin users to create payments and inspect system outputs     |
+----------------------------------------------------------------------------------+
                                      |
                                      v
+----------------------------------------------------------------------------------+
|                              API Gateway Layer                                   |
|----------------------------------------------------------------------------------|
| API Gateway                                                                       |
| - Single entry point for the frontend                                             |
| - Routes requests to internal microservices                                       |
| - Hides internal service addresses from clients                                   |
+----------------------------------------------------------------------------------+
                                      |
                                      v
+----------------------------------------------------------------------------------+
|                              Core Business Layer                                 |
|----------------------------------------------------------------------------------|
| Merchant Service        | Manages merchants, API keys, and merchant status        |
| Payment Service         | Orchestrates payments and durable outbox publication    |
| Fraud Service           | Evaluates payment risk and stores fraud check results   |
| Ledger Service          | Stores immutable payment event history                  |
| Settlement Service      | Calculates merchant payouts from authorized payments    |
| Notification Service    | Processes asynchronous payment notification jobs        |
| Legacy Bank SOAP Service| Simulates a SOAP-based legacy banking provider          |
+----------------------------------------------------------------------------------+
                                      |
                                      v
+----------------------------------------------------------------------------------+
|                            Infrastructure Layer                                  |
|----------------------------------------------------------------------------------|
| PostgreSQL | Relational storage for merchants, payments, outbox, ledger, settlements, logs |
| MongoDB    | Document storage for fraud check records and risk metadata           |
| Redis      | API key cache for fast merchant validation                           |
| Kafka      | Event streaming for payment lifecycle events                         |
| RabbitMQ   | Queue-based background notification processing                       |
+----------------------------------------------------------------------------------+
```

---

### High-Level Request Flow

The frontend does not communicate with individual backend services directly.
All browser requests are sent to the API Gateway.

```text
React Dashboard
      |
      | HTTP
      v
API Gateway
      |
      +---------------------> Merchant Service
      |
      +---------------------> Payment Service
      |
      +---------------------> Ledger Service
      |
      +---------------------> Settlement Service
      |
      +---------------------> Fraud Service
      |
      +---------------------> Notification Service
```

This keeps the client-side integration simple because the frontend only needs one backend base URL:

```text
http://localhost:8090
```

---

### Payment Orchestration Flow

The Payment Service is the main orchestrator of the platform.

When a payment request is received, Payment Service coordinates durable HTTP idempotency, merchant validation, payment persistence, fraud checking, provider authorization, transactional outbox creation, and asynchronous broker publication.

```text
1. React Dashboard sends a payment request to API Gateway with:
   - X-API-Key
   - Idempotency-Key

2. API Gateway routes the request to Payment Service.

3. Payment Service validates the idempotency key format.

4. Payment Service validates the merchant API key by calling Merchant Service.

5. Merchant Service checks merchant data from PostgreSQL and may use Redis
   for faster API key lookup.

6. Payment Service calculates a semantic SHA-256 fingerprint for the request
   and atomically acquires the idempotency key in PostgreSQL.

7. If the same key already exists:
   - Same fingerprint + COMPLETED -> replay the stored PaymentResponse.
   - Different fingerprint -> return 409 Conflict.
   - Same fingerprint + PROCESSING -> return 409 Conflict.

8. The owning request persists the INITIATED payment and a PAYMENT_INITIATED
   Kafka outbox row in the same short PostgreSQL transaction.

9. Payment Service sends the payment details to Fraud Service outside that
   database transaction.

10. Fraud Service calculates a risk score and stores the fraud check result
    in MongoDB.

11. If the fraud decision is REJECTED:
    - Payment Service finalizes the payment as FAILED.
    - The same PostgreSQL transaction stores the final Kafka event and
      RabbitMQ notification outbox rows.

12. If the fraud decision is APPROVED or REVIEW:
    - Payment Service calls the selected payment provider outside the
      database transaction.

13. Payment Service finalizes the payment as AUTHORIZED or FAILED.
    The final payment state, final Kafka event, and notification outbox row
    are committed atomically in PostgreSQL.

14. Payment Service marks the idempotency record as COMPLETED and stores
    the payment ID and serialized response for future replay.

15. The HTTP request can return successfully without waiting for Kafka or
    RabbitMQ broker availability.

16. The scheduled outbox publisher selects eligible PENDING rows using
    FOR UPDATE SKIP LOCKED and moves them to PROCESSING with a lease owner.

17. The publisher sends the exact JSON payload already stored in the outbox.

18. Kafka publication is considered successful after the send future succeeds.
    RabbitMQ publication requires a positive publisher confirm and no
    mandatory returned message.

19. Successful rows become PUBLISHED. Failed rows return to PENDING with
    attempt_count, last_attempt_at, next_attempt_at, and last_error updated.

20. Retry delays use exponential backoff, and stale PROCESSING claims can
    be recovered after the configured lease timeout.

21. Same-payment Kafka lifecycle ordering is preserved so PAYMENT_INITIATED
    is published before the later PAYMENT_AUTHORIZED or PAYMENT_FAILED event.

22. Ledger and Settlement consume Kafka events; Notification Service consumes
    RabbitMQ notification messages.

23. React Dashboard fetches payment, ledger, settlement, fraud, and
    notification outputs through API Gateway.
```

---

### Service Responsibility Matrix

| Service                  | Main Responsibility                              | Database / Infrastructure   | Communication                                 |
| ------------------------ | ------------------------------------------------ | --------------------------- | --------------------------------------------- |
| API Gateway              | Routes frontend requests to backend services     | None                        | HTTP                                          |
| Merchant Service         | Merchant management and API key validation       | PostgreSQL, Redis           | REST                                          |
| Payment Service          | Payment orchestration and transactional outbox relay | PostgreSQL, Kafka, RabbitMQ | REST, SOAP, asynchronous Kafka/RabbitMQ publication |
| Fraud Service            | Risk scoring and fraud check persistence         | MongoDB                     | REST                                          |
| Legacy Bank SOAP Service | Simulated legacy bank authorization              | None                        | SOAP                                          |
| Ledger Service           | Immutable event history and state reconstruction | PostgreSQL, Kafka           | Kafka consumer, REST                          |
| Settlement Service       | Merchant payout calculation                      | PostgreSQL, Kafka           | Kafka consumer, REST                          |
| Notification Service     | Notification job processing and log persistence  | PostgreSQL, RabbitMQ        | RabbitMQ consumer, REST                       |

---

## Main Business Flow

The main payment flow works as follows:

1. A merchant is created in Merchant Service.
2. Merchant Service generates an API key.
3. A payment request is sent through API Gateway with an `Idempotency-Key`.
4. Payment Service validates the idempotency key and merchant API key.
5. Payment Service calculates the request fingerprint and atomically acquires the idempotency key in PostgreSQL.
6. A completed matching retry returns the previously stored response without repeating payment side effects.
7. A changed payload using the same key is rejected with `409 Conflict`.
8. A concurrent request using a key that is still `PROCESSING` is rejected with `409 Conflict`.
9. Payment Service persists the initiated payment and `PAYMENT_INITIATED` outbox row atomically.
10. The owning request calls Fraud Service for a risk check.
11. If fraud rejects the payment, Payment Service finalizes it as `FAILED` and atomically stores the final event and notification outbox rows.
12. Otherwise Payment Service routes the request to `MOCK_BANK` or `LEGACY_BANK_SOAP`.
13. The provider call runs outside the database transaction.
14. Payment Service finalizes the payment as `AUTHORIZED` or `FAILED` and atomically stores the corresponding Kafka event and RabbitMQ notification in the outbox.
15. Payment Service completes the idempotency record with the payment ID and response body.
16. The HTTP request completes without waiting for broker publication.
17. The outbox publisher asynchronously claims eligible rows and publishes their stored payloads to Kafka or RabbitMQ.
18. Successful broker acknowledgments move rows to `PUBLISHED`; failures are retried with durable metadata and exponential backoff.
19. Ledger Service consumes Kafka events and stores immutable payment event history.
20. Settlement Service consumes authorized payment events and calculates merchant settlement.
21. Notification Service consumes RabbitMQ messages and stores notification logs.
22. The React dashboard displays all related outputs.

---

## Communication Patterns

This project intentionally uses multiple communication patterns to simulate a real world distributed system.

### REST Communication

REST is used for request response operations where an immediate result is required.

Used between:

```text
Frontend -> API Gateway
API Gateway -> Microservices
Payment Service -> Merchant Service
Payment Service -> Fraud Service
```

REST is suitable here because payment initiation and API key validation require immediate responses.

---

### SOAP Communication

SOAP is used for legacy bank integration.

Used between:

```text
Payment Service -> Legacy Bank SOAP Service
```

This demonstrates how a modern microservice can integrate with an older enterprise system that still exposes SOAP based APIs.

---

### Transactional Outbox Communication

Payment Service does not perform a database write and broker publish as one fragile dual-write operation.

Instead, payment state and the logical broker message are committed together in PostgreSQL:

```text
Payment transaction
      |
      +--> payments
      |
      +--> outbox_events
      |
      v
PostgreSQL COMMIT
      |
      v
Outbox Publisher
   |          |
   v          v
 Kafka    RabbitMQ
```

The publisher performs network calls after the database transaction has completed. Broker outages therefore do not roll back an already authorized payment.

---

### Kafka Communication

Kafka is used for payment domain events.

Used for:

```text
Payment Service outbox -> Kafka payment-events topic
Kafka payment-events topic -> Ledger Service
Kafka payment-events topic -> Settlement Service
```

The outbox publisher uses the payment ID as the Kafka message key and sends the exact serialized event payload stored at outbox creation time. It waits for the Kafka send result before marking the row `PUBLISHED`.

Kafka is suitable because ledger and settlement operations should react to payment events without tightly coupling themselves to Payment Service.

---

### RabbitMQ Communication

RabbitMQ is used for background notification jobs.

Used for:

```text
Payment Service outbox -> RabbitMQ notification exchange / queue
RabbitMQ notification queue -> Notification Service
```

The publisher sends persistent JSON messages, uses the outbox event ID as correlation data, requires a positive publisher confirm, and treats mandatory returned messages as publication failures.

RabbitMQ is suitable for notification delivery because it is a task oriented asynchronous workload.

---

## Data Storage Design

The project uses different storage technologies for different purposes.

### PostgreSQL

PostgreSQL is used for transactional and relational data.

Used by:

```text
Merchant Service
Payment Service
Ledger Service
Settlement Service
Notification Service
```

Why PostgreSQL?

* Strong consistency.
* Relational structure.
* Transactional guarantees.
* Suitable for payment, merchant, settlement, and notification records.
* Supports atomic idempotency acquisition with `INSERT ... ON CONFLICT DO NOTHING`.
* Provides database-level uniqueness for `(merchant_id, order_id)` as a final duplicate-order backstop.

The Payment Service schema is versioned with Flyway. Current payment migrations establish:

* The `payments` table.
* A unique `(merchant_id, order_id)` constraint and merchant/date lookup index.
* The `idempotency_records` table for durable request ownership, replay state, and completed responses.
* The `outbox_events` table for durable Kafka and RabbitMQ messages.
* Outbox destination metadata, stable payload storage, and `PENDING` / `PROCESSING` / `PUBLISHED` lifecycle state.
* Claim/lease fields (`processing_started_at`, `lock_owner`) for concurrent-safe publication and stale claim recovery.
* Retry metadata (`attempt_count`, `last_attempt_at`, `next_attempt_at`, `last_error`) for controlled exponential backoff.

Hibernate schema management is configured for validation rather than runtime schema mutation.

---

### MongoDB

MongoDB is used by Fraud Service.

Why MongoDB?

* Fraud records may contain flexible metadata.
* Risk reasons can change over time.
* Document structure is useful for storing scoring details.
* Semi structured fraud data does not always fit a strict relational schema.

---

### Redis

Redis is used by Merchant Service for API key caching.

Why Redis?

* API key validation is frequent.
* Redis provides fast key-value lookups.
* It reduces repeated PostgreSQL queries.
* It improves performance in a payment request path.

---

## Payment Idempotency

`POST /api/payments/initiate` requires an `Idempotency-Key` header.

The idempotency scope is:

```text
merchant_id + operation + idempotency_key
```

For payment initiation, the operation is:

```text
PAYMENT_INITIATION
```

PostgreSQL is the source of truth for correctness. Redis is not used as the authoritative idempotency store.

### Request Fingerprint

Payment Service calculates a semantic SHA-256 fingerprint from the request fields that define the logical payment:

* Amount, normalized before hashing.
* Currency, normalized to uppercase.
* Order ID.
* Card token.
* Provider type.

The merchant is scoped separately by the idempotency database key.

### Request Behavior

| Situation | Result |
|---|---|
| New key | Request acquires ownership and processing starts |
| Same key + same request after completion | Stored `PaymentResponse` is replayed |
| Same key + different request | `409 Conflict` |
| Same key while the first request is still processing | `409 Conflict` |
| Different merchants using the same key | Independent idempotency scopes |

A successful idempotency record moves from `PROCESSING` to `COMPLETED` and stores the related payment ID and serialized response.

For known-safe failures before provider authorization, such as duplicate order detection, the temporary `PROCESSING` reservation is released. If execution stops after an external provider outcome may already have occurred, the record is intentionally not automatically released because blindly retrying could create a duplicate authorization. Provider-side idempotency or reconciliation is a future reliability improvement.

Idempotency protects HTTP retries, while the unique `(merchant_id, order_id)` constraint protects the separate business invariant that a merchant order must not create multiple payment records.

---

## Transactional Outbox

The Payment Service uses a PostgreSQL backed transactional outbox to reliably bridge payment database state and asynchronous broker publication.

Without an outbox, a request could save the payment successfully and fail before Kafka/RabbitMQ publication, or publish successfully and fail before the database update. The outbox removes the first database-to-broker dual-write by storing the logical message in the same transaction as the payment state change.

### Atomic Persistence

For the initiated state:

```text
BEGIN
  save PAYMENT_INITIATED state
  save PAYMENT_INITIATED outbox event
COMMIT
```

For the final state:

```text
BEGIN
  update payment to AUTHORIZED / FAILED
  save final Kafka outbox event
  save RabbitMQ notification outbox event
COMMIT
```

External fraud and provider calls are kept outside these short database transactions.

### Stable Stored Payload

The event or notification payload is serialized once when the outbox row is created. The outbox row ID is also used as the logical event/notification ID.

Retries therefore publish the same stored JSON and the same logical ID rather than reconstructing a new message from mutable payment state.

### Claim and Lease Lifecycle

Outbox rows move through:

```text
PENDING -> PROCESSING -> PUBLISHED
```

Eligible rows are claimed in short PostgreSQL transactions using `FOR UPDATE SKIP LOCKED`. A claimed row stores `lock_owner` and `processing_started_at`. Broker network I/O happens after the claim transaction has completed.

If a publisher crashes after claiming a row, stale `PROCESSING` rows are returned to `PENDING` after the configured lease timeout.

### Retry and Exponential Backoff

Failed publications are not retried in a tight scheduler loop. The row returns to `PENDING` with:

* `attempt_count`
* `last_attempt_at`
* `next_attempt_at`
* `last_error`

The verified default retry progression is based on a 5 second base delay and is capped at 60 seconds.

Only rows whose `next_attempt_at` is due are claimable again.

### Ordering

For the same payment and Kafka destination, a later lifecycle event cannot be claimed while an earlier event is still not `PUBLISHED`.

This preserves:

```text
PAYMENT_INITIATED
        |
        v
PAYMENT_AUTHORIZED / PAYMENT_FAILED
```

RabbitMQ notification publication is independent because it uses a different destination.

### Broker Failure Isolation

Manual Docker E2E tests verified both failure domains:

* With Kafka unavailable, the payment request still returned `201 Created` / `AUTHORIZED`, the Kafka outbox rows remained durable, and the RabbitMQ notification could still publish.
* After Kafka recovered, pending Kafka rows were published automatically without resubmitting the payment request.
* With RabbitMQ unavailable, Kafka events still published successfully while the notification row remained retryable.
* After RabbitMQ recovered, the notification was published automatically.

### Delivery Semantics

The outbox provides **at-least-once** publication semantics.

A broker may accept a message and the application may fail before PostgreSQL records the row as `PUBLISHED`. That message can therefore be delivered again on retry. Consumer-side idempotency is the next reliability layer and remains a separate development phase.

---

## Event Driven Architecture

Payment lifecycle events are created as immutable outbox messages whenever an important payment state transition is committed.

Example event types:

```text
PAYMENT_INITIATED
PAYMENT_AUTHORIZED
PAYMENT_FAILED
```

The event JSON is serialized once during outbox creation. A scheduled publisher later sends the stored payload to Kafka and marks the row `PUBLISHED` only after the Kafka send succeeds.

### Ledger Service Event Consumption

Ledger Service consumes all payment events and stores them as immutable records.

This allows the system to reconstruct a payment's final state by reading its event history.

Example:

```text
PAYMENT_INITIATED -> PAYMENT_AUTHORIZED
```

Final reconstructed state:

```text
AUTHORIZED
```

### Settlement Service Event Consumption

Settlement Service consumes payment events but only processes authorized payments.

For authorized payments, it calculates:

* Gross amount
* Commission amount
* Net settlement amount
* Merchant payout data

Failed payments do not generate settlement records.

Because outbox delivery is at-least-once, consumer duplicate protection is treated as a separate reliability concern and is listed in Future Improvements.

---

## Fraud Detection Flow

Fraud Service evaluates each payment request before authorization.

The risk score is calculated using simple rule based checks.

Example rules:

```text
High amount above threshold
Repeated card usage
Repeated IP usage
```

Possible decisions:

```text
APPROVED
REVIEW
REJECTED
```

If the fraud decision is rejected, Payment Service stops the authorization process and marks the payment as failed.

If the fraud decision is approved or reviewable, Payment Service continues to provider authorization.

---

## SOAP Legacy Bank Integration

The Legacy Bank SOAP Service simulates a traditional bank authorization system.

Payment Service calls this SOAP service when the selected provider is:

```text
LEGACY_BANK_SOAP
```

The SOAP service exposes a WSDL and processes authorization requests through XML based SOAP messages.

This part of the project demonstrates:

* SOAP endpoint creation.
* WSDL exposure.
* XSD based request and response contracts.
* SOAP client integration from a Spring Boot microservice.
* Coexistence of REST and SOAP in the same architecture.

---

## React Merchant Dashboard

The React dashboard is used to test and visualize the full payment flow.

It communicates with the backend only through API Gateway.

Main dashboard features:

* List merchants.
* Select a merchant.
* Display merchant API key.
* Initiate a payment.
* Select payment provider.
* Generate order ID.
* Display payment result.
* Display ledger state.
* Display settlement result.
* Display fraud check result.
* Display notification logs.

Frontend base URL:

```text
http://localhost:5173
```

API base URL:

```text
http://localhost:8090
```

---

## Docker Compose Infrastructure

The entire system is containerized using Docker Compose.

Docker Compose starts:

* PostgreSQL
* Redis
* Kafka
* MongoDB
* RabbitMQ
* All Spring Boot microservices
* React dashboard

This allows the full platform to run locally with one command.

Main command:

```bash
 docker compose up -d --build
```

---

## Project Structure

```text
paycore-connect/
├── backend/
│   ├── merchant-service/
│   ├── payment-service/
│   ├── ledger-service/
│   ├── legacy-bank-soap-service/
│   ├── settlement-service/
│   ├── fraud-service/
│   ├── notification-service/
│   └── api-gateway/
│
├── frontend/
│   └── merchant-dashboard/
│
├── infrastructure/
│   ├── docker-compose.yml
│   └── postgres/
│       └── init-db.sql
│
├── docs/
├── postman/
├── README.md
└── .gitignore
```

---

## How to Run

### Prerequisites

You need:

* Docker
* Docker Compose

Node.js is not required locally because the React dashboard runs inside Docker.

Java and Maven are also not required locally for Docker execution because backend services are built inside Maven based Docker build containers.

---

### 1. Clone the repository

```bash
git clone <repository-url>
cd paycore-connect
```

---

### 2. Start all services

```bash
cd infrastructure
docker compose up -d --build
```

The first build can take several minutes because Maven dependencies and frontend packages are downloaded.

---

### 3. Check running containers

```bash
docker ps
```

Expected containers:

```text
paycore-postgres
paycore-redis
paycore-kafka
paycore-mongodb
paycore-rabbitmq
paycore-merchant-service
paycore-payment-service
paycore-ledger-service
paycore-legacy-bank-soap-service
paycore-settlement-service
paycore-fraud-service
paycore-notification-service
paycore-api-gateway
paycore-merchant-dashboard
```

---

### 4. Open the React dashboard

```text
http://localhost:5173
```

---

### 5. Open RabbitMQ Management UI

```text
http://localhost:15672
```

Credentials:

```text
Username: paycore
Password: paycore
```

---

### 6. Check API Gateway health

```text
http://localhost:8090/actuator/health
```

---

## API Gateway Endpoints

All external requests should go through API Gateway.

Base URL:

```text
http://localhost:8090
```

### Merchant Endpoints

```http
POST /api/merchants
GET /api/merchants
GET /api/merchants/{merchantId}
PATCH /api/merchants/{merchantId}/status
POST /api/merchants/{merchantId}/api-key
GET /api/merchants/validate-api-key
```

### Payment Endpoints

```http
POST /api/payments/initiate
GET /api/payments/{paymentId}
GET /api/payments/merchant/{merchantId}
```

### Ledger Endpoints

```http
GET /api/ledger/payments/{paymentId}/events
GET /api/ledger/payments/{paymentId}/state
GET /api/ledger/merchants/{merchantId}/events
```

### Settlement Endpoints

```http
GET /api/settlements/payments/{paymentId}
GET /api/settlements/merchants/{merchantId}
GET /api/settlements/merchants/{merchantId}/summary
```

### Fraud Endpoints

```http
POST /api/fraud/check
GET /api/fraud/payments/{paymentId}
GET /api/fraud/merchants/{merchantId}
```

### Notification Endpoints

```http
GET /api/notifications/payments/{paymentId}
GET /api/notifications/merchants/{merchantId}
```

---

## Example API Requests

### Create Merchant

```http
POST http://localhost:8090/api/merchants
Content-Type: application/json
```

Request body:

```json
{
  "name": "Docker Demo Merchant",
  "email": "docker-demo@merchant.com"
}
```

The response includes an API key. This key is required for payment initiation.

---

### Initiate Payment with Mock Bank

```http
POST http://localhost:8090/api/payments/initiate
Content-Type: application/json
X-API-Key: <merchant-api-key>
Idempotency-Key: <unique-key-for-this-logical-payment>
X-Forwarded-For: 192.168.1.77
```

Request body:

```json
{
  "amount": 1000.00,
  "currency": "TRY",
  "orderId": "ORDER-DOCKER-1001",
  "cardToken": "card_token_1234567890123456",
  "providerType": "MOCK_BANK"
}
```

Expected result:

```text
Payment is authorized through mock provider.
Payment state and outbox messages are committed to PostgreSQL.
Kafka events are published asynchronously from the outbox.
Ledger state is created.
Settlement is calculated.
RabbitMQ notification is published asynchronously from the outbox.
Notification message is processed.
Fraud check is stored.
```

For a retry of the same logical payment, reuse the same `Idempotency-Key` and the same request body. After completion, Payment Service returns the stored response with the same payment ID instead of executing the provider flow again. A new logical payment must use a new idempotency key.

---

### Initiate Payment with Legacy SOAP Bank

```http
POST http://localhost:8090/api/payments/initiate
Content-Type: application/json
X-API-Key: <merchant-api-key>
Idempotency-Key: <unique-key-for-this-logical-payment>
X-Forwarded-For: 192.168.1.88
```

Request body:

```json
{
  "amount": 1200.00,
  "currency": "TRY",
  "orderId": "ORDER-SOAP-1001",
  "cardToken": "card_token_1234567890123456",
  "providerType": "LEGACY_BANK_SOAP"
}
```

Expected result:

```text
Payment Service calls Legacy Bank SOAP Service.
SOAP authorization result is stored.
Payment state and outbox rows are committed.
Kafka and RabbitMQ publication continues asynchronously through the outbox relay.
```

---

### Check Ledger State

```http
GET http://localhost:8090/api/ledger/payments/{paymentId}/state
```

---

### Check Settlement

```http
GET http://localhost:8090/api/settlements/payments/{paymentId}
```

---

### Check Fraud Result

```http
GET http://localhost:8090/api/fraud/payments/{paymentId}
```

---

### Check Notification Logs

```http
GET http://localhost:8090/api/notifications/payments/{paymentId}
```

---

## Service Ports

| Service                  |  Port | Description                             |
| ------------------------ | ----: |-----------------------------------------|
| React Merchant Dashboard |  5173 | Frontend dashboard                      |
| API Gateway              |  8090 | Single entry point                      |
| Merchant Service         |  8081 | Merchant and API key management         |
| Payment Service          |  8082 | Payment orchestration                   |
| Ledger Service           |  8083 | Kafka event consumer and event history  |
| Legacy Bank SOAP Service |  8084 | SOAP based bank simulation              |
| Settlement Service       |  8085 | Settlement calculation                  |
| Fraud Service            |  8086 | Fraud scoring and fraud logs            |
| Notification Service     |  8087 | RabbitMQ consumer and notification logs |
| PostgreSQL               |  5432 | Relational database                     |
| Redis                    |  6379 | API key cache                           |
| Kafka                    |  9092 | Event streaming                         |
| MongoDB                  | 27017 | Fraud document database                 |
| RabbitMQ                 |  5672 | Message broker                          |
| RabbitMQ Management UI   | 15672 | RabbitMQ web dashboard                  |

---

## Docker Compose Services

The Docker Compose setup includes:

```text
postgres
redis
kafka
mongodb
rabbitmq
merchant-service
payment-service
ledger-service
legacy-bank-soap-service
settlement-service
fraud-service
notification-service
api-gateway
merchant-dashboard
```

---

## Configuration Notes

Inside Docker Compose, services communicate using Docker service names.

Examples:

```text
postgres:5432
redis:6379
mongodb:27017
kafka:29092
rabbitmq:5672
merchant-service:8081
fraud-service:8086
legacy-bank-soap-service:8084
```

The browser based React application uses:

```text
http://localhost:8090
```

This is because the React code runs in the user's browser, not inside the Docker network.

Payment Service outbox behavior can be tuned with environment variables:

```text
OUTBOX_BATCH_SIZE=10
OUTBOX_POLL_INTERVAL_MS=1000
OUTBOX_LEASE_TIMEOUT_SECONDS=120
OUTBOX_PUBLISH_TIMEOUT_SECONDS=5
OUTBOX_RETRY_BASE_DELAY_SECONDS=5
OUTBOX_RETRY_MAX_DELAY_SECONDS=60
```

These values control batch claiming, scheduler cadence, stale lease recovery, broker wait time, and exponential retry backoff.

---

## Design Decisions

### Why use API Gateway?

The API Gateway provides a single entry point for the frontend and hides internal microservice addresses. It also makes future improvements easier, such as authentication, rate limiting, request logging, and centralized security policies.

---

### Why use PostgreSQL for most services?

Most business data in this system is transactional and relational. Merchant records, payments, settlements, ledger events, notification logs, and idempotency records benefit from relational consistency and structured queries.

---

### Why use PostgreSQL-backed idempotency?

Payment retries are a correctness problem, not only a caching problem. The idempotency record is therefore stored in PostgreSQL and acquired atomically with a unique key. This makes concurrent same-key requests deterministic and prevents an `exists()`-then-insert race.

Redis remains useful for merchant API key caching, but it is not the source of truth for payment idempotency.

---

### Why use a Transactional Outbox?

Payment persistence and broker publication cannot be made atomic with a normal local database transaction. Publishing directly after a database write creates a dual-write failure window.

The transactional outbox stores the logical Kafka/RabbitMQ message in PostgreSQL together with the payment state change. A separate publisher then performs broker I/O, allowing payment correctness to remain independent from temporary Kafka or RabbitMQ outages.

The relay uses short claim transactions, stale lease recovery, broker acknowledgments, stable stored payloads, and exponential retry backoff. Its delivery model is deliberately at-least-once, so downstream consumer idempotency remains important.

---

### Why use MongoDB for Fraud Service?

Fraud data can be dynamic. Risk signals, scoring metadata, velocity information, and rule explanations may evolve. MongoDB allows flexible document structures for this type of data.

---

### Why use Redis?

Redis is used for fast API key validation. API key checks happen frequently in the payment request path, so Redis helps reduce database load and improve lookup performance.

---

### Why use Kafka?

Kafka is used for domain events. Ledger and settlement processes should react to payment events without being tightly coupled to Payment Service. Kafka enables durable, asynchronous, event driven processing.

---

### Why use RabbitMQ?

RabbitMQ is used for notification jobs. Notifications are background tasks and should not block the payment authorization response. RabbitMQ is a good fit for queue based task processing.

---

### Why include SOAP?

Many financial systems still integrate with legacy SOAP APIs. This project includes SOAP to demonstrate how modern Spring Boot microservices can communicate with legacy enterprise systems.

---

## Current Capabilities

The project currently supports:

* Merchant registration.
* API key generation.
* API key validation.
* Payment initiation.
* Required `Idempotency-Key` support for payment initiation.
* PostgreSQL-backed semantic request fingerprinting and completed-response replay.
* Concurrent same-key request protection.
* Database-level duplicate order protection.
* Flyway-managed Payment Service schema migrations.
* Mock bank authorization.
* Legacy SOAP bank authorization.
* Fraud scoring.
* Transactional Outbox for reliable PostgreSQL-to-Kafka/RabbitMQ publication.
* Atomic payment-state and outbox persistence.
* Stable serialized event/message payloads and logical IDs across retries.
* Outbox claim/lease handling with `FOR UPDATE SKIP LOCKED`.
* Stale claim recovery.
* Same-payment Kafka lifecycle ordering.
* Kafka send acknowledgment before marking an event `PUBLISHED`.
* RabbitMQ publisher confirms and mandatory-return handling.
* Durable retry metadata and exponential backoff.
* Kafka outage recovery without losing payment events.
* RabbitMQ outage recovery without coupling broker availability to payment success.
* Kafka event consumption.
* Immutable event ledger with payment state reconstruction.
* Settlement calculation.
* RabbitMQ notification processing.
* Notification log storage.
* React dashboard monitoring.
* Full Docker Compose orchestration.

---

## Future Improvements

Potential future improvements:

* Add database-enforced idempotent consumers and stronger duplicate protection for ledger, settlement, and notification processing.
* Add concurrency tests for duplicate consumer deliveries against real databases.
* Add dead letter handling for terminal or non-retriable messaging failures.
* Add Resilience4j timeouts, retries, and circuit breakers around synchronous integrations.
* Add Testcontainers-based PostgreSQL, Kafka, RabbitMQ, Redis, and MongoDB integration tests.
* Automate Kafka/RabbitMQ outage and recovery scenarios in CI.
* Add automated concurrency tests for payment idempotency.
* Add contract testing between services.
* Add distributed tracing with OpenTelemetry.
* Add centralized logging with ELK or Loki.
* Add Prometheus and Grafana monitoring.
* Add k6 load and performance tests.
* Add centralized authentication with JWT and role based access control.
* Add rate limiting and broader security controls at API Gateway.
* Add Jenkins or GitHub Actions CI/CD pipeline.
* Add Kubernetes deployment manifests.
* Add real payment provider adapter interfaces and provider-side idempotency support.
* Add reconciliation for uncertain external provider outcomes.
* Add admin dashboard features and merchant specific reporting.
* Add API documentation with OpenAPI aggregation.

---

## Summary

PayCore Connect demonstrates a realistic payment orchestration architecture using modern backend engineering practices.

The project includes:

* Microservices architecture.
* REST and SOAP communication.
* API Gateway routing.
* PostgreSQL transactional persistence.
* Flyway-managed relational schema migrations.
* PostgreSQL-backed HTTP idempotency and replay protection.
* Transactional Outbox based database-to-broker delivery.
* Durable outbox claim/lease and stale recovery.
* Kafka and RabbitMQ broker acknowledgment handling.
* Exponential broker retry backoff with persisted attempt metadata.
* Broker outage recovery with at-least-once publication semantics.
* MongoDB document persistence.
* Redis caching.
* Kafka based event driven processing.
* Immutable event ledger with state reconstruction.
* RabbitMQ based asynchronous job processing.
* React dashboard.
* Docker Compose orchestration.
