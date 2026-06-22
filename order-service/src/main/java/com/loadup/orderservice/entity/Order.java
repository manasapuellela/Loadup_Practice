package com.loadup.orderservice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_tenant_id", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // updatable = false enforces immutability at the database level.
    // Even if application code calls setTenantId() on an existing order, Hibernate excludes this column from the generated UPDATE statement entirely.
    @NotBlank
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @NotBlank
    @Column(name = "customer_id", nullable = false)
    private String customerId;

    // BigDecimal, not Double, money needs exact decimal representation, floating point arithmetic can silently drift on currency values.
    @NotNull
    @Positive
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Defaults status to CREATED if nothing set it explicitly, a safety net, not the primary mechanism, OrderService sets status directly too.
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = OrderStatus.CREATED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Order(String tenantId, String customerId, BigDecimal totalAmount) {
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.totalAmount = totalAmount;
    }
}