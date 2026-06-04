package com.cfduel.config.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link ApiRateLimitInterceptor} for the REST API (spec §11).
 *
 * <p>The interceptor covers {@code /api/**} but is excluded from public,
 * unauthenticated endpoints — {@code /api/public/**} and the public profile /
 * leaderboard reads that {@code SecurityConfig} permits anonymously. Those
 * carry no session {@code userId}, so the interceptor would skip them anyway;
 * the explicit excludes keep the intent clear and avoid touching the session
 * for cacheable public reads.
 */
@Configuration
@RequiredArgsConstructor
public class RateLimitWebConfig implements WebMvcConfigurer {

    private final ApiRateLimitInterceptor apiRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiRateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        // Explicitly public / unauthenticated API surface (see SecurityConfig).
                        "/api/public/**",
                        "/api/auth/**",
                        "/api/users/search",
                        "/api/users/*",
                        "/api/users/*/**",
                        "/api/achievements",
                        "/api/leaderboard");
    }
}
