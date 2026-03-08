package org.booklore.config;

import liquibase.integration.spring.SpringLiquibase;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

@Configuration
@ConditionalOnProperty(prefix = "spring.liquibase", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(LiquibaseProperties.class)
public class DatabaseMigrationConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationConfig.class);

    /**
     * Custom SpringLiquibase bean that first runs legacy Flyway catch-up
     * if the database has a flyway_schema_history table (existing installation).
     * Overrides Spring Boot's auto-configured Liquibase bean.
     */
    @Bean
    public SpringLiquibase liquibase(DataSource dataSource, LiquibaseProperties properties) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(properties.getChangeLog());

        // Skip Liquibase for in-memory databases (H2 in tests — Hibernate DDL manages the schema)
        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();
            if (url != null && url.startsWith("jdbc:h2:mem:")) {
                log.info("In-memory database detected — skipping Liquibase migrations");
                liquibase.setShouldRun(false);
                return liquibase;
            }
        } catch (SQLException ignored) {}

        runLegacyFlywayIfNeeded(dataSource);
        return liquibase;
    }

    private void runLegacyFlywayIfNeeded(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            if (tableExists(conn, "flyway_schema_history")) {
                log.info("Legacy Flyway schema history detected — running catch-up migrations");
                Flyway flyway = Flyway.configure()
                        .dataSource(dataSource)
                        .locations("classpath:db/migration")
                        .load();
                try {
                    flyway.migrate();
                } catch (FlywayValidateException e) {
                    flyway.repair();
                    flyway.migrate();
                }
                log.info("Legacy Flyway catch-up complete");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed legacy Flyway migration check", e);
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(
                conn.getCatalog(), null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}
