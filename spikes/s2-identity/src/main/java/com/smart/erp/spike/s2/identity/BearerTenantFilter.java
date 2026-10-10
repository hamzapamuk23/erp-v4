package com.smart.erp.spike.s2.identity;

import com.smart.erp.spike.s2.kernel.TenantKey;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Checks the token's tenant against its status and the host (design decision 3), then binds it for the request. */
final class BearerTenantFilter extends OncePerRequestFilter {

    private final TenantDirectory tenants;

    BearerTenantFilter(TenantDirectory tenants) {
        this.tenants = tenants;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!(SecurityContextHolder.getContext().getAuthentication()
                instanceof TenantJwtAuthentication authentication)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        TenantRecord tenant = authentication.tenant();
        if (tenant.status() != TenantStatus.ACTIVE) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        Optional<TenantKey> hostTenant = TenantHost.normalize(request.getServerName())
                .flatMap(tenants::findByHost)
                .map(TenantRecord::key);
        if (hostTenant.isPresent() && !hostTenant.get().equals(tenant.key())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        TenantFilterChain.proceed(tenant.key(), chain, request, response);
    }
}
