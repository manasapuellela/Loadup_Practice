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

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        String tenantId = TenantContext.getCurrentTenantId();

        Order order = new Order(tenantId, request.customerId(), request.totalAmount());
        order.setStatus(OrderStatus.CREATED);
        Order saved = orderRepository.save(order);

        writeOutboxEvent(saved, "ORDER_RECEIPT");

        return saved;
    }

    @Transactional
    public Order updateOrderStatus(UUID orderId, OrderStatus newStatus) {
        String tenantId = TenantContext.getCurrentTenantId();

        Order order = orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        OrderStatus currentStatus = order.getStatus();

        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new InvalidTransitionException(currentStatus, newStatus);
        }

        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);

        String eventType = newStatus.isTerminal() ? "ORDER_COMPLETED" : "ORDER_RECEIPT";
        writeOutboxEvent(saved, eventType);

        return saved;
    }

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
        }
    }
}