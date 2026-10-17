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

    @Value("${gym.platform.admin-email:admin@gymflow.io}")
    private String adminEmail;

    @Value("${gym.platform.admin-password:AdminPassword123!}")
    private String adminPassword;

    @Bean
    CommandLineRunner initSuperAdmin(UserRepository users, PasswordEncoder encoder) {
        return args -> {
            String email = adminEmail.trim().toLowerCase();
            if (!users.existsByEmailIgnoreCase(email)) {
                users.save(new UserAccount("Platform Super-Admin", email, null,
                        encoder.encode(adminPassword), Role.SUPER_ADMIN, null, null));
                log.info("Initialized platform Super-Admin account: {}", email);
            }
        };
    }
}

