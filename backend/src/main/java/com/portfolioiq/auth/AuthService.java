package com.portfolioiq.auth;

import com.portfolioiq.audit.AuditService;
import com.portfolioiq.auth.AuthDtos.*;
import com.portfolioiq.common.ApiException;
import com.portfolioiq.security.JwtService;
import com.portfolioiq.user.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
public class AuthService {
    private static final Duration VERIFY_TTL = Duration.ofHours(24);
    private static final Duration RESET_TTL = Duration.ofHours(1);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final TokenService tokens;
    private final EmailService email;
    private final AuditService audit;
    private final String frontendUrl;
    private final boolean requireVerifiedEmail;

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder encoder,
                       AuthenticationManager authManager, JwtService jwt, TokenService tokens,
                       EmailService email, AuditService audit,
                       @Value("${app.frontend-url}") String frontendUrl,
                       @Value("${app.auth.require-verified-email:false}") boolean requireVerifiedEmail) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.authManager = authManager;
        this.jwt = jwt;
        this.tokens = tokens;
        this.email = email;
        this.audit = audit;
        this.frontendUrl = frontendUrl;
        this.requireVerifiedEmail = requireVerifiedEmail;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String address = req.email().trim();
        if (users.existsByEmailIgnoreCase(address)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
        User user = new User(address, encoder.encode(req.password()), req.firstName().trim(), req.lastName().trim());
        // Self-registration always yields INVESTOR. Elevated roles are assigned by administrators only.
        user.getRoles().add(roles.findByName(RoleName.INVESTOR).orElseThrow());
        users.save(user);
        audit.log(user.getId(), "USER_REGISTERED", "USER", String.valueOf(user.getId()), null);
        sendVerificationEmail(user);
        return token(user);
    }

    public AuthResponse login(LoginRequest req) {
        String address = req.email().trim();
        try {
            authManager.authenticate(new UsernamePasswordAuthenticationToken(address, req.password()));
        } catch (AuthenticationException e) {
            audit.log(users.findIdByEmail(address).orElse(null), "LOGIN_FAILED", "USER", null, null);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"); // same message for all failures
        }
        User user = users.findByEmailIgnoreCase(address).orElseThrow();
        if (requireVerifiedEmail && !user.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Please verify your email address before signing in");
        }
        audit.log(user.getId(), "LOGIN_SUCCESS", "USER", String.valueOf(user.getId()), null);
        return token(user);
    }

    @Transactional(readOnly = true)
    public MeResponse me(String emailAddress) {
        User u = users.findByEmailIgnoreCase(emailAddress).orElseThrow();
        return new MeResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                u.isEmailVerified(), roleNames(u));
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        Long userId = tokens.consume(rawToken, TokenType.EMAIL_VERIFICATION);
        User user = users.findById(userId).orElseThrow();
        user.markEmailVerified();
        users.save(user);
        audit.log(userId, "EMAIL_VERIFIED", "USER", String.valueOf(userId), null);
    }

    @Transactional
    public void resendVerification(String emailAddress) {
        User user = users.findByEmailIgnoreCase(emailAddress).orElseThrow();
        if (user.isEmailVerified()) throw new ApiException(HttpStatus.CONFLICT, "Email is already verified");
        sendVerificationEmail(user);
    }

    /** Always completes silently so the response never reveals whether an account exists. */
    @Transactional
    public void forgotPassword(String emailAddress) {
        users.findByEmailIgnoreCase(emailAddress.trim()).filter(User::isEnabled).ifPresent(user -> {
            String raw = tokens.issue(user.getId(), TokenType.PASSWORD_RESET, RESET_TTL);
            email.send(user.getEmail(), "Reset your PortfolioIQ password",
                    "We received a request to reset your password.\n\nReset link (valid for 1 hour):\n"
                            + frontendUrl + "/reset-password?token=" + raw
                            + "\n\nIf you did not request this, you can ignore this email.");
            audit.log(user.getId(), "PASSWORD_RESET_REQUESTED", "USER", String.valueOf(user.getId()), null);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        Long userId = tokens.consume(req.token(), TokenType.PASSWORD_RESET);
        User user = users.findById(userId).orElseThrow();
        user.changePasswordHash(encoder.encode(req.newPassword()));
        users.save(user);
        tokens.invalidateAll(userId, TokenType.PASSWORD_RESET);
        audit.log(userId, "PASSWORD_RESET", "USER", String.valueOf(userId), null);
    }

    private void sendVerificationEmail(User user) {
        String raw = tokens.issue(user.getId(), TokenType.EMAIL_VERIFICATION, VERIFY_TTL);
        email.send(user.getEmail(), "Verify your PortfolioIQ email",
                "Welcome to PortfolioIQ, " + user.getFirstName() + "!\n\nConfirm your email address (valid for 24 hours):\n"
                        + frontendUrl + "/verify-email?token=" + raw);
    }

    private AuthResponse token(User u) {
        List<String> names = roleNames(u);
        return new AuthResponse(jwt.generate(u.getEmail(), names), "Bearer", jwt.ttlSeconds(), u.getEmail(), names);
    }

    private List<String> roleNames(User u) {
        return u.getRoles().stream().map(r -> r.getName().name()).sorted().toList();
    }
}
