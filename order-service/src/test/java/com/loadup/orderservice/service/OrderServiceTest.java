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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OrderService orderService;

    private static final String TENANT_A = "tenant-a";

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, outboxEventRepository, new ObjectMapper());

        lenient().when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> {
                    OutboxEvent event = invocation.getArgument(0);
                    if (event.getId() == null) {
                        event.setId(UUID.randomUUID());
                    }
                    return event;
                });
    }

    @Test
    void createOrder_savesOrderAndWritesOutboxEvent_withinTenantScope() {
        ScopedValue.where(TenantContext.TENANT_ID, TENANT_A).run(() -> {
            CreateOrderRequest request = new CreateOrderRequest("cust-001", BigDecimal.valueOf(49.99));

            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(UUID.randomUUID());
                return order;
            });

            Order result = orderService.createOrder(request);

            assertThat(result.getTenantId()).isEqualTo(TENANT_A);
            assertThat(result.getCustomerId()).isEqualTo("cust-001");
            assertThat(result.getTotalAmount()).isEqualTo(BigDecimal.valueOf(49.99));

            verify(orderRepository, times(1)).save(any(Order.class));
            verify(outboxEventRepository, atLeastOnce()).save(any());
        });
    }

    @Test
    void updateOrderStatus_validTransition_succeedsAndWritesOutboxEvent() {
        ScopedValue.where(TenantContext.TENANT_ID, TENANT_A).run(() -> {
            UUID orderId = UUID.randomUUID();
            Order existingOrder = new Order(TENANT_A, "cust-001", BigDecimal.valueOf(49.99));
            existingOrder.setId(orderId);
            existingOrder.setStatus(OrderStatus.CREATED);

            when(orderRepository.findByIdAndTenantId(orderId, TENANT_A))
                    .thenReturn(Optional.of(existingOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            Order result = orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(outboxEventRepository, atLeastOnce()).save(any());
        });
    }

    @Test
    void updateOrderStatus_invalidTransition_throwsInvalidTransitionException() {
        ScopedValue.where(TenantContext.TENANT_ID, TENANT_A).run(() -> {
            UUID orderId = UUID.randomUUID();
            Order existingOrder = new Order(TENANT_A, "cust-001", BigDecimal.valueOf(49.99));
            existingOrder.setId(orderId);
            existingOrder.setStatus(OrderStatus.COMPLETED);

            when(orderRepository.findByIdAndTenantId(orderId, TENANT_A))
                    .thenReturn(Optional.of(existingOrder));

            assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, OrderStatus.CANCELLED))
                    .isInstanceOf(InvalidTransitionException.class)
                    .hasMessageContaining("COMPLETED")
                    .hasMessageContaining("CANCELLED");

            verify(orderRepository, never()).save(any());
            verify(outboxEventRepository, never()).save(any());
        });
    }

    @Test
    void updateOrderStatus_orderNotFoundInTenant_throwsOrderNotFoundException() {
        ScopedValue.where(TenantContext.TENANT_ID, TENANT_A).run(() -> {
            UUID orderId = UUID.randomUUID();

            when(orderRepository.findByIdAndTenantId(orderId, TENANT_A))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED))
                    .isInstanceOf(OrderNotFoundException.class);
        });
    }

    @Test
    void updateOrderStatus_terminalState_writesOrderCompletedEventType() {
        ScopedValue.where(TenantContext.TENANT_ID, TENANT_A).run(() -> {
            UUID orderId = UUID.randomUUID();
            Order existingOrder = new Order(TENANT_A, "cust-001", BigDecimal.valueOf(49.99));
            existingOrder.setId(orderId);
            existingOrder.setStatus(OrderStatus.CONFIRMED);

            when(orderRepository.findByIdAndTenantId(orderId, TENANT_A))
                    .thenReturn(Optional.of(existingOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);

            orderService.updateOrderStatus(orderId, OrderStatus.COMPLETED);

            verify(outboxEventRepository, atLeastOnce()).save(captor.capture());
            assertThat(captor.getAllValues())
                    .anyMatch(e -> "ORDER_COMPLETED".equals(e.getEventType()));
        });
    }

    @Test
    void cancelOrder_fromCreatedState_succeeds() {
        ScopedValue.where(TenantContext.TENANT_ID, TENANT_A).run(() -> {
            UUID orderId = UUID.randomUUID();
            Order existingOrder = new Order(TENANT_A, "cust-001", BigDecimal.valueOf(49.99));
            existingOrder.setId(orderId);
            existingOrder.setStatus(OrderStatus.CREATED);

            when(orderRepository.findByIdAndTenantId(orderId, TENANT_A))
                    .thenReturn(Optional.of(existingOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            Order result = orderService.cancelOrder(orderId);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        });
    }

    @Test
    void getOrder_doesNotLeakAcrossTenants() {
        ScopedValue.where(TenantContext.TENANT_ID, "tenant-b").run(() -> {
            UUID orderId = UUID.randomUUID();

            when(orderRepository.findByIdAndTenantId(orderId, "tenant-b"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrder(orderId))
                    .isInstanceOf(OrderNotFoundException.class);

            verify(orderRepository).findByIdAndTenantId(orderId, "tenant-b");
            verify(orderRepository, never()).findById(any());
        });
    }
}