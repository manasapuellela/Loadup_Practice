package com.loadup.notificationservice.controller;

import com.loadup.notificationservice.dto.OrderEventRequest;
import com.loadup.notificationservice.entity.Notification;
import com.loadup.notificationservice.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/order-events")
    public ResponseEntity<Void> receiveOrderEvent(@Valid @RequestBody OrderEventRequest event) {
        notificationService.recordOrderEvent(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<Notification>> getNotificationsForOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(notificationService.getNotificationsForOrder(orderId));
    }

    @GetMapping
    public ResponseEntity<List<Notification>> getAllNotifications() {
        return ResponseEntity.ok(notificationService.getAllNotifications());
    }
}