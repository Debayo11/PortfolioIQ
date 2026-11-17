package com.portfolioiq.auth;

import com.portfolioiq.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * One-time tokens for email verification and password reset. The raw token is shown once (emailed);
 * only its SHA-256 hash is stored, so a database leak cannot be used to take over accounts.
 */
@Service
public class TokenService {
    private static final SecureRandom RNG = new SecureRandom();
    private final UserTokenRepository repo;
    private final Clock clock;

    public TokenService(UserTokenRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    /** Issues a new token and invalidates any earlier unused token of the same type for this user. */
    @Transactional
    public String issue(Long userId, TokenType type, Duration ttl) {
        Instant now = clock.instant();
        repo.invalidateOpen(userId, type, now);
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repo.save(new UserToken(userId, sha256(raw), type, now.plus(ttl)));
        return raw;
    }

    /** Validates and burns the token, returning the owning user id. */
    @Transactional
    public Long consume(String raw, TokenType type) {
        UserToken t = repo.findByTokenHashAndType(sha256(raw), type).orElseThrow(TokenService::invalid);
        Instant now = clock.instant();
        if (t.getUsedAt() != null || t.getExpiresAt().isBefore(now)) throw invalid();
        t.markUsed(now);
        return t.getUserId();
    }

    @Transactional
    public void invalidateAll(Long userId, TokenType type) {
        repo.invalidateOpen(userId, type, clock.instant());
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
