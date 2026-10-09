package com.smart.erp.spike.s2.identity;

import com.smart.erp.spike.s2.tenancy.TenantDirectory;
import com.smart.erp.spike.s2.tenancy.TenantHost;
import com.smart.erp.spike.s2.tenancy.TenantRecord;
import com.smart.erp.spike.s2.tenancy.TenantStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the tenant from the host (doc §4.4 rule 1) and binds it for the rest of the chain. Runs before CSRF,
 * logout and OAuth2 login, so the organization hint and the login check see the host's tenant. Unknown host: 404;
 * tenant not ACTIVE: 503. Statuses are set directly: an error dispatch would re-enter the chain without a tenant.
 */
final class BrowserTenantFilter extends OncePerRequestFilter {

    private final TenantDirectory tenants;

    BrowserTenantFilter(TenantDirectory tenants) {
        this.tenants = tenants;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<TenantRecord> tenant =
                TenantHost.normalize(request.getServerName()).flatMap(tenants::findByHost);
        if (tenant.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (tenant.get().status() != TenantStatus.ACTIVE) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        TenantFilterChain.proceed(tenant.get().key(), chain, request, response);
    }
}
