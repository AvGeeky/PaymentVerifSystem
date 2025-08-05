package com.events.paymentverifsystem.Utilities.Email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simple Gmail OAuth2 token provider using a refresh token.
 * Configure gmail.clientId, gmail.clientSecret, gmail.refreshToken in application.yml/env.
 *
 * This implementation caches the access token until expiry and refreshes when needed.
 */
@Component
@ConfigurationProperties(prefix = "gmail")
public class GmailOauth2TokenProvider implements TokenProvider {
    private static final Logger log = LoggerFactory.getLogger(GmailOauth2TokenProvider.class);

    private String clientId;
    private String clientSecret;
    private String refreshToken;

    // cached token and expiry
    private volatile String cachedAccessToken;
    private volatile Instant accessTokenExpiry = Instant.EPOCH;
    private final ReentrantLock lock = new ReentrantLock();

    private final RestTemplate rest = new RestTemplate();

    @Override
    public String getAccessToken() {
        // fast-check
        if (cachedAccessToken != null && Instant.now().isBefore(accessTokenExpiry.minusSeconds(30))) {
            return cachedAccessToken;
        }

        // lock and refresh
        lock.lock();
        try {
            if (cachedAccessToken != null && Instant.now().isBefore(accessTokenExpiry.minusSeconds(30))) {
                return cachedAccessToken;
            }
            if (clientId == null || clientSecret == null || refreshToken == null) {
                log.warn("Gmail OAuth2 credentials are not configured");
                return null;
            }

            String tokenEndpoint = "https://oauth2.googleapis.com/token";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("refresh_token", refreshToken);
            body.add("grant_type", "refresh_token");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> resp = rest.exchange(tokenEndpoint, HttpMethod.POST, request, Map.class);
            if (resp.getStatusCode() != HttpStatus.OK) {
                log.error("Failed to refresh Gmail access token: status={}", resp.getStatusCode());
                return null;
            }

            Map<String, Object> map = resp.getBody();
            if (map == null || !map.containsKey("access_token")) {
                log.error("Token endpoint returned no access_token");
                return null;
            }
            String accessToken = (String) map.get("access_token");
            Integer expiresIn = map.containsKey("expires_in") ? ((Number) map.get("expires_in")).intValue() : 3600;

            cachedAccessToken = accessToken;
            accessTokenExpiry = Instant.now().plusSeconds(expiresIn);

            log.info("Obtained new Gmail access token, expires in {}s", expiresIn);
            return cachedAccessToken;
        } catch (Exception ex) {
            log.error("Error fetching Gmail access token", ex);
            return null;
        } finally {
            lock.unlock();
        }
    }

    // setters for ConfigurationProperties
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
