package com.loadup.orderservice.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_processed", columnList = "processed")
})
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String payload;

    @Column(name = "processed", nullable = false)
    private boolean processed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public OutboxEvent() {
    }

    public OutboxEvent(String tenantId, UUID orderId, String eventType, String payload) {
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.eventType = eventType;
        this.payload = payload;
    }

    public void markProcessed() {
        this.processed = true;
        this.processedAt = Instant.now();
    }

    public void incrementAttempt() {
        this.attemptCount++;
    }

    // Getters and setters
    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getOrderId() { return orderId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public boolean isProcessed() { return processed; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getProcessedAt() { return processedAt; }
    public int getAttemptCount() { return attemptCount; }
}