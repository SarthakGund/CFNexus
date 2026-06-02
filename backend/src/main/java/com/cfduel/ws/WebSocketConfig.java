package com.cfduel.ws;

import com.cfduel.auth.OAuth2SuccessHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.Map;

/**
 * STOMP-over-WebSocket configuration (spec §5).
 *
 * <ul>
 *   <li>SockJS endpoint at {@code /ws}, restricted to the configured frontend origin.</li>
 *   <li>Simple in-memory broker on {@code /topic} and {@code /queue}; app prefix {@code /app};
 *       user prefix {@code /user}.</li>
 *   <li>10KB inbound message size cap.</li>
 *   <li>CONNECT interceptor that authenticates from the shared HTTP session and attaches a
 *       {@link StompPrincipal} carrying the user UUID.</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.frontend-origin:http://localhost:3000}")
    private String frontendOrigin;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(frontendOrigin)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(10 * 1024);
        registration.setSendBufferSizeLimit(512 * 1024);
        registration.setSendTimeLimit(20 * 1000);
    }

    /**
     * Authenticate the STOMP CONNECT frame using the HTTP session attribute set by
     * {@link OAuth2SuccessHandler}. Because SockJS shares the HTTP session, the session
     * attributes (including {@code userId}) are exposed on the STOMP session attributes.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
                    return message;
                }

                Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
                if (sessionAttrs == null) {
                    // Session attributes unavailable (e.g. some dev/test transports): allow so
                    // local development keeps working; handlers fall back to header resolution.
                    log.warn("STOMP CONNECT without session attributes; allowing unauthenticated (dev fallback)");
                    return message;
                }

                Object userId = sessionAttrs.get(OAuth2SuccessHandler.SESSION_USER_ID);
                if (userId == null) {
                    throw new MessageDeliveryException("unauthenticated");
                }

                accessor.setUser(new StompPrincipal(userId.toString()));
                return message;
            }
        });
    }
}
