package com.cfduel.auth;

import com.cfduel.user.User;
import com.cfduel.user.UserService;
import com.cfduel.user.dto.UserProfileDto;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LOCAL-ONLY dev login (plan-v2 Stage 3). Upserts a fake {@link User} by handle and
 * establishes the same session ({@code userId} attribute) the OAuth2 success handler
 * sets — so single-machine, two-player duel testing works without two real Codeforces
 * accounts or CF being reachable.
 *
 * <p>Guarded by {@code @Profile("local")} so the bean (and its route) never exist in
 * any other profile. The matching security permit/CSRF exemption in
 * {@code SecurityConfig} is likewise gated on the {@code local} profile.
 */
@RestController
@RequestMapping("/api/dev")
@Profile("local")
@Validated
@Slf4j
@RequiredArgsConstructor
public class DevAuthController {

    private static final String HANDLE_REGEX = "^[a-zA-Z0-9_\\-]{1,24}$";

    private final UserService userService;

    /** POST /api/dev/login?handle=foo — upsert a dev user and log them in via session. */
    @PostMapping("/login")
    public ResponseEntity<UserProfileDto> login(
            @RequestParam("handle")
            @Pattern(regexp = HANDLE_REGEX, message = "handle must be 1-24 chars of letters, digits, _ or -")
            String handle,
            HttpSession session) {

        User user = userService.upsertFromOAuth(new UserService.OAuthUserInfo(
                handle, null, 1500, 1500, "specialist", "specialist", null));

        // Controllers resolve the user from this attribute; the OAuth2 success handler sets it too.
        session.setAttribute(OAuth2SuccessHandler.SESSION_USER_ID, user.getId());

        // Establish a Spring Security context so endpoints behind .authenticated() (e.g. /api/auth/me)
        // accept the request, and persist it to the session the same way the OAuth login flow does.
        var authentication = new UsernamePasswordAuthenticationToken(
                handle, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        log.warn("DEV LOGIN: established session for handle '{}' (userId={})", handle, user.getId());
        return ResponseEntity.ok(UserProfileDto.from(user));
    }
}
