package com.cfduel.auth;

import com.cfduel.user.User;
import com.cfduel.user.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Runs after a successful Codeforces OAuth login (spec §4 steps 5-6): ensures
 * the user is upserted, stores {@code userId} in the HTTP session, and redirects
 * to the SPA dashboard.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    /** Session attribute key shared with controllers to resolve the current user. */
    public static final String SESSION_USER_ID = "userId";

    private final UserService userService;
    private final SimpleUrlAuthenticationSuccessHandler redirectStrategy =
            new SimpleUrlAuthenticationSuccessHandler();

    @Value("${app.frontend-origin:http://localhost:3000}")
    private String frontendOrigin;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {

        if (authentication.getPrincipal() instanceof OAuth2User oauthUser) {
            String handle = String.valueOf(oauthUser.getAttribute("handle"));
            User user = userService.findByHandle(handle).orElseGet(() -> {
                // Defensive: CustomOAuth2UserService should already have upserted.
                Long id = toLong(oauthUser.getAttribute("id"));
                return userService.upsertFromOAuth(new UserService.OAuthUserInfo(
                        handle, id, null, null, null, null, null));
            });

            HttpSession session = request.getSession(true);
            session.setAttribute(SESSION_USER_ID, user.getId());
            log.debug("Stored userId {} in session for handle {}", user.getId(), handle);
        } else {
            log.warn("OAuth2 success with unexpected principal type: {}",
                    authentication.getPrincipal());
        }

        String target = frontendOrigin.replaceAll("/+$", "") + "/dashboard";
        redirectStrategy.setDefaultTargetUrl(target);
        redirectStrategy.onAuthenticationSuccess(request, response, authentication);
    }

    private static Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
