package com.loadup.orderservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_processed", columnList = "processed")
})
@Getter
@NoArgsConstructor
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

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "processed", nullable = false)
    private boolean processed = false;

    @Column(name = "failed", nullable = false)
    private boolean failed = false;

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

    public OutboxEvent(String tenantId, UUID orderId, String eventType, String payload) {
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.eventType = eventType;
        this.payload = payload;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public void markProcessed() {
        this.processed = true;
        this.processedAt = Instant.now();
    }

    public void markFailed() {
        this.failed = true;
    }

    public void incrementAttempt() {
        this.attemptCount++;
    }
}
