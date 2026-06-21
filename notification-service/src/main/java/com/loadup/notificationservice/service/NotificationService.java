package com.loadup.notificationservice.service;

import com.loadup.notificationservice.dto.OrderEventRequest;
import com.loadup.notificationservice.entity.Notification;
import com.loadup.notificationservice.entity.NotificationType;
import com.loadup.notificationservice.repository.NotificationRepository;
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

    // validates the request body's tenant ID against the trusted header context,
    // checks for a duplicate delivery using the source event ID, and only then
    // saves a new Notification record (the simulated "send")
    public Notification recordOrderEvent(OrderEventRequest event) {
        String validatedTenantId = com.loadup.notificationservice.tenant.TenantContext.getCurrentTenantId();

        if (!validatedTenantId.equals(event.tenantId())) {
            throw new com.loadup.notificationservice.exception.TenantMismatchException(
                    validatedTenantId, event.tenantId());
        }

        Optional<Notification> existing = notificationRepository.findBySourceEventId(event.eventId());
        if (existing.isPresent()) {
            return existing.get();
        }

        NotificationType type = "ORDER_COMPLETED".equals(event.eventType())
                ? NotificationType.ORDER_COMPLETED
                : NotificationType.ORDER_RECEIPT;

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
        String tenantId = com.loadup.notificationservice.tenant.TenantContext.getCurrentTenantId();
        return notificationRepository.findAllByTenantIdAndOrderId(tenantId, orderId);
    }

    // fetches every notification belonging to the current tenant, no order filter
    public List<Notification> getAllNotifications() {
        String tenantId = com.loadup.notificationservice.tenant.TenantContext.getCurrentTenantId();
        return notificationRepository.findAllByTenantId(tenantId);
    }

    // builds the human-readable message text, different wording depending on
    // whether it's a receipt or a completion notice
    private String buildMessage(NotificationType type, OrderEventRequest event) {
        return switch (type) {
            case ORDER_RECEIPT -> "Your order " + event.orderId() + " has been received. Status: " + event.status();
            case ORDER_COMPLETED -> "Your order " + event.orderId() + " is now " + event.status() + ". Thank you!";
        };
    }

    // "Sending" simulation is the method used to represent where a real notification is
    // provider (email/SMS/push) would be called.persisting the Notification record IS the simulated send
    // the Notification record IS the simulated send
}