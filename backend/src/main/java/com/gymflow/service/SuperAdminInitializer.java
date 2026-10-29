package com.gymflow.service;

import com.gymflow.domain.Role;
import com.gymflow.domain.UserAccount;
import com.gymflow.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SuperAdminInitializer {
    private static final Logger log = LoggerFactory.getLogger(SuperAdminInitializer.class);

    @Value("${gym.platform.admin-email:admin@rememberflow.com}")
    private String adminEmail;

    @Value("${gym.platform.admin-password:AdminPassword123!}")
    private String adminPassword;

    @Bean
    CommandLineRunner initSuperAdmin(UserRepository users, PasswordEncoder encoder,
            org.springframework.core.env.Environment env) {
        return args -> {
            String email = adminEmail.trim().toLowerCase();
            boolean allowDefault = Boolean
                    .parseBoolean(env.getProperty("gym.platform.allow-default-admin-password", "false"));
            if ("AdminPassword123!".equals(adminPassword) && !allowDefault) {
                if (!env.acceptsProfiles(org.springframework.core.env.Profiles.of("dev", "test"))) {
                    throw new IllegalStateException(
                            "FATAL: Default Super-Admin password detected in non-dev environment. "
                            + "Please configure GYM_PLATFORM_ADMIN_PASSWORD in your .env file "
                            + "(or set GYM_PLATFORM_ALLOW_DEFAULT_ADMIN_PASSWORD=true to bypass).");
                }
                log.warn("SECURITY WARNING: Using default Super-Admin password. "
                        + "Configure GYM_PLATFORM_ADMIN_PASSWORD for production environments.");
            } else if ("AdminPassword123!".equals(adminPassword) && allowDefault) {
                // H-1 FIX: GYM_PLATFORM_ALLOW_DEFAULT_ADMIN_PASSWORD=true was explicitly set.
                // Log at ERROR level so this is never silently active in production.
                // The original code logged nothing in this branch, making the bypass invisible.
                log.error("[SECURITY BYPASS ACTIVE] gym.platform.allow-default-admin-password=true is set "
                        + "and the Super-Admin account is protected only by the default password 'AdminPassword123!'. "
                        + "This is a CRITICAL security risk in any internet-facing deployment. "
                        + "Set GYM_PLATFORM_ADMIN_PASSWORD to a strong unique secret immediately.");
            }
            if (!users.existsByEmailIgnoreCase(email)) {
                users.save(new UserAccount("Platform Super-Admin", email, null,
                        encoder.encode(adminPassword), Role.SUPER_ADMIN, null, null));
                log.info("Initialized platform Super-Admin account: {}", email);
            }
        };
    }
}
