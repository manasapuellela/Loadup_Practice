package com.loadup.orderservice.exception;

import com.loadup.orderservice.entity.OrderStatus;

public class InvalidTransitionException extends RuntimeException {
    public InvalidTransitionException(OrderStatus from, OrderStatus to) {
        super("Cannot transition order from " + from + " to " + to);
    }
}