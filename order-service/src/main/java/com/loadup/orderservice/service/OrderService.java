package com.loadup.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loadup.orderservice.dto.CreateOrderRequest;
import com.loadup.orderservice.entity.Order;
import com.loadup.orderservice.entity.OrderStatus;
import com.loadup.orderservice.entity.OutboxEvent;
import com.loadup.orderservice.exception.InvalidTransitionException;
import com.loadup.orderservice.exception.OrderNotFoundException;
import com.loadup.orderservice.repository.OrderRepository;
import com.loadup.orderservice.repository.OutboxEventRepository;
import com.loadup.orderservice.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository,
                         OutboxEventRepository outboxEventRepository,
                         ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    // Status is set explicitly here rather than relying solely on the entity's @PrePersist default, 
    // so the service layer's intent doesn't depend on a lifecycle hook firing correctly.
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        String tenantId = TenantContext.getCurrentTenantId();

        Order order = new Order(tenantId, request.customerId(), request.totalAmount());
        order.setStatus(OrderStatus.CREATED);
        Order saved = orderRepository.save(order);

        writeOutboxEvent(saved, "ORDER_RECEIPT");

        return saved;
        // The outbox write happens in this same @Transactional method, so the order and its event either both commit or both roll back together.
    }

    // Centralizes the transition check, lookup, and outbox write so update and cancel both go through the same guarantees.
    @Transactional
    public Order updateOrderStatus(UUID orderId, OrderStatus newStatus) {
        String tenantId = TenantContext.getCurrentTenantId();

        Order order = orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        OrderStatus currentStatus = order.getStatus();

        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new InvalidTransitionException(currentStatus, newStatus);
            // The enum itself decides what's valid, this method never duplicates that rule, it only enforces whatever the enum says.
        }

        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);

        // Cancellation gets its own event type, it was previously collapsed into ORDER_COMPLETED, which produced a confusing "is now CANCELLED. Thank you!" message. Each terminal outcome now has distinct wording.
        String eventType;
        if (newStatus == OrderStatus.CANCELLED) {
            eventType = "ORDER_CANCELLED";
        } else if (newStatus.isTerminal()) {
            eventType = "ORDER_COMPLETED";
        } else {
            eventType = "ORDER_RECEIPT";
        }

        writeOutboxEvent(saved, eventType);

        return saved;
    }

    // Cancellation is just a transition like any other, it reuses the same validation and outbox logic rather than duplicating cancel-specific rules.
    @Transactional
    public Order cancelOrder(UUID orderId) {
        return updateOrderStatus(orderId, OrderStatus.CANCELLED);
    }

    public Order getOrder(UUID orderId) {
        String tenantId = TenantContext.getCurrentTenantId();
        return orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    public List<Order> getAllOrders() {
        String tenantId = TenantContext.getCurrentTenantId();
        return orderRepository.findAllByTenantId(tenantId);
    }

    // The event is saved twice on purpose: once to get a generated ID, once more to embed that same ID inside its own JSON payload as eventId, which
    // is what makes the idempotency check on notification-service possible.
    private void writeOutboxEvent(Order order, String eventType) {
        try {
            OutboxEvent outboxEvent = new OutboxEvent(
                    order.getTenantId(), order.getId(), eventType, null
            );
            outboxEvent = outboxEventRepository.save(outboxEvent);

            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("eventId", outboxEvent.getId());
            eventPayload.put("orderId", order.getId());
            eventPayload.put("tenantId", order.getTenantId());
            eventPayload.put("customerId", order.getCustomerId());
            eventPayload.put("status", order.getStatus().name());
            eventPayload.put("eventType", eventType);

            String payloadJson = objectMapper.writeValueAsString(eventPayload);
            outboxEvent.setPayload(payloadJson);
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write outbox event", e);
            // If this ever throws, the whole transaction rolls back, an order is never left without a corresponding outbox event.
        }
    }
}