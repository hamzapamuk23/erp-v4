package com.smart.erp.spike.s2.identity;

import com.smart.erp.spike.s2.kernel.TenantContext;
import com.smart.erp.spike.s2.kernel.TenantKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;

/**
 * Runs the rest of a filter chain with a tenant bound (TenantContext's scope API, S1 B11): the binding ends with the
 * request, whatever happens downstream.
 */
final class TenantFilterChain {

    private TenantFilterChain() {}

    static void proceed(TenantKey tenant, FilterChain chain, ServletRequest request, ServletResponse response)
            throws IOException, ServletException {
        try {
            TenantContext.run(tenant, () -> {
                try {
                    chain.doFilter(request, response);
                } catch (IOException | ServletException e) {
                    throw new CheckedFilterException(e);
                }
            });
        } catch (CheckedFilterException e) {
            e.rethrow();
        }
    }

    /** Carries the chain's checked exception through the Runnable; rethrown with its type intact. */
    private static final class CheckedFilterException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        CheckedFilterException(Exception cause) {
            super(cause);
        }

        void rethrow() throws IOException, ServletException {
            if (getCause() instanceof IOException io) {
                throw io;
            }
            throw (ServletException) getCause();
        }
    }
}
