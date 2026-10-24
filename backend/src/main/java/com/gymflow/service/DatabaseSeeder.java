package com.gymflow.service;

import com.gymflow.domain.MembershipPlan;
import com.gymflow.domain.BranchType;
import com.gymflow.domain.GymBranch;
import com.gymflow.domain.GymOrganization;
import com.gymflow.domain.Role;
import com.gymflow.domain.UserAccount;
import com.gymflow.repository.PlanRepository;
import com.gymflow.repository.BranchRepository;
import com.gymflow.repository.OrganizationRepository;
import com.gymflow.repository.UserRepository;
import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@ConditionalOnProperty(name = "gym.demo-data.enabled", havingValue = "true")
public class DatabaseSeeder {
    @Bean
    CommandLineRunner seedDevelopmentData(UserRepository users, PlanRepository plans, PasswordEncoder encoder,
            OrganizationRepository organizations, BranchRepository branches,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute("ALTER TABLE user_accounts MODIFY COLUMN role VARCHAR(32) NOT NULL");
            } catch (Exception ignored) {
                // Table might not exist yet or running against in-memory H2 in unit tests
            }
            try {
                jdbcTemplate.execute("ALTER TABLE user_accounts ADD COLUMN member_pin VARCHAR(8) NULL");
            } catch (Exception ignored) {
            }
            try {
                jdbcTemplate.execute("ALTER TABLE pt_sessions ADD COLUMN verification_method VARCHAR(32) NULL");
            } catch (Exception ignored) {
            }
            GymOrganization organization = organizations.findByCodeIgnoreCase("FORM")
                    .orElseGet(() -> organizations.save(new GymOrganization("FORM / Club", "FORM", com.gymflow.domain.SubscriptionTier.PREMIUM)));
            if (organization.getSubscriptionTier() != com.gymflow.domain.SubscriptionTier.PREMIUM) {
                organization.upgradeTier(com.gymflow.domain.SubscriptionTier.PREMIUM);
                organizations.save(organization);
            }
            GymBranch mainBranch = branches.findByOrganizationIdAndCodeIgnoreCase(organization.getId(), "MAIN")
                    .orElseGet(() -> branches.save(new GymBranch(organization, "Main branch", "MAIN",
                            BranchType.COMPANY_OWNED, null, null)));
            if (!users.existsByEmailIgnoreCase("owner@gym.local")) {
                users.save(new UserAccount("Gym Owner", "owner@gym.local", null,
                        encoder.encode("ChangeMe123!"), Role.OWNER, organization, null));
            }
            if (!users.existsByEmailIgnoreCase("admin@rememberflow.com")) {
                users.save(new UserAccount("Platform Super-Admin", "admin@rememberflow.com", null,
                        encoder.encode("AdminPassword123!"), Role.SUPER_ADMIN, null, null));
            }
            if (plans.count() == 0) {
                plans.save(plan(organization, "Essential", "Open gym access for one month.", "49.00", 1));
                plans.save(plan(organization, "Strength", "Full access with trainer-led strength sessions.",
                        "129.00", 3));
                plans.save(plan(organization, "All access", "Year-round access and recovery room use.",
                        "399.00", 12));
            }
            users.findByEmailIgnoreCase("owner@gym.local").ifPresent(owner -> owner.assignLocation(organization, null));
            plans.findAll().stream().filter(plan -> plan.getOrganization() == null)
                    .forEach(plan -> plan.assignLocation(organization, null));
            branches.findById(mainBranch.getId()).orElseThrow();
        };
    }

    private MembershipPlan plan(GymOrganization organization, String name, String description, String price,
            int durationMonths) {
        MembershipPlan plan = new MembershipPlan(name, description, new BigDecimal(price), durationMonths);
        plan.assignLocation(organization, null);
        return plan;
    }
}