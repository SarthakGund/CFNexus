package com.cfduel.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * CSRF request handler tuned for a JavaScript SPA that reads the {@code XSRF-TOKEN}
 * cookie and echoes it back in the {@code X-XSRF-TOKEN} header.
 *
 * <p>This is the request handler from Spring Security's official SPA guide. It keeps
 * BREACH protection (XOR masking) when the token is rendered, but resolves the token
 * value as a plain (raw) value when it arrives via the request header — which is how
 * the cookie value is sent back. Without this, the default
 * {@link XorCsrfTokenRequestAttributeHandler} would try to un-mask the raw cookie
 * value sent in the header and reject every state-changing request with 403.
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            Supplier<CsrfToken> csrfToken) {
        // Always use XOR masking when rendering the token (BREACH protection); the
        // cookie repository still stores the raw token value.
        this.xor.handle(request, response, csrfToken);
        // Cause the token to be loaded so the cookie is written (see CsrfCookieFilter).
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        // If the token arrives in the header it is the raw cookie value -> resolve plainly.
        // If it arrives as a request parameter it is the masked value -> XOR resolve.
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        return StringUtils.hasText(headerValue)
                ? this.plain.resolveCsrfTokenValue(request, csrfToken)
                : this.xor.resolveCsrfTokenValue(request, csrfToken);
    }
}
