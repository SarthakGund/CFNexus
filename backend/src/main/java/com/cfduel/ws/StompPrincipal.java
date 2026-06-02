package com.cfduel.ws;

import java.security.Principal;

/**
 * Minimal {@link Principal} wrapping the authenticated user's UUID string so that
 * {@code @MessageMapping} handlers can resolve the current user from the Principal
 * the CONNECT interceptor attaches to the STOMP session.
 */
public record StompPrincipal(String userId) implements Principal {

    @Override
    public String getName() {
        return userId;
    }
}
