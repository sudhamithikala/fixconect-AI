package com.fixconnect.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

/**
 * Small in-place schema fixes that {@code ddl-auto=update} cannot do on its own.
 * <p>
 * Hibernate creates enum columns as a native ENUM type (H2, MySQL) or with a CHECK constraint, listing the values that
 * existed at creation time. Databases created before the ADMIN role was added would reject role = 'ADMIN',
 * so the old restriction on {@code users.role} is relaxed here. Safe to run on every start.
 */
@Component
@Order(1)
public class DatabaseMigrations implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrations.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public DatabaseMigrations(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(String... args) {
        try (Connection c = dataSource.getConnection()) {
            String product = c.getMetaData().getDatabaseProductName().toLowerCase();
            if (product.contains("h2")) {
                fixH2RoleConstraint();
            } else if (product.contains("mysql") || product.contains("mariadb")) {
                fixMySqlRoleColumn();
            }
        } catch (Exception e) {
            log.warn("Schema check skipped: {}", e.getMessage());
        }
    }

    private void fixH2RoleConstraint() {
        // Hibernate 6 on H2 creates enum fields as a native ENUM('CUSTOMER','PROVIDER') column
        List<String> types = jdbc.queryForList(
                "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'USERS' AND COLUMN_NAME = 'ROLE'",
                String.class);
        if (!types.isEmpty() && types.get(0) != null && types.get(0).toUpperCase().startsWith("ENUM")) {
            jdbc.execute("ALTER TABLE USERS ALTER COLUMN ROLE SET DATA TYPE VARCHAR(20)");
            log.info("Converted USERS.ROLE from ENUM to VARCHAR(20) to allow the ADMIN role");
        }

        // ...or as VARCHAR with a CHECK constraint, depending on the version that created the table
        List<String> names = jdbc.queryForList(
                "SELECT tc.CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc "
                        + "JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc "
                        + "ON tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME AND tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA "
                        + "WHERE tc.TABLE_NAME = 'USERS' AND tc.CONSTRAINT_TYPE = 'CHECK' "
                        + "AND cc.CHECK_CLAUSE LIKE '%PROVIDER%' AND cc.CHECK_CLAUSE NOT LIKE '%ADMIN%'",
                String.class);
        for (String name : names) {
            jdbc.execute("ALTER TABLE USERS DROP CONSTRAINT \"" + name + "\"");
            log.info("Relaxed old role constraint {} on USERS to allow the ADMIN role", name);
        }
    }

    private void fixMySqlRoleColumn() {
        List<String> types = jdbc.queryForList(
                "SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'users' AND COLUMN_NAME = 'role'", String.class);
        if (!types.isEmpty() && types.get(0).toLowerCase().startsWith("enum") && !types.get(0).contains("ADMIN")) {
            jdbc.execute("ALTER TABLE users MODIFY role VARCHAR(20) NOT NULL");
            log.info("Converted users.role to VARCHAR(20) to allow the ADMIN role");
        }
    }
}
