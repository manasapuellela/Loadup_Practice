package com.loadup.notificationservice.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TenantFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    // Runs before any controller, so a missing tenant never reaches business logic at all, the rejection happens at the earliest possible point.
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String tenantId = request.getHeader(TENANT_HEADER);

        if (tenantId == null || tenantId.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Missing required header: " + TENANT_HEADER + "\"}");
            return;
            // This error is written manually here rather than through GlobalExceptionHandler,
            // since the filter runs before Spring's exception-handling machinery is even in play. Known inconsistency,
            // documented in the README rather than silently left unexplained.
        }

        try {
            ScopedValue.where(TenantContext.TENANT_ID, tenantId)
                    .run(() -> {
                        try {
                            filterChain.doFilter(request, response);
                        } catch (ServletException | IOException e) {
                            throw new RuntimeException(e);
                            // ScopedValue.run() only accepts a Runnable, which can't declare checked exceptions,
                            // so they're wrapped here and unwrapped just below.
                        }
                    });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof ServletException se) throw se;
            if (e.getCause() instanceof IOException ie) throw ie;
            throw e;
            // Unwraps back to the original checked exception type, so callers upstream see the real exception, not a generic RuntimeException.
        }
    }
}