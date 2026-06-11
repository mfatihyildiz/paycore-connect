# PayCore Connect - API Flow Documentation

This document explains the main API flows in PayCore Connect.

It focuses on how requests move through the system, which services are involved, which communication style is used, and what the expected output is.

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

All external API calls should be sent through the API Gateway.

---

## 2. API Gateway Routing

| Gateway Path            | Target Service       | Target Port |
| ----------------------- | -------------------- | ----------: |
| `/api/merchants/**`     | Merchant Service     |        8081 |
| `/api/payments/**`      | Payment Service      |        8082 |
| `/api/ledger/**`        | Ledger Service       |        8083 |
| `/api/settlements/**`   | Settlement Service   |        8085 |
| `/api/fraud/**`         | Fraud Service        |        8086 |
| `/api/notifications/**` | Notification Service |        8087 |

The Legacy Bank SOAP Service is not directly exposed through API Gateway for normal frontend usage. It is called internally by Payment Service.

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
  v
PostgreSQL
```

### What Happens

1. Client sends merchant creation request to API Gateway.
2. API Gateway routes request to Merchant Service.
3. Merchant Service validates input.
4. Merchant Service generates API key.
5. Merchant Service stores merchant record in PostgreSQL.
6. Merchant Service returns merchant response.

### Expected Output

The response includes:

* Merchant ID
* Name
* Email
* API key
* Status
* Created timestamp
* Updated timestamp

---

## 4. Merchant Listing Flow

### Purpose

Lists all registered merchants.

### Endpoint

```http
GET /api/merchants
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

1. Client requests merchant list.
2. API Gateway routes request to Merchant Service.
3. Merchant Service fetches merchants from PostgreSQL.
4. Merchant Service returns list of merchants.

---

## 5. API Key Validation Flow

### Purpose

Validates whether a merchant API key is valid and active.

### Endpoint

```http
GET /api/merchants/validate-api-key
```

### Required Header

```http
X-API-Key: <merchant-api-key>
```

### Flow

```text
Payment Service or Client
  |
  v
API Gateway
  |
  v
Merchant Service
  |
  +----> Redis
  |
  +----> PostgreSQL
```

### What Happens

1. Merchant Service receives API key.
2. It checks Redis cache first if available.
3. If cache miss occurs, it checks PostgreSQL.
4. If the merchant is active and API key is valid, validation succeeds.
5. If not, validation fails.

### Why Redis Is Used

API key validation is part of the hot payment path. Redis reduces repeated database lookups and improves response speed.

---

## 6. Payment Initiation Flow

### Purpose

Creates and authorizes a payment.

### Endpoint

```http
POST /api/payments/initiate
```

### Required Headers

```http
Content-Type: application/json
X-API-Key: <merchant-api-key>
X-Forwarded-For: <client-ip-address>
```

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

### Flow

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
  +---- REST ----> Fraud Service
  |
  +---- Internal/Provider Authorization
  |
  +---- Kafka ----> payment events topic
  |
  +---- RabbitMQ -> notification queue
  |
  v
PostgreSQL
```

### What Happens

1. Client sends payment request to API Gateway.
2. API Gateway routes request to Payment Service.
3. Payment Service validates merchant API key by calling Merchant Service.
4. Payment Service creates initial payment record.
5. Payment Service publishes `PAYMENT_INITIATED` event to Kafka.
6. Payment Service calls Fraud Service.
7. Fraud Service calculates risk score and stores fraud result in MongoDB.
8. Payment Service decides whether to continue based on fraud result.
9. Payment Service routes payment to selected provider.
10. Payment Service stores final payment status.
11. Payment Service publishes final event to Kafka.
12. Payment Service sends notification job to RabbitMQ.
13. Response is returned to client.

---

## 7. Payment Provider Flow - MOCK_BANK

### Purpose

Simulates a modern REST-like internal mock payment provider.

### Provider Type

```text
MOCK_BANK
```

### Flow

```text
Payment Service
  |
  v
Mock Bank Authorization Logic
```

### What Happens

1. Payment Service receives provider type as `MOCK_BANK`.
2. Payment Service uses internal mock provider logic.
3. Mock provider returns approval or failure.
4. Payment Service updates payment result.

### Why It Exists

The mock bank provider allows the project to test the payment lifecycle without depending on an external real bank API.

---

## 8. Payment Provider Flow - LEGACY_BANK_SOAP

### Purpose

Simulates authorization through a SOAP based legacy banking system.

### Provider Type

```text
LEGACY_BANK_SOAP
```

### Flow

```text
Payment Service
  |
  | SOAP
  v
Legacy Bank SOAP Service
```

### SOAP Service

```text
http://localhost:8084/ws
```

### WSDL

```text
http://localhost:8084/ws/legacyBank.wsdl
```

### What Happens

1. Payment Service receives provider type as `LEGACY_BANK_SOAP`.
2. Payment Service prepares SOAP request.
3. Payment Service calls Legacy Bank SOAP Service.
4. SOAP service returns authorization result.
5. Payment Service stores SOAP provider response.
6. Payment Service continues Kafka and RabbitMQ flow.

### Why It Exists

Many financial institutions still expose SOAP services. This flow shows how modern Spring Boot services can integrate with legacy SOAP systems.

---

## 9. Fraud Check Flow

### Purpose

Evaluates the risk level of a payment before provider authorization.

### Endpoint

```http
POST /api/fraud/check
```

This endpoint is usually called internally by Payment Service.

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

### Fraud Rules

Fraud Service uses simple rule based scoring.

Example rules:

```text
High amount threshold
Card velocity threshold
IP velocity threshold
```

### Possible Decisions

```text
APPROVED
REVIEW
REJECTED
```

### What Happens If Rejected

If Fraud Service returns `REJECTED`:

1. Payment Service marks payment as failed.
2. Payment Service publishes `PAYMENT_FAILED` event to Kafka.
3. Settlement Service does not create settlement.
4. Notification flow may still record a failed payment notification.

---

## 10. Kafka Event Flow

### Purpose

Publishes payment lifecycle events so other services can react asynchronously.

### Producer

```text
Payment Service
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
Payment Service
  |
  | Kafka Producer
  v
Kafka Topic: payment-events
  |
  +----> Ledger Service
  |
  +----> Settlement Service
```

---

## 11. Ledger Event Flow

### Purpose

Stores immutable payment event history.

### Endpoint

```http
GET /api/ledger/payments/{paymentId}/events
GET /api/ledger/payments/{paymentId}/state
```

### Flow

```text
Kafka payment-events topic
  |
  v
Ledger Service
  |
  v
PostgreSQL
```

### What Happens

1. Ledger Service consumes every payment event.
2. It stores the event in PostgreSQL.
3. It can return event history.
4. It can reconstruct current payment state from events.

### Example State Reconstruction

```text
PAYMENT_INITIATED
PAYMENT_AUTHORIZED
```

Final state:

```text
AUTHORIZED
```

---

## 12. Settlement Flow

### Purpose

Calculates merchant payout after successful payment authorization.

### Endpoint

```http
GET /api/settlements/payments/{paymentId}
GET /api/settlements/merchants/{merchantId}
GET /api/settlements/merchants/{merchantId}/summary
```

### Flow

```text
Kafka payment-events topic
  |
  v
Settlement Service
  |
  v
PostgreSQL
```

### What Happens

1. Settlement Service consumes payment events.
2. It filters authorized payments.
3. It calculates commission.
4. It calculates net settlement amount.
5. It stores settlement record in PostgreSQL.

### Example

```text
Payment amount: 1000.00 TRY
Commission rate: 2.5%
Commission amount: 25.00 TRY
Net settlement: 975.00 TRY
```

---

## 13. Notification Flow

### Purpose

Processes payment notification jobs asynchronously.

### Producer

```text
Payment Service
```

### Broker

```text
RabbitMQ
```

### Consumer

```text
Notification Service
```

### Flow

```text
Payment Service
  |
  | RabbitMQ Producer
  v
RabbitMQ notification queue
  |
  v
Notification Service
  |
  v
PostgreSQL
```

### What Happens

1. Payment Service publishes notification message to RabbitMQ.
2. Notification Service consumes the message.
3. Notification Service processes notification.
4. Notification Service stores notification log in PostgreSQL.

### Why RabbitMQ Is Used

Notification processing is a background task. It should not block the payment authorization response.

---

## 14. Full Successful Payment Flow

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
  +----> Fraud Service
  |
  +----> Mock Bank or Legacy SOAP Bank
  |
  +----> PostgreSQL payment DB
  |
  +----> Kafka payment-events
  |          |
  |          +----> Ledger Service
  |          |
  |          +----> Settlement Service
  |
  +----> RabbitMQ notification queue
             |
             v
       Notification Service
```

---

## 15. Full Failed Payment Flow

A payment may fail because of:

* Invalid API key
* Passive merchant
* Fraud rejection
* Provider rejection
* Legacy bank limit rejection
* Internal service error

Example fraud rejection flow:

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
  +----> Fraud Service returns REJECTED
  |
  +----> Payment Service stores FAILED payment
  |
  +----> Payment Service publishes PAYMENT_FAILED event
  |
  +----> Ledger Service stores failure event
  |
  +----> Settlement Service ignores failed payment
```

---

## 16. Frontend Observation Flow

After payment creation, the React dashboard fetches additional outputs:

```text
GET /api/ledger/payments/{paymentId}/state
GET /api/settlements/payments/{paymentId}
GET /api/fraud/payments/{paymentId}
GET /api/notifications/payments/{paymentId}
```

This allows the UI to show the full distributed system result in one place.

---

## 17. Summary

The API flow demonstrates:

* Centralized routing through API Gateway
* REST based internal validation
* SOAP based legacy provider authorization
* Kafka based event driven ledger and settlement processing
* RabbitMQ based asynchronous notification processing
* Different databases for different business needs
