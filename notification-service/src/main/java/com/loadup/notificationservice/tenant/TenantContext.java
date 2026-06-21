package com.loadup.notificationservice.tenant;

public final class TenantContext {

    public static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();

    private TenantContext() {
    }

    public static String getCurrentTenantId() {
        if (!TENANT_ID.isBound()) {
            throw new IllegalStateException("No tenant ID bound to current scope");
        }
        return TENANT_ID.get();
    }
}