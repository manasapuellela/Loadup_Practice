# Microservices Assessment - Manasa P.

Two Java/Spring Boot microservices for a multi-tenant e-commerce platform:

* `order-service`
* `notification-service`

The main things I focused on were tenant isolation, order lifecycle rules, and making sure notification delivery problems do not affect order creation or updates.

## Overview

`order-service` creates orders, updates their status, and cancels them when allowed.

`notification-service` stores a notification when an order is created, updated, completed, or cancelled.

Every request is tied to a tenant. A tenant can only access its own orders and notifications.

```text
Client
  |
  |  REST request + X-Tenant-Id
  v
order-service
  |
  |  Order + OutboxEvent in one transaction
  v
order-db
  |
  |  background poller sends event over REST
  v
notification-service
  |
  v
notification-db
```

The order request does not wait for notification-service. When an order changes, `order-service` saves an outbox event in the same transaction as the order. A background poller picks up that event and sends it to `notification-service`.

This means a slow or unavailable notification-service does not fail the order request.

I left Kafka out intentionally. It would add broker setup and operational overhead that are not needed for this assessment. The outbox still gives durable, asynchronous delivery.

## Multi-tenancy

Every API request requires:

```text
X-Tenant-Id: tenant-a
```

A request without this header is rejected with `400 Bad Request`.

I used Java 21 `ScopedValue` to carry the tenant ID through the request. This keeps the tenant context scoped to the request without manual `ThreadLocal` cleanup.

For this assessment, the tenant comes from a request header. In a production system, the tenant ID would come from a validated JWT or another authenticated identity source. Replacing the header with a JWT claim would only affect the request filter.

Orders and notifications both store `tenant_id`. Application code uses tenant-scoped repository methods for reads and updates, such as:

```text
findByIdAndTenantId(...)
findAllByTenantId(...)
findAllByTenantIdAndOrderId(...)
```

The notification service also validates that the tenant ID in an incoming event matches the tenant ID from the request header.

## Order lifecycle

Orders have four statuses:

```text
CREATED
CONFIRMED
COMPLETED
CANCELLED
```

Allowed transitions:

```text
CREATED   -> CONFIRMED
CREATED   -> CANCELLED
CONFIRMED -> COMPLETED
CONFIRMED -> CANCELLED
```

`COMPLETED` and `CANCELLED` are terminal states.

The transition rules live in `OrderStatus`, rather than being repeated as service-layer `if` statements. Invalid transitions return `409 Conflict`.

## Notification delivery and idempotency

Order events are written to an outbox table along with the order transaction.

The poller delivers pending events to `notification-service` over REST. If the delivery fails, the event remains pending and the attempt count increases. Events that exceed the retry limit are marked as failed and excluded from future polling.

Each outbox event has a unique `eventId`.

If notification-service receives the same event more than once for the same tenant, it returns the already-created notification instead of creating a duplicate.

A unique database constraint on `source_event_id` is the final duplicate safeguard. If an event ID is already associated with another tenant, notification-service rejects it instead of exposing another tenant's notification.

## Other decisions

**UUIDs instead of sequential IDs**
UUIDs avoid exposing record volume through predictable IDs.

**Separate databases**
Each service owns its own data. `order-service` does not read notification tables, and `notification-service` does not read order tables.

**Notifications are stored records**
The assessment does not require email, SMS, or push integration. Persisting a notification record represents the simulated send.

**BigDecimal for money**
`totalAmount` uses `BigDecimal` with database precision and scale.

**Health checks in Docker Compose**
The services wait for PostgreSQL to become healthy before starting.

## Testing

There are both unit and integration tests.

### Unit tests

Unit tests cover:

* Valid and invalid order status transitions
* Order cancellation rules
* Tenant-scoped order lookup
* Notification tenant mismatch handling
* Notification idempotency
* Cross-tenant event-ID collision handling
* Duplicate insert recovery after a database constraint violation

### Integration tests

Repository integration tests use H2 to verify tenant-scoped queries and tenant immutability behavior.

## How to run

Requirements:

* Docker
* Docker Compose

From the project root:

```bash
docker compose pull
docker compose up
```

Services:

```text
order-service:        http://localhost:8081
notification-service: http://localhost:8082
```

## API examples

### Create an order

```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-a" \
  -d '{"customerId": "cust-001", "totalAmount": 49.99}'
```

### Update order status

```bash
curl -X PATCH http://localhost:8081/api/orders/{orderId}/status \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-a" \
  -d '{"status": "CONFIRMED"}'
```

### Cancel an order

```bash
curl -X POST http://localhost:8081/api/orders/{orderId}/cancel \
  -H "X-Tenant-Id: tenant-a"
```

### List orders for a tenant

```bash
curl http://localhost:8081/api/orders \
  -H "X-Tenant-Id: tenant-a"
```

### Get notifications for an order

```bash
curl http://localhost:8082/api/notifications/order/{orderId} \
  -H "X-Tenant-Id: tenant-a"
```

## Running tests

```bash
cd order-service
mvn test
```

Then:

```bash
cd ../notification-service
mvn test
```

## Assumptions

* `totalAmount` must be positive.
* `customerId` is a plain string and is not validated against another service.
* The four order states above are the complete lifecycle for this assessment.
* An order can only be cancelled from `CREATED` or `CONFIRMED`.
* Notifications are retained permanently.
* Notification delivery is asynchronous and does not change order success or failure.

## What I would add with more time

* JWT-based authentication instead of a tenant header.
* Correlation IDs across services.
* Explicit HTTP timeout configuration and a circuit breaker around notification delivery.
* A versioned event contract.
* A dead-letter process for failed outbox events.
* Pagination for order and notification listing endpoints.
* More controller-level and end-to-end integration tests.
* A separate response DTO for notifications instead of returning the JPA entity directly.
* Consistent structured error responses for missing tenant headers and application exceptions.
* Explicit rejection of unknown event types.
