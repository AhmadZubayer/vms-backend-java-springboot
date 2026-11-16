package com.example.stark_vms_server.service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Remembers the identifiers of access tokens that were signed out before they expired.
 *
 * <p>Access tokens are self-contained, so signing out cannot delete a token that a client
 * already holds. Each entry is kept only until the token would have expired on its own, and
 * the list lives in memory, so restarting the server accepts previously signed-out tokens
 * again for the remainder of their short lifetime.
 */
@Component
public class TokenDenylist {

    private final Map<String, Instant> revokedUntil = new ConcurrentHashMap<>();

    public void revoke(String tokenId, Instant expiresAt) {
        if (tokenId == null || tokenId.isBlank() || expiresAt == null) {
            return;
        }

        purgeExpired();
        revokedUntil.put(tokenId, expiresAt);
    }

    public boolean isRevoked(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return false;
        }

        Instant expiresAt = revokedUntil.get(tokenId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(Instant.now())) {
            revokedUntil.remove(tokenId);
            return false;
        }

        return true;
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        revokedUntil.values().removeIf(expiresAt -> expiresAt.isBefore(now));
    }
}
