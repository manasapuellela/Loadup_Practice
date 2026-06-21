package com.loadup.orderservice.dto;

import com.loadup.orderservice.entity.Order;
import com.loadup.orderservice.entity.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public class OrderResponse {

    private UUID id;
    private String customerId;
    private Double totalAmount;
    private OrderStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public static OrderResponse fromEntity(Order order) {
        OrderResponse response = new OrderResponse();
        response.id = order.getId();
        response.customerId = order.getCustomerId();
        response.totalAmount = order.getTotalAmount();
        response.status = order.getStatus();
        response.createdAt = order.getCreatedAt();
        response.updatedAt = order.getUpdatedAt();
        return response;
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}