package com.loadup.orderservice.entity;

public enum OrderStatus {
    CREATED,
    CONFIRMED,
    COMPLETED,
    CANCELLED;

    // Both terminal states are grouped here since notification-service treats them the same way for "is this order done", 
    // even though they trigger different event types upstream in OrderService.
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    // Lives on the enum, not in OrderService, so any future caller asks the status itself rather than duplicating this rule elsewhere.
    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case CREATED -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}