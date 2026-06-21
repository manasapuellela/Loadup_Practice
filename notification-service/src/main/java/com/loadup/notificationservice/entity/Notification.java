package com.loadup.notificationservice.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_tenant_id", columnList = "tenant_id")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

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

    public Notification() {
    }

    public Notification(String tenantId, UUID orderId, String customerId,
                         NotificationType notificationType, String message) {
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.notificationType = notificationType;
        this.message = message;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public NotificationType getNotificationType() { return notificationType; }
    public String getMessage() { return message; }
    public Instant getSentAt() { return sentAt; }
}