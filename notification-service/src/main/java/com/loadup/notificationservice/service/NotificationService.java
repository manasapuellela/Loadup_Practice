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

    // Validates the request against the trusted header tenant & checks for a duplicate delivery scoped to that tenant, and only then persists.
    public Notification recordOrderEvent(OrderEventRequest event) {
        String validatedTenantId = TenantContext.getCurrentTenantId();

        // Header wins over body, always. This is the fix for a real gap wherethe body's tenantId was being trusted instead of the validated header.
        if (!validatedTenantId.equals(event.tenantId())) {
            throw new TenantMismatchException(validatedTenantId, event.tenantId());
        }

        Optional<Notification> existing = notificationRepository
                .findBySourceEventIdAndTenantId(event.eventId(), validatedTenantId);
        if (existing.isPresent()) {
            return existing.get();
            // Idempotent success: a retried delivery for this tenant returns the same notification instead of creating a duplicate.
        }
        // The event ID exists, but not for this tenant, this is a cross-tenant collision, not a missing record.
        //  Reject it rather than let the unique constraint throw an unhandled database exception on insert.
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
        // "Sending" is simulated: this save IS the delivery
    }

    // Scoped to the current tenant, never returns another tenant's notifications.
    public List<Notification> getNotificationsForOrder(UUID orderId) {
        String tenantId = TenantContext.getCurrentTenantId();
        return notificationRepository.findAllByTenantIdAndOrderId(tenantId, orderId);
    }

    // fetches every notification belonging to the current tenant, no order filter
    public List<Notification> getAllNotifications() {
        String tenantId = TenantContext.getCurrentTenantId();
        return notificationRepository.findAllByTenantId(tenantId);
    }

    // Wording differs by type so a cancelled order never reads like a success.
    private String buildMessage(NotificationType type, OrderEventRequest event) {
        return switch (type) {
            case ORDER_RECEIPT -> "Your order " + event.orderId() + " has been received. Status: " + event.status();
            case ORDER_COMPLETED -> "Your order " + event.orderId() + " is now complete. Thank you!";
            case ORDER_CANCELLED -> "Your order " + event.orderId() + " has been cancelled.";
        };
    }
}
