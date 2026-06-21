package com.loadup.orderservice.repository;

import com.loadup.orderservice.entity.Order;
import com.loadup.orderservice.entity.OrderStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    private UUID orderIdTenantA;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();

        Order orderA = new Order(TENANT_A, "cust-001", 49.99);
        orderA.setStatus(OrderStatus.CREATED);
        orderA = orderRepository.save(orderA);
        orderIdTenantA = orderA.getId();

        Order orderA2 = new Order(TENANT_A, "cust-002", 19.99);
        orderA2.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(orderA2);

        Order orderB = new Order(TENANT_B, "cust-003", 99.99);
        orderB.setStatus(OrderStatus.CREATED);
        orderRepository.save(orderB);
    }

    @Test
    void findByIdAndTenantId_returnsOrder_whenTenantMatches() {
        Optional<Order> result = orderRepository.findByIdAndTenantId(orderIdTenantA, TENANT_A);

        assertThat(result).isPresent();
        assertThat(result.get().getCustomerId()).isEqualTo("cust-001");
        assertThat(result.get().getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    void findByIdAndTenantId_returnsEmpty_whenTenantDoesNotMatch() {
        Optional<Order> result = orderRepository.findByIdAndTenantId(orderIdTenantA, TENANT_B);

        assertThat(result).isEmpty();
    }

    @Test
    void findAllByTenantId_returnsOnlyOrdersForThatTenant() {
        List<Order> tenantAOrders = orderRepository.findAllByTenantId(TENANT_A);
        List<Order> tenantBOrders = orderRepository.findAllByTenantId(TENANT_B);

        assertThat(tenantAOrders).hasSize(2);
        assertThat(tenantAOrders).allMatch(o -> o.getTenantId().equals(TENANT_A));

        assertThat(tenantBOrders).hasSize(1);
        assertThat(tenantBOrders.get(0).getCustomerId()).isEqualTo("cust-003");
    }

    @Test
    void findAllByTenantId_returnsEmptyList_forUnknownTenant() {
        List<Order> result = orderRepository.findAllByTenantId("tenant-nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void tenantIdIsImmutable_onUpdate() {
        Order order = orderRepository.findByIdAndTenantId(orderIdTenantA, TENANT_A).orElseThrow();

        order.setTenantId(TENANT_B);
        order.setCustomerId("cust-001-updated");
        orderRepository.save(order);
        orderRepository.flush();

        entityManager.clear();

        Optional<Order> reloaded = orderRepository.findByIdAndTenantId(orderIdTenantA, TENANT_A);

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getTenantId()).isEqualTo(TENANT_A);
        assertThat(reloaded.get().getCustomerId()).isEqualTo("cust-001-updated");
    }

    @Test
    void orderPersistsWithGeneratedIdAndTimestamps() {
        Order order = new Order(TENANT_A, "cust-999", 10.0);
        order.setStatus(OrderStatus.CREATED);

        Order saved = orderRepository.save(order);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}