# PayCore Connect - Architecture Documentation

This document explains the architecture of PayCore Connect in detail, including service responsibilities, communication patterns, infrastructure components, runtime flow, and design decisions.

---

## 1. Architectural Goal

PayCore Connect is designed as a microservices based payment orchestration platform.

The main architectural goal is to simulate how a real world payment infrastructure works when multiple independent services must cooperate to complete a payment lifecycle.

The system separates responsibilities into independent services:

* Merchant management
* Payment orchestration
* Fraud analysis
* Legacy bank authorization
* Ledger event storage
* Settlement calculation
* Notification processing
* Frontend access through API Gateway

Each service owns its own business responsibility and, where necessary, its own data storage.

---

## 2. High-Level Architecture

```text
+--------------------------------------------------------------------------------+
|                                Client Layer                                    |
|--------------------------------------------------------------------------------|
| React Merchant Dashboard                                                       |
+--------------------------------------+-----------------------------------------+
                                       |
                                       | HTTP
                                       v
+--------------------------------------------------------------------------------+
|                              Gateway Layer                                     |
|--------------------------------------------------------------------------------|
| API Gateway                                                                    |
| - Single entry point                                                            |
| - Routes requests to backend services                                           |
| - Hides internal service topology                                               |
+--------------------------------------+-----------------------------------------+
                                       |
                                       v
+--------------------------------------------------------------------------------+
|                           Microservices Layer                                  |
|--------------------------------------------------------------------------------|
| Merchant Service          | Merchant and API key management                     |
| Payment Service           | Main payment orchestration                         |
| Fraud Service             | Fraud scoring and fraud history                    |
| Legacy Bank SOAP Service  | SOAP based legacy bank simulation                  |
| Ledger Service            | Event history and state reconstruction             |
| Settlement Service        | Merchant payout calculation                        |
| Notification Service      | Asynchronous notification processing               |
+--------------------------------------+-----------------------------------------+
                                       |
                                       v
+--------------------------------------------------------------------------------+
|                         Infrastructure Layer                                   |
|--------------------------------------------------------------------------------|
| PostgreSQL | MongoDB | Redis | Kafka | RabbitMQ                                |
+--------------------------------------------------------------------------------+
```

---

## 3. Service Responsibility Overview

| Service                  | Responsibility                                          | Storage / Infrastructure    |
| ------------------------ |---------------------------------------------------------| --------------------------- |
| API Gateway              | Routes external requests to internal services           | None                        |
| Merchant Service         | Manages merchants, API keys, and merchant status        | PostgreSQL, Redis           |
| Payment Service          | Orchestrates the payment lifecycle                      | PostgreSQL, Kafka, RabbitMQ |
| Fraud Service            | Calculates fraud risk and stores fraud results          | MongoDB                     |
| Legacy Bank SOAP Service | Simulates a SOAP based legacy bank provider             | None                        |
| Ledger Service           | Stores immutable payment events and reconstructs state  | PostgreSQL, Kafka           |
| Settlement Service       | Calculates merchant settlement for authorized payments  | PostgreSQL, Kafka           |
| Notification Service     | Consumes notification jobs and stores notification logs | PostgreSQL, RabbitMQ        |
| React Dashboard          | Provides UI for testing and observing the flow          | Browser runtime             |

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
        +-------------------------------------+--------------------------------------+
        |                                     |                                      |
        v                                     v                                      v
+--------------------+              +--------------------+               +--------------------+
| Merchant Service   |              | Payment Service    |               | Fraud Service      |
| Port: 8081         |              | Port: 8082         |               | Port: 8086         |
| PostgreSQL         |              | PostgreSQL         |               | MongoDB            |
| Redis              |              | Orchestrator       |               | Risk scoring       |
+---------+----------+              +---------+----------+               +---------+----------+
          ^                                   |                                    ^
          |                                   | REST                               |
          |                                   +------------------------------------+
          |
          |                                   | SOAP
          |                                   v
          |                         +----------------------+
          |                         | Legacy Bank SOAP     |
          |                         | Port: 8084           |
          |                         +----------------------+
          |
          |
          |                         +----------------------+
          |                         | Kafka                |
          |                         | payment-events       |
          |                         +----------+-----------+
          |                                    |
          |                   +----------------+----------------+
          |                   |                                 |
          |                   v                                 v
          |          +--------------------+            +--------------------+
          |          | Ledger Service     |            | Settlement Service |
          |          | Port: 8083         |            | Port: 8085         |
          |          | PostgreSQL         |            | PostgreSQL         |
          |          +--------------------+            +--------------------+
          |
          |
          |                         +----------------------+
          |                         | RabbitMQ             |
          |                         | notification queue   |
          |                         +----------+-----------+
          |                                    |
          |                                    v
          |                         +----------------------+
          |                         | Notification Service |
          |                         | Port: 8087           |
          |                         | PostgreSQL           |
          |                         +----------------------+
```

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

The dashboard does not call backend services directly. It sends all requests to the API Gateway.

---

### 5.2 Gateway Layer

The API Gateway is the single external entry point.

Frontend base API URL:

```text
http://localhost:8090
```

The gateway routes requests based on path patterns:

| Path                    | Target Service       |
| ----------------------- | -------------------- |
| `/api/merchants/**`     | Merchant Service     |
| `/api/payments/**`      | Payment Service      |
| `/api/ledger/**`        | Ledger Service       |
| `/api/settlements/**`   | Settlement Service   |
| `/api/fraud/**`         | Fraud Service        |
| `/api/notifications/**` | Notification Service |

The API Gateway is important because it prevents the frontend from depending on internal service addresses.

---

### 5.3 Business Services Layer

The business services layer contains domain specific microservices.

Each service is responsible for a clearly separated business capability.

This separation makes the system easier to understand, extend, test, and deploy.

---

### 5.4 Infrastructure Layer

The infrastructure layer provides databases, caches, event streaming, and message queueing.

| Technology | Used For                          |
| ---------- | --------------------------------- |
| PostgreSQL | Transactional relational storage  |
| MongoDB    | Flexible fraud check documents    |
| Redis      | API key cache                     |
| Kafka      | Payment lifecycle event streaming |
| RabbitMQ   | Notification job queueing         |

---

## 6. Main Payment Flow

The main business operation is payment initiation.

```text
1. User initiates payment from React Dashboard.
2. React sends request to API Gateway.
3. API Gateway routes the request to Payment Service.
4. Payment Service validates the merchant API key through Merchant Service.
5. Merchant Service checks PostgreSQL and Redis.
6. Payment Service sends fraud check request to Fraud Service.
7. Fraud Service calculates risk score and stores the result in MongoDB.
8. Payment Service continues or fails the payment based on fraud decision.
9. Payment Service calls selected provider:
   - MOCK_BANK
   - LEGACY_BANK_SOAP
10. Payment Service stores payment result in PostgreSQL.
11. Payment Service publishes payment events to Kafka.
12. Ledger Service consumes Kafka events and stores event history.
13. Settlement Service consumes authorized payment events and calculates payout.
14. Payment Service sends notification job to RabbitMQ.
15. Notification Service consumes the job and stores notification log.
16. React Dashboard displays all related outputs.
```

---

## 7. Communication Patterns

PayCore Connect uses multiple communication patterns.

### 7.1 REST

Used for synchronous request response communication.

REST is used between:

```text
React Dashboard -> API Gateway
API Gateway -> Backend services
Payment Service -> Merchant Service
Payment Service -> Fraud Service
```

REST is used when the caller needs an immediate response.

---

### 7.2 SOAP

Used for legacy banking integration.

SOAP is used between:

```text
Payment Service -> Legacy Bank SOAP Service
```

This simulates enterprise environments where old banking systems may still expose SOAP APIs.

---

### 7.3 Kafka

Used for event driven processing.

Kafka is used between:

```text
Payment Service -> Kafka topic: payment events
Kafka topic -> Ledger Service
Kafka topic -> Settlement Service
```

Kafka allows Ledger Service and Settlement Service to react to payment events without being directly called by Payment Service.

---

### 7.4 RabbitMQ

Used for background job processing.

RabbitMQ is used between:

```text
Payment Service -> RabbitMQ notification queue
RabbitMQ notification queue -> Notification Service
```

Notification processing is asynchronous because it should not block the payment response.

---

## 8. Database Ownership

Each service owns its own data model.

| Service              | Database   | Reason                               |
| -------------------- | ---------- | ------------------------------------ |
| Merchant Service     | PostgreSQL | Structured merchant and API key data |
| Payment Service      | PostgreSQL | Transactional payment records        |
| Ledger Service       | PostgreSQL | Immutable event records              |
| Settlement Service   | PostgreSQL | Structured payout records            |
| Notification Service | PostgreSQL | Notification logs                    |
| Fraud Service        | MongoDB    | Dynamic fraud metadata               |

This follows the database per service principle at a simplified local development level.

---

## 9. Why PostgreSQL?

PostgreSQL is used for relational and transactional data.

It is suitable for:

* Merchant records
* Payment transactions
* Ledger records
* Settlement records
* Notification logs

These data types require structure, consistency, and reliable querying.

---

## 10. Why MongoDB?

MongoDB is used only by Fraud Service.

Fraud data can contain flexible metadata such as:

* Risk score
* Triggered rules
* IP velocity
* Card velocity
* Fraud reasons
* Additional future risk signals

A document model is better suited for this kind of evolving fraud metadata.

---

## 11. Why Redis?

Redis is used by Merchant Service for API key validation.

Payment initiation requires API key validation. Since this can happen frequently, caching API keys in Redis reduces repeated PostgreSQL lookups.

---

## 12. Why Kafka?

Kafka is used for durable event streaming.

Payment Service publishes events such as:

```text
PAYMENT_INITIATED
PAYMENT_AUTHORIZED
PAYMENT_FAILED
```

Ledger Service and Settlement Service consume those events independently.

Kafka is suitable here because:

* Payment events represent domain facts.
* Multiple consumers can process the same event stream.
* Services do not need to directly call each other.
* Event history can be replayed or inspected.

---

## 13. Why RabbitMQ?

RabbitMQ is used for task based asynchronous messaging.

Notification processing is a background operation.

The payment result should not wait for notification delivery or notification logging. RabbitMQ decouples this background task from the main payment flow.

---

## 14. Why API Gateway?

The API Gateway provides:

* A single frontend entry point
* Cleaner routing
* Hidden internal service addresses
* A central place for future authentication
* A central place for future rate limiting
* A central place for future request logging

Without the gateway, the frontend would need to know every backend service port.

---

## 15. Why SOAP Integration?

SOAP integration is included because many financial institutions still use legacy SOAP based systems.

The Legacy Bank SOAP Service demonstrates:

* SOAP endpoint exposure
* WSDL generation
* XSD based contract
* SOAP client usage from Payment Service
* Integration of legacy protocols into a modern microservices system

---

## 16. Deployment Model

The current deployment model is local Docker Compose orchestration.

Docker Compose starts:

* Infrastructure containers
* Backend microservices
* React dashboard

This makes the project easy to run with a single command:

```bash
docker compose up -d --build
```

---

## 17. Future Architecture Improvements

Possible improvements:

* Kubernetes deployment
* Service discovery
* Centralized configuration
* Distributed tracing
* Centralized logging
* API Gateway authentication
* Circuit breaker pattern
* Retry mechanisms
* Dead letter queues
* Idempotency support
* OpenTelemetry metrics
* Prometheus and Grafana monitoring
