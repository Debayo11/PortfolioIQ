package com.portfolioiq.admin;

import com.portfolioiq.audit.AuditService;
import com.portfolioiq.user.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator. Self-registration only ever gives the Investor role, so the operator names one
 * email address in BOOTSTRAP_ADMIN_EMAIL. That account becomes administrator only once its email is verified (so
 * nobody can claim the address by registering it first) and only while the system has no enabled administrator.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final AuditService audit;
    private final String email;

    public AdminBootstrap(UserRepository users, RoleRepository roles, AuditService audit,
                          @Value("${app.bootstrap-admin-email:}") String email) {
        this.users = users;
        this.roles = roles;
        this.audit = audit;
        this.email = email == null ? "" : email.trim();
    }

    /** On startup, in case the account already exists and is verified. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!email.isEmpty()) users.findByEmailIgnoreCase(email).ifPresent(this::promoteIfEligible);
    }

    /** Called after an email is verified, and at startup. Returns true if the role was granted. */
    @Transactional
    public boolean promoteIfEligible(User user) {
        if (email.isEmpty() || !user.getEmail().equalsIgnoreCase(email) || !user.isEmailVerified() || !user.isEnabled()) {
            return false;
        }
        if (users.countEnabledWithRole(RoleName.ADMINISTRATOR) > 0) return false;
        user.getRoles().add(roles.findByName(RoleName.ADMINISTRATOR).orElseThrow());
        users.save(user);
        audit.log(user.getId(), "ADMIN_BOOTSTRAPPED", "USER", String.valueOf(user.getId()),
                "first administrator granted through BOOTSTRAP_ADMIN_EMAIL");
        log.warn("Administrator role granted to the configured bootstrap account");
        return true;
    }
}
