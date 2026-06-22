# Microservices Assessment - Manasa P.

Two Java/Spring Boot microservices for a multi-tenant e-commerce platform: order-service and notification-service.

## Overview

order-service manages orders: create, update status, cancel. Every order belongs to a tenant, and tenants can never see each other's data.

notification-service listens for order events and records a notification: a receipt when an order is created or updated, and a completion message when an order reaches a final state.

The order creation/update request itself never waits on notification-service. order-service writes an outbox event in the same transaction as the order, and a separate background poller delivers it to notification-service over REST, asynchronously, outside the customer-facing request path. order-service writes an event to its own database in the same transaction as the order, and a background poller (runs every 5 seconds) picks it up and sends it to notification-service over REST. If order-service called notification-service directly, a slow or down notification-service could fail the order creation too. The outbox approach decouples that. I considered Kafka for this, but a real broker needs its own infrastructure (topics, consumer groups) that isn't worth it at this scope, and it's not in the listed tech stack either. This approach can be used when Kafka goes down, store the event with the order, retry later.

## Multi-tenancy

Every request needs a header: `X-Tenant-Id: tenant-a`. A filter checks for it before anything else runs. Missing header gets rejected with 400.

I used Java 21's ScopedValue instead of ThreadLocal to carry the tenant ID through a request. It's automatically available wherever it's needed and automatically cleaned up when the request ends, no manual cleanup.

A header isn't how you'd verify identity in production, anyone could fake it. A real system would extract the tenant ID from a verified JWT after login. I used a header on purpose here, to keep the assessment focused on the isolation logic rather than building an auth system. Swapping it for a JWT claim later only touches the filter, nothing else.

On the data side, every query that touches an order requires a tenant ID as part of the method signature. There's no method anywhere that fetches an order without one, I avoided Spring's default repository methods (like a plain findById) specifically because they don't know about tenants.

I added a test that creates an order for tenant A, then tries to change it to tenant B and save it. The test confirms the database still shows tenant A afterward. tenant_id is marked non-updatable at the column level, so even if application code tried to change it, Hibernate won't include that column in the UPDATE statement.

I also caught a gap during a code review: notification-service was trusting the tenant ID from the request body instead of the validated header. Fixed it so the header is the source of truth, and a mismatch between header and body now returns a 409 instead of silently trusting the body.

## Order lifecycle

Four states: CREATED, CONFIRMED, COMPLETED, CANCELLED.

Allowed moves:
- CREATED to CONFIRMED
- CREATED to CANCELLED
- CONFIRMED to COMPLETED
- CONFIRMED to CANCELLED

COMPLETED and CANCELLED are final. The transition rules live on the OrderStatus enum itself (canTransitionTo), not as if-statements in the service. If another part of the app ever needs to check a transition, it calls the enum directly instead of duplicating logic. An invalid move returns 409 with a clear message.

## Idempotency

The outbox poller can retry a delivery if it doesn't get a confirmed response back, even if notification-service actually processed it. Without protection, that creates duplicate notifications.

Each outbox event has its own ID, which gets passed along to notification-service as `eventId`. notification-service checks for an existing notification with that event ID before inserting, and there's also a unique constraint on the column at the database level, so even a race condition can't produce a duplicate row.

## Other decisions worth noting

**UUIDs, not auto-increment IDs.** Sequential IDs leak information about volume. UUIDs don't.

**Each service owns its own database.** order-service and notification-service never read each other's tables. They only communicate through the outbox/event mechanism.

**Notifications are just database records.** The assessment says they don't need to hit a real provider, just keep a record of what would've been sent. That record is the entire "send."

**Health checks in docker-compose.** Without them, a service can try to connect before its database has finished starting, and crash on first boot.

**Lombok on entities, Java records on DTOs.** Cut the repetitive getter/setter code. Entities use `@Getter` (plus explicit setters only where genuinely needed, like the outbox payload, which gets written in two steps). DTOs are records since they're pure immutable data carriers, that's exactly what records are for.

## Testing

Two kinds, because they catch different things:

1. **Unit tests** (OrderServiceTest, NotificationServiceTest): business logic in isolation, using mocked repositories. Covers valid/invalid transitions, event type branching, the tenant-mismatch rejection, and the idempotency short-circuit.

2. **Integration tests** (OrderRepositoryTest): real queries against an in-memory H2 database, to prove the tenant-scoped queries actually work at the SQL level. The most important one tries to break tenant isolation directly and confirms the database blocks it.

One test initially passed for the wrong reason: Hibernate returned a cached, in-memory copy instead of reading fresh from the database, which would've hidden a real bug if one existed. Added `entityManager.clear()` to force a genuine read before asserting.

## How to run it

Needs Docker and Docker Compose. From the project root:

```bash
docker-compose up
```

Pulls the images from Docker Hub and starts everything. No manual setup.

- order-service: `http://localhost:8081`
- notification-service: `http://localhost:8082`

### API examples

**Create an order:**
```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-a" \
  -d '{"customerId": "cust-001", "totalAmount": 49.99}'
```

**Update status:**
```bash
curl -X PATCH http://localhost:8081/api/orders/{orderId}/status \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-a" \
  -d '{"status": "CONFIRMED"}'
```

**Cancel:**
```bash
curl -X POST http://localhost:8081/api/orders/{orderId}/cancel \
  -H "X-Tenant-Id: tenant-a"
```

**List all orders for a tenant:**
```bash
curl http://localhost:8081/api/orders -H "X-Tenant-Id: tenant-a"
```

**Check notifications for an order:**
```bash
curl http://localhost:8082/api/notifications/order/{orderId} -H "X-Tenant-Id: tenant-a"
```

### Running tests

```bash
cd order-service
mvn test
```

Same for notification-service.

## Assumptions

- totalAmount must be positive.
- customerId is a plain string, not validated against an external system.
- The four statuses listed are the full lifecycle, nothing else was specified.
- Notifications are permanent, no expiry or deletion.
- An order can only be cancelled from CREATED or CONFIRMED.


## What I'd add with more time

- Real authentication (JWT claim instead of header), the seam for this is already isolated to the filter.
- Correlation IDs across the two services. While we can perform propagating an ID through HTTP and Kafka headers for cross-service tracing, this project only has basic logging at the outbox/poller level, no actual ID generation or propagation across the REST call to notification-service. On resilience, the outbox poller already retries failed deliveries with a capped attempt count, which covers part of what a circuit breaker would address, but there's no explicit timeout configuration or breaker pattern on the HTTP call itself.
- A formal, versioned event contract between the two services instead of an informal shared JSON shape.
- Dead-letter handling for outbox events that exceed the retry limit, right now they're just logged and skipped.
- Pagination on the list-orders endpoint.
- Compare Hibernate's `@Filter` against the explicit tenant-scoped repository methods I used, at scale.
- Additional test coverage: HTTP-level tests confirming a missing tenant header returns a structured 400, an end-to-end test confirming one tenant genuinely cannot read/update/cancel another tenant's order through the live API (not just at the repository layer), OutboxPublisher tests for both successful delivery and failure/retry behavior, and a repository-level integration test for notification-service (currently only order-service has one).
- A `NotificationResponse` record, so notification-service returns a controlled API shape instead of the raw JPA entity, consistent with how order-service uses `OrderResponse`.
- Unify the missing-tenant-header error response with the same structured format the global exception handler uses everywhere else, right now that one error path is built manually inside the filter and has a slightly different shape.
- Reject unknown event types explicitly instead of defaulting them to `ORDER_RECEIPT`.
