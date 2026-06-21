package com.loadup.notificationservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_tenant_id", columnList = "tenant_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_notifications_source_event_id", columnNames = "source_event_id")
})
@Getter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_event_id", nullable = false, updatable = false)
    private UUID sourceEventId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, updatable = false)
    private NotificationType notificationType;

    @Column(name = "message", nullable = false, updatable = false)
    private String message;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @PrePersist
    protected void onCreate() {
        this.sentAt = Instant.now();
    }

    public Notification(UUID sourceEventId, String tenantId, UUID orderId, String customerId,
                         NotificationType notificationType, String message) {
        this.sourceEventId = sourceEventId;
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.notificationType = notificationType;
        this.message = message;
    }
}