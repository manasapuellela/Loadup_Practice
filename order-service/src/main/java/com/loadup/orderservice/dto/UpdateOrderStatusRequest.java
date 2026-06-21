package com.loadup.orderservice.dto;

import com.loadup.orderservice.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull(message = "status is required") OrderStatus status
) {}