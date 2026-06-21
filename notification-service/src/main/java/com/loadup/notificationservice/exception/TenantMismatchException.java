package com.loadup.notificationservice.exception;

public class TenantMismatchException extends RuntimeException {
    public TenantMismatchException(String headerTenantId, String bodyTenantId) {
        super("Tenant mismatch: header tenant '" + headerTenantId
                + "' does not match payload tenant '" + bodyTenantId + "'");
    }
}