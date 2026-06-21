package com.loadup.notificationservice.service;

import com.loadup.notificationservice.dto.OrderEventRequest;
import com.loadup.notificationservice.entity.Notification;
import com.loadup.notificationservice.entity.NotificationType;
import com.loadup.notificationservice.exception.TenantMismatchException;
import com.loadup.notificationservice.repository.NotificationRepository;
import com.loadup.notificationservice.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository);
    }

    @Test
    void recordOrderEvent_savesNotification_whenHeaderAndBodyTenantMatch() {
        ScopedValue.where(TenantContext.TENANT_ID, TENANT_A).run(() -> {
            OrderEventRequest event = buildEvent(UUID.randomUUID(), TENANT_A, "ORDER_RECEIPT", "CREATED");

            when(notificationRepository.findBySourceEventIdAndTenantId(any(UUID.class), eq(TENANT_A)))
                    .thenReturn(Optional.empty());
            when(notificationRepository.existsBySourceEventId(any(UUID.class)))
                    .thenReturn(false);
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Notification result = notificationService.recordOrderEvent(event);

            assertThat(result.getTenantId()).isEqualTo(TENANT_A);
            assertThat(result.getNotificationType()).isEqualTo(NotificationType.ORDER_RECEIPT);
            verify(notificationRepository, times(1)).save(any(Notification.class));
        });
    }

    @Test
    void recordOrderEvent_throwsTenantMismatchException_whenHeaderAndBodyDisagree() {
        ScopedValue.where(TenantContext.TENANT_ID, TENANT_A).run(() -> {
            OrderEventRequest event = buildEvent(UUID.randomUUID(), TENANT_B, "ORDER_RECEIPT", "CREATED");

            assertThatThrownBy(() -> notificationService.recordOrderEvent(event))
                    .isInstanceOf(TenantMismatchException.class)
                    .hasMessageContaining(TENANT_A)
                    .hasMessageContaining(TENANT_B);

            verify(notificationRepository, never()).save(any());
        });
    }

    @Test
    void recordOrderEvent_usesHeaderTenantId_notBodyTenantId_whenSaving() {
        ScopedValue.where(TenantContext.TENANT_ID, TENANT_A).run(() -> {
            OrderEventRequest event = buildEvent(UUID.randomUUID(), TENANT_A, "ORDER_RECEIPT", "CREATED");

            when(notificationRepository.findBySourceEventIdAndTenantId(any(UUID.class), eq(TENANT_A)))
                    .thenReturn(Optional.empty());
            when(notificationRepository.existsBySourceEventId(any(UUID.class)))
                    .thenReturn(false);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            notificationService.recordOrderEvent(event);

            verify(notificationRepository).save(captor.capture());
            assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_A);
        });
    }

    @Test
    void recordOrderEvent_setsOrderCompletedType_whenEventTypeIsOrderCompleted() {
        ScopedValue.where(TenantContext.TENANT_ID, TENANT_A).run(() -> {
            OrderEventRequest event = buildEvent(UUID.randomUUID(), TENANT_A, "ORDER_COMPLETED", "COMPLETED");

            when(notificationRepository.findBySourceEventIdAndTenantId(any(UUID.class), eq(TENANT_A)))
                    .thenReturn(Optional.empty());
            when(notificationRepository.existsBySourceEventId(any(UUID.class)))
                    .thenReturn(false);
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Notification result = notificationService.recordOrderEvent(event);

            assertThat(result.getNotificationType()).isEqualTo(NotificationType.ORDER_COMPLETED);
        });
    }

    @Test
    void recordOrderEvent_setsOrderCancelledType_whenEventTypeIsOrderCancelled() {
        ScopedValue.where(TenantContext.TENANT_ID, TENANT_A).run(() -> {
            OrderEventRequest event = buildEvent(UUID.randomUUID(), TENANT_A, "ORDER_CANCELLED", "CANCELLED");

            when(notificationRepository.findBySourceEventIdAndTenantId(any(UUID.class), eq(TENANT_A)))
                    .thenReturn(Optional.empty());
            when(notificationRepository.existsBySourceEventId(any(UUID.class)))
                    .thenReturn(false);
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Notification result = notificationService.recordOrderEvent(event);

            assertThat(result.getNotificationType()).isEqualTo(NotificationType.ORDER_CANCELLED);
        });
    }

    @Test
    void recordOrderEvent_returnsExistingNotification_whenEventIdAlreadyProcessedForSameTenant_doesNotInsertDuplicate() {
        ScopedValue.where(TenantContext.TENANT_ID, TENANT_A).run(() -> {
            UUID eventId = UUID.randomUUID();
            OrderEventRequest event = buildEvent(eventId, TENANT_A, "ORDER_RECEIPT", "CREATED");

            Notification alreadySaved = new Notification(
                    eventId, TENANT_A, event.orderId(), "cust-001",
                    NotificationType.ORDER_RECEIPT, "Your order has been received."
            );

            when(notificationRepository.findBySourceEventIdAndTenantId(eventId, TENANT_A))
                    .thenReturn(Optional.of(alreadySaved));

            Notification result = notificationService.recordOrderEvent(event);

            assertThat(result).isSameAs(alreadySaved);
            verify(notificationRepository, never()).save(any());
        });
    }

    @Test
    void recordOrderEvent_throwsTenantMismatchException_whenEventIdBelongsToDifferentTenant() {
        ScopedValue.where(TenantContext.TENANT_ID, TENANT_A).run(() -> {
            UUID eventId = UUID.randomUUID();
            OrderEventRequest event = buildEvent(eventId, TENANT_A, "ORDER_RECEIPT", "CREATED");

            when(notificationRepository.findBySourceEventIdAndTenantId(eventId, TENANT_A))
                    .thenReturn(Optional.empty());
            when(notificationRepository.existsBySourceEventId(eventId))
                    .thenReturn(true);

            assertThatThrownBy(() -> notificationService.recordOrderEvent(event))
                    .isInstanceOf(TenantMismatchException.class);

            verify(notificationRepository, never()).save(any());
        });
    }

    private OrderEventRequest buildEvent(UUID eventId, String tenantId, String eventType, String status) {
        return new OrderEventRequest(eventId, UUID.randomUUID(), tenantId, "cust-001", status, eventType);
    }
}