# PayCore Connect

**PayCore Connect** is a microservices based payment orchestration platform built with Java, Spring Boot, React, PostgreSQL, MongoDB, Redis, Kafka, RabbitMQ, SOAP, REST, and Docker Compose.

The project simulates a real world payment infrastructure where merchants can initiate payments, validate API keys, route payment requests to mock or legacy banking providers, run fraud checks, publish payment events, create ledger records, calculate settlements, and generate notification logs.

The main purpose of this project is to demonstrate a production oriented backend architecture using synchronous and asynchronous communication patterns, multiple persistence technologies, API Gateway routing, event driven processing, SOAP integration, and a React based dashboard.

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
* Initiate payment requests.
* Route payments to different payment providers.
* Run fraud checks before authorization.
* Store payment records.
* Publish domain events to Kafka.
* Consume payment events for ledger and settlement processes.
* Send asynchronous notification jobs through RabbitMQ.
* Monitor the full flow from a React dashboard.

The platform includes both synchronous and asynchronous communication:

* **Synchronous REST calls** are used when an immediate response is required.
* **SOAP calls** are used to simulate legacy bank integration.
* **Kafka events** are used for event driven ledger and settlement processing.
* **RabbitMQ messages** are used for asynchronous notification processing.

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
* Validating merchant API keys through Merchant Service.
* Calling Fraud Service before payment authorization.
* Routing payments to the selected provider.
* Supporting mock and legacy SOAP bank providers.
* Persisting payment data.
* Publishing payment events to Kafka.
* Publishing notification messages to RabbitMQ.

This service uses:

* **PostgreSQL** for payment transaction records.
* **REST** for merchant validation and fraud checks.
* **SOAP** for legacy bank authorization.
* **Kafka** for payment domain events.
* **RabbitMQ** for asynchronous notification jobs.

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

This service demonstrates an event sourcing style approach.

Instead of only storing the current payment state, it stores the sequence of events that happened during the payment lifecycle.

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
| Payment Service         | Orchestrates payment authorization flow                 |
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
| PostgreSQL | Relational storage for merchants, payments, ledger, settlements, logs |
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

When a payment request is received, Payment Service coordinates merchant validation, fraud checking, provider authorization, event publishing, settlement processing, and notification creation.

```text
1. React Dashboard sends a payment request to API Gateway.

2. API Gateway routes the request to Payment Service.

3. Payment Service validates the merchant API key by calling Merchant Service.

4. Merchant Service checks merchant data from PostgreSQL and may use Redis
   for faster API key lookup.

5. Payment Service sends the payment details to Fraud Service.

6. Fraud Service calculates a risk score and stores the fraud check result
   in MongoDB.

7. If the fraud decision is REJECTED:
   - Payment Service marks the payment as failed.
   - A PAYMENT_FAILED event is published to Kafka.

8. If the fraud decision is APPROVED or REVIEW:
   - Payment Service continues with provider authorization.

9. Payment Service routes the request to one of the supported providers:
   - MOCK_BANK
   - LEGACY_BANK_SOAP

10. If LEGACY_BANK_SOAP is selected, Payment Service calls the SOAP-based
    Legacy Bank SOAP Service.

11. Payment Service stores the payment result in PostgreSQL.

12. Payment Service publishes payment lifecycle events to Kafka.

13. Ledger Service consumes Kafka events and stores immutable event history.

14. Settlement Service consumes authorized payment events and calculates
    merchant settlement amounts.

15. Payment Service publishes a notification job to RabbitMQ.

16. Notification Service consumes the RabbitMQ message and stores the
    notification result in PostgreSQL.

17. React Dashboard fetches the payment result, ledger state, settlement,
    fraud result, and notification logs through API Gateway.
```

---

### Service Responsibility Matrix

| Service                  | Main Responsibility                              | Database / Infrastructure   | Communication                                 |
| ------------------------ | ------------------------------------------------ | --------------------------- | --------------------------------------------- |
| API Gateway              | Routes frontend requests to backend services     | None                        | HTTP                                          |
| Merchant Service         | Merchant management and API key validation       | PostgreSQL, Redis           | REST                                          |
| Payment Service          | Main payment orchestration                       | PostgreSQL, Kafka, RabbitMQ | REST, SOAP, Kafka producer, RabbitMQ producer |
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
3. A payment request is sent through API Gateway.
4. Payment Service receives the request.
5. Payment Service validates the API key by calling Merchant Service.
6. Payment Service calls Fraud Service for a risk check.
7. If the fraud decision is rejected, the payment fails.
8. If the fraud decision is approved or reviewable, Payment Service routes the request to a payment provider.
9. If `MOCK_BANK` is selected, Payment Service uses internal mock authorization logic.
10. If `LEGACY_BANK_SOAP` is selected, Payment Service calls the SOAP bank service.
11. Payment Service stores the payment result.
12. Payment Service publishes payment events to Kafka.
13. Ledger Service consumes Kafka events and stores immutable payment event history.
14. Settlement Service consumes authorized payment events and calculates merchant settlement.
15. Payment Service publishes a notification job to RabbitMQ.
16. Notification Service consumes the RabbitMQ message and stores notification logs.
17. The React dashboard displays all related outputs.

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

### Kafka Communication

Kafka is used for event driven processing.

Used for:

```text
Payment Service -> Kafka payment-events topic
Kafka payment-events topic -> Ledger Service
Kafka payment-events topic -> Settlement Service
```

Kafka is suitable because ledger and settlement operations should react to payment events without tightly coupling themselves to Payment Service.

---

### RabbitMQ Communication

RabbitMQ is used for background job processing.

Used for:

```text
Payment Service -> RabbitMQ notification queue
RabbitMQ notification queue -> Notification Service
```

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

## Event Driven Architecture

Payment Service publishes events to Kafka whenever an important payment lifecycle change occurs.

Example event types:

```text
PAYMENT_INITIATED
PAYMENT_AUTHORIZED
PAYMENT_FAILED
```

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
Kafka events are published.
Ledger state is created.
Settlement is calculated.
Notification message is processed.
Fraud check is stored.
```

---

### Initiate Payment with Legacy SOAP Bank

```http
POST http://localhost:8090/api/payments/initiate
Content-Type: application/json
X-API-Key: <merchant-api-key>
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
Kafka and RabbitMQ flows continue after provider response.
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

---

## Design Decisions

### Why use API Gateway?

The API Gateway provides a single entry point for the frontend and hides internal microservice addresses. It also makes future improvements easier, such as authentication, rate limiting, request logging, and centralized security policies.

---

### Why use PostgreSQL for most services?

Most business data in this system is transactional and relational. Merchant records, payments, settlements, ledger events, and notification logs benefit from relational consistency and structured queries.

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
* Mock bank authorization.
* Legacy SOAP bank authorization.
* Fraud scoring.
* Kafka event publishing.
* Ledger event consumption.
* Event based payment state reconstruction.
* Settlement calculation.
* RabbitMQ notification processing.
* Notification log storage.
* React dashboard monitoring.
* Full Docker Compose orchestration.

---

## Future Improvements

Potential future improvements:

* Add centralized authentication with JWT.
* Add role based access control.
* Add distributed tracing with OpenTelemetry.
* Add centralized logging with ELK or Loki.
* Add Prometheus and Grafana monitoring.
* Add Kubernetes deployment manifests.
* Add Jenkins or GitHub Actions CI/CD pipeline.
* Add unit and integration tests.
* Add contract testing between services.
* Add retry and dead letter queue handling.
* Add idempotency key support for payment requests.
* Add real payment provider adapter interfaces.
* Add admin dashboard features.
* Add merchant specific reporting.
* Add rate limiting at API Gateway.
* Add API documentation with OpenAPI aggregation.

---

## Summary

PayCore Connect demonstrates a realistic payment orchestration architecture using modern backend engineering practices.

The project includes:

* Microservices architecture.
* REST and SOAP communication.
* API Gateway routing.
* PostgreSQL transactional persistence.
* MongoDB document persistence.
* Redis caching.
* Kafka based event driven processing.
* RabbitMQ based asynchronous job processing.
* React dashboard.
* Docker Compose orchestration.
