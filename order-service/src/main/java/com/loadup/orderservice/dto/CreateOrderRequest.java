package com.loadup.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotBlank(message = "customerId is required") String customerId,
        @NotNull(message = "totalAmount is required")
        @Positive(message = "totalAmount must be positive") BigDecimal totalAmount
) {}
