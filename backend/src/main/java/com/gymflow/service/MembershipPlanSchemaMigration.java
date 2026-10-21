package com.gymflow.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class MembershipPlanSchemaMigration {
    @Bean
    ApplicationRunner allowDayBasedPlans(DataSource dataSource) {
        return args -> {
            try (Connection connection = dataSource.getConnection()) {
                if (!connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL")) {
                    return;
                }

                long organizationId = ensureOrganization(connection);
                long mainBranchId = ensureMainBranch(connection, organizationId);

                boolean durationMonthsIsNotNullable = false;
                try (ResultSet columns = connection.getMetaData().getColumns(
                        connection.getCatalog(), null, "membership_plans", "duration_months")) {
                    while (columns.next()) {
                        durationMonthsIsNotNullable = columns
                                .getInt("NULLABLE") == java.sql.DatabaseMetaData.columnNoNulls;
                    }
                }

                try (Statement statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE memberships MODIFY COLUMN status VARCHAR(32) NOT NULL");
                } catch (Exception ignored) {
                }

                if (durationMonthsIsNotNullable) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("ALTER TABLE membership_plans MODIFY COLUMN duration_months INT NULL");
                    }
                }

                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("UPDATE memberships next_membership "
                            + "JOIN memberships previous_membership "
                            + "ON next_membership.member_id = previous_membership.member_id "
                            + "AND next_membership.start_date = DATE_ADD(previous_membership.end_date, INTERVAL 1 DAY) "
                            + "AND next_membership.created_at > previous_membership.created_at "
                            + "LEFT JOIN memberships existing_renewal "
                            + "ON existing_renewal.renewed_from_id = previous_membership.id "
                            + "SET previous_membership.renewal_start_date = next_membership.start_date, "
                            + "next_membership.renewed_from_id = previous_membership.id "
                            + "WHERE previous_membership.renewal_start_date IS NULL "
                            + "AND next_membership.renewed_from_id IS NULL AND existing_renewal.id IS NULL");
                }

                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("UPDATE memberships parent_membership "
                            + "LEFT JOIN memberships scheduled_membership "
                            + "ON scheduled_membership.renewed_from_id = parent_membership.id "
                            + "AND scheduled_membership.start_date = parent_membership.renewal_start_date "
                            + "AND scheduled_membership.status = 'ACTIVE' "
                            + "SET parent_membership.renewal_start_date = NULL "
                            + "WHERE parent_membership.renewal_start_date IS NOT NULL "
                            + "AND scheduled_membership.id IS NULL");
                }

                dropUniqueRenewalParentIndex(connection);

                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("UPDATE memberships m JOIN membership_plans p ON p.id = m.plan_id "
                            + "SET m.plan_name_snapshot = p.name, "
                            + "m.plan_description_snapshot = p.description, "
                            + "m.plan_duration_months_snapshot = p.duration_months, "
                            + "m.plan_duration_days_snapshot = p.duration_days "
                            + "WHERE m.plan_name_snapshot IS NULL");
                }

                try (PreparedStatement users = connection.prepareStatement(
                        "UPDATE user_accounts SET organization_id = ? WHERE organization_id IS NULL");
                        PreparedStatement branchUsers = connection.prepareStatement(
                                "UPDATE user_accounts SET branch_id = ? WHERE branch_id IS NULL AND role <> 'OWNER'");
                        PreparedStatement plans = connection.prepareStatement(
                                "UPDATE membership_plans SET organization_id = ? WHERE organization_id IS NULL");
                        PreparedStatement memberships = connection.prepareStatement(
                                "UPDATE memberships SET branch_id = ? WHERE branch_id IS NULL")) {
                    users.setLong(1, organizationId);
                    users.executeUpdate();
                    branchUsers.setLong(1, mainBranchId);
                    branchUsers.executeUpdate();
                    plans.setLong(1, organizationId);
                    plans.executeUpdate();
                    memberships.setLong(1, mainBranchId);
                    memberships.executeUpdate();
                }
            }
        };
    }

    private void dropUniqueRenewalParentIndex(Connection connection) throws Exception {
        Map<String, Set<String>> uniqueIndexes = new HashMap<>();
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                connection.getCatalog(), null, "memberships", true, false)) {
            while (indexes.next()) {
                String indexName = indexes.getString("INDEX_NAME");
                String columnName = indexes.getString("COLUMN_NAME");
                if (indexName != null && columnName != null) {
                    uniqueIndexes.computeIfAbsent(indexName, ignored -> new HashSet<>()).add(columnName);
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            for (Map.Entry<String, Set<String>> index : uniqueIndexes.entrySet()) {
                if (index.getValue().size() == 1
                        && index.getValue().stream().anyMatch("renewed_from_id"::equalsIgnoreCase)) {
                    String escapedName = index.getKey().replace("`", "``");
                    statement.execute("ALTER TABLE memberships DROP INDEX `" + escapedName + "`");
                }
            }
        }
    }

    private long ensureOrganization(Connection connection) throws Exception {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id FROM gym_organizations WHERE code = ?")) {
            select.setString(1, "FORM");
            try (ResultSet rows = select.executeQuery()) {
                if (rows.next())
                    return rows.getLong(1);
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO gym_organizations (name, code, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, "FORM / Club");
            insert.setString(2, "FORM");
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (keys.next())
                    return keys.getLong(1);
            }
        }
        throw new IllegalStateException("Unable to create default gym organization");
    }

    private long ensureMainBranch(Connection connection, long organizationId) throws Exception {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id FROM gym_branches WHERE organization_id = ? AND code = ?")) {
            select.setLong(1, organizationId);
            select.setString(2, "MAIN");
            try (ResultSet rows = select.executeQuery()) {
                if (rows.next())
                    return rows.getLong(1);
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO gym_branches (organization_id, name, code, type, active, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setLong(1, organizationId);
            insert.setString(2, "Main branch");
            insert.setString(3, "MAIN");
            insert.setString(4, "COMPANY_OWNED");
            insert.setBoolean(5, true);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (keys.next())
                    return keys.getLong(1);
            }
        }
        throw new IllegalStateException("Unable to create default gym branch");
    }
}