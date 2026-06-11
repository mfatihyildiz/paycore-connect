# PayCore Connect — Test Documentation

## 1. Purpose

This document describes the test strategy, test scope, test coverage, execution commands, and verified scenarios for **PayCore Connect — Payment Orchestration Platform**.

PayCore Connect is structured as a multi service fintech backend platform. The system includes payment orchestration, merchant validation, fraud checks, payment event publishing, ledger reconstruction, settlement calculation, notification processing, legacy SOAP bank integration, and API gateway routing support.

The test suite was designed to validate the main business logic, REST controllers, event driven adapters, provider clients, external service wrappers, and configuration components without requiring real infrastructure such as Kafka, RabbitMQ, Redis, MongoDB, PostgreSQL, or external SOAP/REST services during unit level execution.

---

## 2. Services Covered

The following backend services are covered by the test suite:

| Service | Responsibility                                                                                                                                          |
|---|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `fraud-service` | Evaluates payment risk and stores fraud check results                                                                                                   |
| `merchant-service` | Manages merchants, API keys, status updates, and API key validation                                                                                     |
| `payment-service` | Orchestrates payment initiation, merchant validation, fraud check, provider authorization, Kafka event publishing, and RabbitMQ notification publishing |
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
    - This avoids unnecessary Spring context startup and dependency loading issues.

3. **Event Consumer Tests**
    - Validate Kafka/RabbitMQ listener methods delegate incoming messages to the correct service method.
    - Kafka/RabbitMQ infrastructure is not started.

4. **Producer Tests**
    - Validate Kafka/RabbitMQ producer classes build the correct event/message payloads.
    - KafkaTemplate and RabbitTemplate are mocked.
    - `ArgumentCaptor` is used to inspect produced event/message content.

5. **Client Wrapper Tests**
    - Validate REST/SOAP wrapper classes call the correct endpoint and map responses properly.
    - `MockRestServiceServer` is used for REST clients.
    - `WebServiceTemplate` is mocked for SOAP provider client tests.

6. **Configuration Tests**
    - Validate CORS configuration and gateway request logging behavior.
    - No gateway runtime is required.

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

The tests are intentionally designed to run locally without requiring:

- PostgreSQL
- MongoDB
- Redis
- Kafka
- RabbitMQ
- External REST services
- External SOAP services

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
| `MockBankPaymentProviderClientTest` | `MockBankPaymentProviderClient` |
| `PaymentProviderFactoryTest` | `PaymentProviderFactory` |
| `LegacyBankSoapPaymentProviderClientTest` | `LegacyBankSoapPaymentProviderClient` |
| `PaymentEventProducerTest` | `PaymentEventProducer` |
| `PaymentNotificationProducerTest` | `PaymentNotificationProducer` |
| `MerchantClientTest` | `MerchantClient` |
| `FraudClientTest` | `FraudClient` |

---

### PaymentServiceTest Coverage

The payment service is the central orchestration layer. It validates merchant API keys, checks duplicate orders, persists payment state, calls fraud service, authorizes payment through provider clients, publishes Kafka events, and publishes notification messages.

Covered scenarios:

- Valid payment request creates initiated payment
- Merchant API key is validated before payment processing
- Invalid merchant API key throws `InvalidMerchantApiKeyException`
- Duplicate order throws `DuplicateOrderException`
- Payment currency is normalized to uppercase
- Card last four digits are extracted from card token
- Initiated payment event is published
- Fraud check is called with payment and request details
- Fraud rejection marks payment as `FAILED`
- Fraud rejected payment publishes final payment event
- Fraud rejected payment publishes notification
- Provider authorization is called when fraud does not reject
- Approved provider response marks payment as `AUTHORIZED`
- Failed provider response marks payment as `FAILED`
- Provider response fields are stored on payment
- Payment can be retrieved by ID
- Missing payment throws `PaymentNotFoundException`
- Merchant payment list is returned

### PaymentControllerTest Coverage

Covered endpoints:

```http
POST /api/payments/initiate
GET /api/payments/{paymentId}
GET /api/payments/merchant/{merchantId}
```

Covered scenarios:

- Valid payment initiation returns `201 Created`
- `X-Forwarded-For` header is parsed and first IP is used
- Missing `X-Forwarded-For` uses `"unknown"` client IP
- Missing `X-API-Key` returns `400 Bad Request`
- Invalid request body returns `400 Bad Request`
- Invalid merchant API key returns `401 Unauthorized`
- Duplicate order returns `409 Conflict`
- Payment by ID returns `200 OK`
- Missing payment returns `404 Not Found`
- Merchant payment list returns `200 OK`

### PaymentInitiateRequest Validation Covered

Invalid body test verifies validation errors for:

- `amount`
- `currency`
- `orderId`
- `cardToken`
- `providerType`

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
- SOAP approved response maps to `ProviderPaymentResponse`
- SOAP rejected response maps to `ProviderPaymentResponse`
- SOAP endpoint URL is correctly passed to `WebServiceTemplate`
- Merchant ID, amount, currency, order ID, and card token are sent correctly

---

### PaymentEventProducerTest Coverage

Target:

```java
PaymentEventProducer.publishPaymentEvent(payment)
```

Covered scenarios:

- Kafka topic is correct
- Kafka key is `paymentId.toString()`
- `eventId` is generated
- `eventType` follows `"PAYMENT_" + payment.status.name()` format
- Payment ID is mapped
- Merchant ID is mapped
- Amount is mapped
- Currency is mapped
- Order ID is mapped
- Payment status is mapped
- Provider type is mapped
- Provider reference ID is mapped
- Provider response code is mapped
- Provider response message is mapped
- `occurredAt` is generated at publish time
- Authorized payment event is published correctly
- Failed payment event is published correctly

---

### PaymentNotificationProducerTest Coverage

Target:

```java
PaymentNotificationProducer.publishPaymentNotification(payment, merchantName)
```

Covered scenarios:

- RabbitMQ exchange is correct
- RabbitMQ routing key is correct
- `notificationId` is generated
- Payment ID is mapped
- Merchant ID is mapped
- Merchant name is mapped
- Amount is mapped
- Currency is mapped
- Order ID is mapped
- Payment status is mapped as string
- Provider type is mapped as string
- Provider reference ID is mapped
- Provider response code is mapped
- Provider response message is mapped
- `occurredAt` is generated at publish time
- Authorized payment notification is published correctly
- Failed payment notification is published correctly

---

### MerchantClientTest Coverage

Target:

```java
MerchantClient.validateApiKey(apiKey)
```

Covered scenarios:

- Sends `GET` request to merchant validation endpoint
- Uses endpoint `/api/merchants/validate-api-key`
- Sets `X-API-Key` header correctly
- Valid merchant response is deserialized correctly
- Invalid API key response is deserialized correctly
- Merchant service server error is propagated as exception

---

### FraudClientTest Coverage

Target:

```java
FraudClient.checkPaymentRisk(request)
```

Covered scenarios:

- Sends `POST` request to fraud check endpoint
- Uses endpoint `/api/fraud/check`
- Sends JSON request body
- `REVIEW` fraud response is deserialized correctly
- `REJECTED` fraud response is deserialized correctly
- Triggered fraud rules are mapped correctly
- Fraud service server error is propagated as exception

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

---

# 10. Event Driven Testing Coverage

The platform uses both Kafka and RabbitMQ patterns.

## Kafka

| Service | Component | Test Focus |
|---|---|---|
| `payment-service` | `PaymentEventProducer` | Builds and sends payment events |
| `ledger-service` | `PaymentEventConsumer` | Delegates consumed events to ledger service |
| `settlement-service` | `PaymentEventConsumer` | Delegates consumed events to settlement service |

## RabbitMQ

| Service | Component | Test Focus |
|---|---|---|
| `payment-service` | `PaymentNotificationProducer` | Builds and sends notification messages |
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

Controller tests use:

```java
MockMvcBuilders.standaloneSetup(controller)
```

Reason:

- Faster execution
- No full Spring context startup
- Avoids unnecessary auto configuration issues
- Keeps controller tests focused on endpoint behavior
- Easier to inject mocked service dependencies

## 12.2 ReflectionTestUtils for @Value Fields

Classes with `@Value` fields are tested using:

```java
ReflectionTestUtils.setField(target, "fieldName", "value");
```

This avoids Spring context startup while still testing behavior depending on configuration values.

Used in:

- `PaymentEventProducerTest`
- `PaymentNotificationProducerTest`
- `MerchantClientTest`
- `FraudClientTest`
- `LegacyBankSoapPaymentProviderClientTest`

## 12.3 ArgumentCaptor for Produced Events

Producer tests use `ArgumentCaptor` to inspect generated payloads.

Example:

```java
ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);
```

This validates not only that a send method was called, but also that the actual event content is correct.

## 12.4 MockRestServiceServer for REST Clients

REST client tests use:

```java
MockRestServiceServer.bindTo(restClientBuilder).build();
```

This verifies:

- HTTP method
- URL
- Headers
- Response mapping
- Error propagation

---

# 13. Known Non-Goals of the Current Test Suite

The current suite does not aim to provide full end to end infrastructure testing.

Not currently covered:

- Real Kafka broker integration
- Real RabbitMQ integration
- Real Redis integration
- Real MongoDB integration
- Real PostgreSQL integration
- Docker Compose end to end payment flow
- Contract testing between services
- Performance/load testing
- Security penetration testing

These are valid future improvements but intentionally out of scope for the current fast running test suite.

---

# 14. Recommended Future Improvements

## 14.1 Testcontainers Integration

Add Testcontainers based integration tests for:

- PostgreSQL
- MongoDB
- Redis
- Kafka
- RabbitMQ

This would allow more realistic integration validation while keeping tests repeatable.

## 14.2 End to End Payment Flow Test

Create an E2E test for the full successful payment flow:

```text
Merchant API Key
-> Payment Initiation
-> Fraud Check
-> Provider Authorization
-> Kafka Payment Event
-> Ledger Event Storage
-> Settlement Calculation
-> RabbitMQ Notification
-> Notification Log
```

## 14.3 Contract Tests

Introduce contract tests between:

- `payment-service` and `merchant-service`
- `payment-service` and `fraud-service`
- `payment-service` and `legacy-bank-soap-service`

Recommended tools:

- Spring Cloud Contract
- Pact

## 14.4 CI Pipeline

Add GitHub Actions or GitLab CI pipeline:

```yaml
mvn test
```

per service.

Recommended CI stages:

1. Compile
2. Unit tests
3. Controller tests
4. Adapter tests
5. Package
6. Docker build

## 14.5 Coverage Reporting

Add JaCoCo to each service and generate reports:

```powershell
mvn clean test jacoco:report
```

Recommended minimum coverage target:

| Layer | Target |
|---|---|
| Service Layer | 80%+ |
| Controller Layer | 70%+ |
| Adapter Layer | 70%+ |
| Overall | 75%+ |

---

# 15. Overall Coverage Summary

| Area                            | Status |
|---------------------------------|---|
| Business service logic          | Covered |
| REST controllers                | Covered |
| Validation handling             | Covered |
| Exception handling              | Covered |
| Kafka consumers                 | Covered |
| Kafka producers                 | Covered |
| RabbitMQ consumers              | Covered |
| RabbitMQ producers              | Covered |
| REST clients                    | Covered |
| SOAP provider client            | Covered |
| Gateway config/filter           | Covered |
| Real infrastructure integration | Future improvement |
| End to end Docker flow          | Future improvement |

---

# 16. Final Assessment

The current test suite provides strong coverage for the core backend responsibilities of PayCore Connect.

The most important verified capabilities are:

- Merchant API key validation flow
- Payment initiation orchestration
- Duplicate order prevention
- Fraud risk decision handling
- Provider authorization handling
- Payment state transitions
- Kafka event creation and consumption
- Ledger state reconstruction
- Settlement calculation
- RabbitMQ notification creation and consumption
- REST and SOAP client wrapper correctness
- Controller level HTTP status and JSON response behavior
- Centralized exception handling behavior

The project is now in a significantly stronger state for portfolio presentation, technical review, and CI integration.

