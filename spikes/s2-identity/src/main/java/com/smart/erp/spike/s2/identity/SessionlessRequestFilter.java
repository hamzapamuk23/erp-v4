package com.smart.erp.spike.s2.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Takes the session away from the rest of the bearer chain (ADR-0040 path 2). A stateless session creation policy only
 * stops Spring Security from using the session for the security context; behind Spring Session, anything else that
 * asks the request for its session ({@code getSession(false)}) loads the session of whatever cookie the caller sends,
 * refreshes its idle timeout and may rotate its id: the authentication details, the session fixation strategy, Spring
 * MVC's flash map manager and its request-handled event all do. Here the request has no session, and a call that would
 * make one fails instead.
 */
final class SessionlessRequestFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(new SessionlessRequest(request), response);
    }

    private static final class SessionlessRequest extends HttpServletRequestWrapper {

        SessionlessRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public @Nullable HttpSession getSession(boolean create) {
            if (create) {
                throw noSession();
            }
            return null;
        }

        @Override
        public HttpSession getSession() {
            throw noSession();
        }

        @Override
        public @Nullable String getRequestedSessionId() {
            return null;
        }

        @Override
        public boolean isRequestedSessionIdValid() {
            return false;
        }

        @Override
        public String changeSessionId() {
            throw noSession();
        }

        private static IllegalStateException noSession() {
            return new IllegalStateException("A bearer request has no session (ADR-0040 path 2)");
        }
    }
}
