package com.cfduel.auth;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Loads the Codeforces user during OpenID Connect login.
 *
 * <p>Codeforces is an OIDC provider (see {@code /.well-known/openid-configuration}):
 * it only supports the {@code openid} scope, has no userinfo endpoint, and returns an
 * HS256-signed {@code id_token} (verified via {@code SecurityConfig#idTokenDecoderFactory})
 * that carries the user's handle. We extract the handle from the id_token claims, then
 * reuse {@link CustomOAuth2UserService#upsertByHandle(String)} to pull the full public
 * profile (rating, rank, avatar) and upsert the local user.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final CustomOAuth2UserService cfUserService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcIdToken idToken = userRequest.getIdToken();
        Map<String, Object> claims = idToken.getClaims();
        // TEMP DEBUG: surface the exact CF id_token claim names. Drop to debug/remove
        // once the handle claim is confirmed.
        log.info("CF id_token claims: {}", claims);

        String handle = firstNonBlank(
                asString(claims.get("handle")),
                asString(claims.get("preferred_username")),
                asString(claims.get("name")),
                idToken.getSubject());
        if (handle == null || handle.isBlank()) {
            throw new OAuth2AuthenticationException(
                    "Cannot determine Codeforces handle from id_token claims: " + claims);
        }

        Map<String, Object> profile = cfUserService.upsertByHandle(handle);

        // Expose "handle" (and the CF user id) as principal attributes so the rest of
        // the app — e.g. OAuth2SuccessHandler — can read them uniformly, regardless of
        // CF's native claim naming.
        Map<String, Object> attributes = new HashMap<>(claims);
        attributes.put("handle", handle);
        if (profile.get("id") != null) {
            attributes.put("id", profile.get("id"));
        }
        OidcUserInfo userInfo = new OidcUserInfo(attributes);

        return new DefaultOidcUser(
                AuthorityUtils.createAuthorityList("ROLE_USER"), idToken, userInfo, "handle");
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
