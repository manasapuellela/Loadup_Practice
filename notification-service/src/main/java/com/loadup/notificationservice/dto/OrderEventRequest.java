package com.loadup.notificationservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OrderEventRequest(
        @NotNull UUID eventId,
        @NotNull UUID orderId,
        @NotBlank String tenantId,
        @NotBlank String customerId,
        @NotBlank String status,
        @NotBlank String eventType
) {}