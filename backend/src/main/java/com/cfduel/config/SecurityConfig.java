package com.cfduel.config;

import com.cfduel.auth.CustomOidcUserService;
import com.cfduel.auth.OAuth2SuccessHandler;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 6 configuration (spec §4).
 *
 * <h2>CSRF</h2>
 * The SPA is cookie/session based, so CSRF protection stays ENABLED for REST.
 * We use {@link CookieCsrfTokenRepository#withHttpOnlyFalse()} so the Next.js
 * client can read the {@code XSRF-TOKEN} cookie and echo it back in the
 * {@code X-XSRF-TOKEN} header. CSRF is ignored only for the STOMP WebSocket
 * handshake ({@code /ws/**}), which is authenticated via the session cookie and
 * cannot carry a CSRF header through the SockJS transport.
 *
 * <h2>Sessions</h2>
 * Session creation/cookie flags (Secure, HttpOnly, SameSite) are owned by
 * Agent A in application.yml; this class does not duplicate them.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOidcUserService customOidcUserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    @Value("${app.frontend-origin:http://localhost:3000}")
    private String frontendOrigin;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers(new AntPathRequestMatcher("/ws/**")))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/",
                        "/error",
                        "/login",
                        "/oauth2/**",
                        "/login/oauth2/**",
                        "/api/public/**",
                        "/ws/**",
                        "/actuator/health",
                        "/checkpoint/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html").permitAll()
                // Public profile reads (SSR/SEO, spec §15): handle lookup + history.
                .requestMatchers(HttpMethod.GET,
                        "/api/users/*",
                        "/api/users/*/rating-history",
                        "/api/users/*/match-history",
                        "/api/users/*/cf-rating-history",
                        // Achievements grid is part of the public profile (spec §15):
                        // catalogue + a user's earned set are readable anonymously.
                        "/api/users/*/achievements",
                        "/api/achievements",
                        // Public leaderboard (landing-page preview + /leaderboard, spec §13, §19).
                        "/api/leaderboard").permitAll()
                .anyRequest().authenticated())
            .oauth2Login(oauth -> oauth
                .tokenEndpoint(token -> token.accessTokenResponseClient(loggingTokenResponseClient()))
                .userInfoEndpoint(userInfo -> userInfo.oidcUserService(customOidcUserService))
                .successHandler(oAuth2SuccessHandler)
                .failureHandler((req, res, ex) -> {
                    // TEMP DEBUG: dump the raw callback query string so we can see whether
                    // Codeforces returned ?error=... (provider rejected us) or ?code=&state=
                    // (state mismatch). Remove once OAuth login is confirmed working.
                    log.error("OAuth2 callback query string: {}", req.getQueryString());
                    log.error("OAuth2 login failure: [{}] {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
                    String base = frontendOrigin.replaceAll("/+$", "");
                    String error = java.net.URLEncoder.encode(ex.getMessage(), java.nio.charset.StandardCharsets.UTF_8);
                    res.sendRedirect(base + "/login?error=" + error);
                }))
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/api/auth/logout"))
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("CFNEXUSSESSION", "JSESSIONID", "SESSION")
                .logoutSuccessHandler((req, res, authn) -> res.setStatus(HttpStatus.OK.value())))
            // Return 401 (not a redirect to the OAuth provider) for unauthenticated
            // API/XHR requests so the SPA can react instead of following a redirect.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

        return http.build();
    }

    /**
     * TEMP DEBUG: authorization-code token client with an interceptor that logs the exact
     * request we send to the Codeforces token endpoint and the raw response body Codeforces
     * returns. Remove once OAuth login is confirmed working.
     */
    @Bean
    public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> loggingTokenResponseClient() {
        DefaultAuthorizationCodeTokenResponseClient client = new DefaultAuthorizationCodeTokenResponseClient();

        RestTemplate rest = new RestTemplate(Arrays.asList(
                new FormHttpMessageConverter(),
                new OAuth2AccessTokenResponseHttpMessageConverter()));
        rest.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        // Buffering factory lets the interceptor read the response body without consuming it.
        rest.setRequestFactory(new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory()));
        rest.getInterceptors().add((request, body, execution) -> {
            log.info("CF TOKEN REQ -> {} {}\n  headers={}\n  body={}",
                    request.getMethod(), request.getURI(), request.getHeaders(),
                    new String(body, StandardCharsets.UTF_8));
            ClientHttpResponse response = execution.execute(request, body);
            String respBody = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
            log.info("CF TOKEN RESP <- {}\n  headers={}\n  body={}",
                    response.getStatusCode(), response.getHeaders(), respBody);
            return response;
        });

        client.setRestOperations(rest);
        return client;
    }

    /**
     * Codeforces signs the OIDC {@code id_token} with HS256 (symmetric, using the
     * client secret) — see its {@code /.well-known/openid-configuration}. Spring's
     * default {@link OidcIdTokenDecoderFactory} expects RS256 + a JWKS endpoint, so
     * we override the expected JWS algorithm to HS256; the factory then verifies the
     * token with the client secret as the MAC key.
     */
    @Bean
    public JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory() {
        OidcIdTokenDecoderFactory factory = new OidcIdTokenDecoderFactory();
        factory.setJwsAlgorithmResolver(registration -> MacAlgorithm.HS256);
        return factory;
    }

    /** CORS for the configured frontend origin, allowing credentials (cookies). */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontendOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
