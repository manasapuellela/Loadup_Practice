package com.loadup.notificationservice.service;

import com.loadup.notificationservice.dto.OrderEventRequest;
import com.loadup.notificationservice.entity.Notification;
import com.loadup.notificationservice.entity.NotificationType;
import com.loadup.notificationservice.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Notification recordOrderEvent(OrderEventRequest event) {
        NotificationType type = "ORDER_COMPLETED".equals(event.getEventType())
                ? NotificationType.ORDER_COMPLETED
                : NotificationType.ORDER_RECEIPT;

        String message = buildMessage(type, event);

        Notification notification = new Notification(
                event.getTenantId(),
                event.getOrderId(),
                event.getCustomerId(),
                type,
                message
        );

        return notificationRepository.save(notification);
    }

    public List<Notification> getNotificationsForOrder(UUID orderId) {
        String tenantId = com.loadup.notificationservice.tenant.TenantContext.getCurrentTenantId();
        return notificationRepository.findAllByTenantIdAndOrderId(tenantId, orderId);
    }

    public List<Notification> getAllNotifications() {
        String tenantId = com.loadup.notificationservice.tenant.TenantContext.getCurrentTenantId();
        return notificationRepository.findAllByTenantId(tenantId);
    }

    private String buildMessage(NotificationType type, OrderEventRequest event) {
        return switch (type) {
            case ORDER_RECEIPT -> "Your order " + event.getOrderId() + " has been received. Status: " + event.getStatus();
            case ORDER_COMPLETED -> "Your order " + event.getOrderId() + " is now " + event.getStatus() + ". Thank you!";
        };
    }

    // "Sending" simulation: this method represents where a real notification
    // provider (email/SMS/push) would be called. For this assessment, persisting
    // the Notification record IS the simulated send, per the assessment's instructions.
}