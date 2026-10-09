package com.smart.erp.spike.s2.sample;

import com.smart.erp.spike.s2.identity.TenantOidcUser;
import com.smart.erp.spike.s2.kernel.TenantContext;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** The application behind the security chains: just enough to show who got in and under which tenant. */
@RestController
class SampleController {

    @GetMapping("/")
    String home() {
        return "ok";
    }

    /** Browser chain only: it needs the session's {@link TenantOidcUser} and CSRF token, which a bearer call lacks. */
    @GetMapping("/bootstrap")
    Bootstrap bootstrap(@AuthenticationPrincipal TenantOidcUser user, CsrfToken csrf) {
        return new Bootstrap(
                user.tenant().value(), user.getSubject(), user.getFullName(), csrf.getHeaderName(), csrf.getToken());
    }

    @GetMapping("/api/whoami")
    WhoAmI whoAmI(Authentication authentication) {
        return new WhoAmI(
                TenantContext.require().value(),
                authentication.getName(),
                authentication.getClass().getSimpleName());
    }

    @PostMapping("/api/echo")
    Map<String, String> echo() {
        return Map.of("tenant", TenantContext.require().value());
    }
}
