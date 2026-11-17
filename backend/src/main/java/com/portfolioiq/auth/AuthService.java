package com.portfolioiq.auth;

import com.portfolioiq.auth.AuthDtos.*;
import com.portfolioiq.common.ApiException;
import com.portfolioiq.security.JwtService;
import com.portfolioiq.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final JwtService jwt;

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder encoder,
                       AuthenticationManager authManager, JwtService jwt) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.authManager = authManager;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
        User user = new User(email, encoder.encode(req.password()), req.firstName().trim(), req.lastName().trim());
        // Self-registration always yields INVESTOR. Elevated roles are assigned by administrators only.
        user.getRoles().add(roles.findByName(RoleName.INVESTOR).orElseThrow());
        users.save(user);
        return token(user);
    }

    public AuthResponse login(LoginRequest req) {
        try {
            authManager.authenticate(new UsernamePasswordAuthenticationToken(req.email().trim(), req.password()));
        } catch (AuthenticationException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"); // same message for all failures
        }
        return token(users.findByEmailIgnoreCase(req.email().trim()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public MeResponse me(String email) {
        User u = users.findByEmailIgnoreCase(email).orElseThrow();
        return new MeResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                u.isEmailVerified(), roleNames(u));
    }

    private AuthResponse token(User u) {
        List<String> names = roleNames(u);
        return new AuthResponse(jwt.generate(u.getEmail(), names), "Bearer", jwt.ttlSeconds(), u.getEmail(), names);
    }

    private List<String> roleNames(User u) {
        return u.getRoles().stream().map(r -> r.getName().name()).sorted().toList();
    }
}
