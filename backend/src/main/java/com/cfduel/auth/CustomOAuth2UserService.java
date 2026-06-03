package com.cfduel.auth;

import com.cfduel.user.User;
import com.cfduel.user.UserService;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Loads the Codeforces user during OAuth login.
 *
 * Strategy:
 *  1. Codeforces includes "handle" in the access-token response additional params — use it.
 *  2. Call the public https://codeforces.com/api/user.info?handles={handle} (no auth needed)
 *     to get the full profile (rating, rank, avatar, …).
 *  3. Unwrap result[0] from the {"status":"OK","result":[...]} envelope and build OAuth2User.
 *
 * This avoids relying on bearer-token auth against the user-info endpoint, which
 * Codeforces does not support in the standard OAuth2 UserInfo fashion.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final String CF_USER_INFO = "https://codeforces.com/api/user.info?handles=";

    private final UserService userService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // Step 1: get handle from the token response (Codeforces includes it there)
        Map<String, Object> tokenParams = userRequest.getAdditionalParameters();
        log.debug("Token additional parameters: {}", tokenParams);

        String handle = asString(tokenParams.get("handle"));
        if (handle == null || handle.isBlank()) {
            // Fallback: token params didn't include handle — try the user-info endpoint with bearer
            log.warn("Handle not in token params, falling back to bearer user-info call. Params: {}", tokenParams);
            handle = fetchHandleViaBearer(userRequest);
        }

        if (handle == null || handle.isBlank()) {
            throw new OAuth2AuthenticationException("Cannot determine Codeforces handle after OAuth flow");
        }

        // Steps 2-3: fetch the full CF profile + upsert the local user.
        Map<String, Object> attrs = upsertByHandle(handle);

        Collection<GrantedAuthority> authorities = AuthorityUtils.createAuthorityList("ROLE_USER");
        return new DefaultOAuth2User(authorities, attrs, "handle");
    }

    /**
     * Fetches the full Codeforces profile for {@code handle} from the public
     * user.info API and upserts the local user. Returns the CF profile attributes
     * (id, rating, rank, avatar, …). Shared by the OAuth2 and OIDC login paths.
     */
    public Map<String, Object> upsertByHandle(String handle) {
        Map<String, Object> attrs = fetchCfUserInfo(handle);
        UserService.OAuthUserInfo info = new UserService.OAuthUserInfo(
                handle,
                asLong(attrs.get("id")),
                asInt(attrs.get("rating")),
                asInt(attrs.get("maxRating")),
                asString(attrs.get("rank")),
                asString(attrs.get("maxRank")),
                firstNonBlank(asString(attrs.get("titlePhoto")), asString(attrs.get("avatar"))));
        User user = userService.upsertFromOAuth(info);
        log.debug("OAuth2/OIDC login upserted user {} (id={})", handle, user.getId());
        return attrs;
    }

    private Map<String, Object> fetchCfUserInfo(String handle) {
        String url = CF_USER_INFO + handle;
        log.debug("Fetching CF user info from {}", url);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});

            Map<String, Object> body = response.getBody();
            if (body == null || !"OK".equals(body.get("status"))) {
                log.error("CF user.info returned non-OK: {}", body);
                throw new OAuth2AuthenticationException("Codeforces user info request failed: " + body);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> result = (List<Map<String, Object>>) body.get("result");
            if (result == null || result.isEmpty()) {
                throw new OAuth2AuthenticationException("Codeforces user info result is empty for handle: " + handle);
            }
            return new HashMap<>(result.get(0));
        } catch (OAuth2AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("HTTP error fetching CF user info for {}: {}", handle, e.getMessage(), e);
            throw new OAuth2AuthenticationException("Failed to fetch Codeforces user info: " + e.getMessage());
        }
    }

    private String fetchHandleViaBearer(OAuth2UserRequest userRequest) {
        String accessToken = userRequest.getAccessToken().getTokenValue();
        String userInfoUri = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUri();
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(accessToken);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    userInfoUri,
                    org.springframework.http.HttpMethod.GET,
                    new org.springframework.http.HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});
            Map<String, Object> body = response.getBody();
            if (body != null && "OK".equals(body.get("status"))) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> result = (List<Map<String, Object>>) body.get("result");
                if (result != null && !result.isEmpty()) {
                    return asString(result.get(0).get("handle"));
                }
            }
            log.warn("Bearer user-info fallback got non-OK body: {}", body);
        } catch (Exception e) {
            log.warn("Bearer user-info fallback failed: {}", e.getMessage());
        }
        return null;
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Integer asInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (NumberFormatException e) { return null; }
    }

    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (NumberFormatException e) { return null; }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return (b != null && !b.isBlank()) ? b : null;
    }
}
