package com.loadup.notificationservice.service;

import com.loadup.notificationservice.dto.OrderEventRequest;
import com.loadup.notificationservice.entity.Notification;
import com.loadup.notificationservice.entity.NotificationType;
import com.loadup.notificationservice.exception.TenantMismatchException;
import com.loadup.notificationservice.repository.NotificationRepository;
import com.loadup.notificationservice.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // Validates the request body's tenant ID against the trusted header context.
    // Checks for a duplicate delivery scoped to the current tenant first (idempotent
    // success). If the event ID exists under a different tenant, rejects it as a
    // cross-tenant collision rather than leaking that notification.
    // "Sending" is simulated: persisting the Notification record IS the send,
    // per the assessment's instructions, no real email/SMS/push provider is called.
    public Notification recordOrderEvent(OrderEventRequest event) {
        String validatedTenantId = TenantContext.getCurrentTenantId();

        if (!validatedTenantId.equals(event.tenantId())) {
            throw new TenantMismatchException(validatedTenantId, event.tenantId());
        }

        Optional<Notification> existing = notificationRepository
                .findBySourceEventIdAndTenantId(event.eventId(), validatedTenantId);
        if (existing.isPresent()) {
            return existing.get();
        }

        if (notificationRepository.existsBySourceEventId(event.eventId())) {
            throw new TenantMismatchException(validatedTenantId, "unknown (event belongs to a different tenant)");
        }

        NotificationType type = switch (event.eventType()) {
            case "ORDER_COMPLETED" -> NotificationType.ORDER_COMPLETED;
            case "ORDER_CANCELLED" -> NotificationType.ORDER_CANCELLED;
            default -> NotificationType.ORDER_RECEIPT;
        };

        String message = buildMessage(type, event);

        Notification notification = new Notification(
                event.eventId(),
                validatedTenantId,
                event.orderId(),
                event.customerId(),
                type,
                message
        );

        return notificationRepository.save(notification);
    }

    // fetches all notifications for one specific order, scoped to the current tenant
    public List<Notification> getNotificationsForOrder(UUID orderId) {
        String tenantId = TenantContext.getCurrentTenantId();
        return notificationRepository.findAllByTenantIdAndOrderId(tenantId, orderId);
    }

    // fetches every notification belonging to the current tenant, no order filter
    public List<Notification> getAllNotifications() {
        String tenantId = TenantContext.getCurrentTenantId();
        return notificationRepository.findAllByTenantId(tenantId);
    }

    // builds the human-readable message text, varies by notification type
    private String buildMessage(NotificationType type, OrderEventRequest event) {
        return switch (type) {
            case ORDER_RECEIPT -> "Your order " + event.orderId() + " has been received. Status: " + event.status();
            case ORDER_COMPLETED -> "Your order " + event.orderId() + " is now complete. Thank you!";
            case ORDER_CANCELLED -> "Your order " + event.orderId() + " has been cancelled.";
        };
    }
}

    // "Sending" simulation is the method used to represent where a real notification is
    // provider (email/SMS/push) would be called.persisting the Notification record IS the simulated send
    // the Notification record IS the simulated send
