package com.cfduel.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Forces the deferred {@link CsrfToken} to be materialised on every request.
 *
 * <p>Spring Security 6 loads the CSRF token lazily: the {@code XSRF-TOKEN} cookie
 * is only written once the token is actually read. A SPA that has not yet
 * performed a state-changing request would therefore never receive the cookie,
 * making its first POST fail with 403. Reading {@code csrfToken.getToken()} here
 * triggers {@link org.springframework.security.web.csrf.CookieCsrfTokenRepository}
 * to write the cookie on safe (GET) requests too.
 */
public final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
        if (csrfToken != null) {
            // Render the token value, which causes the deferred repository to persist the cookie.
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
