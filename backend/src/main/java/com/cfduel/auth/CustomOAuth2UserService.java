package com.cfduel.auth;

import com.cfduel.user.User;
import com.cfduel.user.UserService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Loads the Codeforces user during OAuth login, extracts the {@code handle}
 * (the configured {@code user-name-attribute}) plus available profile fields,
 * and upserts the local {@link User} record (spec §4 post-login flow steps 2-3).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserService userService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauthUser = super.loadUser(userRequest);
        Map<String, Object> attrs = oauthUser.getAttributes();

        String handle = asString(attrs.get("handle"));
        if (handle == null || handle.isBlank()) {
            throw new OAuth2AuthenticationException("Codeforces response missing 'handle' attribute");
        }

        UserService.OAuthUserInfo info = new UserService.OAuthUserInfo(
                handle,
                asLong(attrs.get("id")),
                asInt(attrs.get("rating")),
                asInt(attrs.get("maxRating")),
                asString(attrs.get("rank")),
                asString(attrs.get("maxRank")),
                firstNonBlank(asString(attrs.get("titlePhoto")), asString(attrs.get("avatar"))));

        User user = userService.upsertFromOAuth(info);
        log.debug("OAuth2 login upserted user {} (id={})", handle, user.getId());

        // user-name-attribute = "handle", so OAuth2User.getName() returns the handle.
        return oauthUser;
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Integer asInt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long asLong(Object o) {
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

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return (b != null && !b.isBlank()) ? b : null;
    }
}
