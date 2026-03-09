package org.booklore.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Injects SQLite-specific Hibernate properties early in startup, before the
 * application context is created, so they apply only when the datasource URL
 * points to a SQLite database.
 *
 * Specifically, sets hibernate.connection.handling_mode to
 * DELAYED_ACQUISITION_AND_RELEASE_AFTER_STATEMENT so that long-lived outer
 * @Transactional methods (e.g. library scans) do not hold a physical connection
 * during the book-processing loop. Without this, each concurrent scan holds one
 * connection for its entire duration; enough simultaneous scans exhaust the pool
 * and prevent the per-book REQUIRES_NEW sub-transactions from acquiring connections.
 *
 * Registered via META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.
 */
public class SQLiteEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.startsWith("jdbc:sqlite:")) {
            return;
        }

        environment.getPropertySources().addLast(new MapPropertySource(
                "sqlite-hibernate-config",
                Map.of("spring.jpa.properties.hibernate.connection.handling_mode",
                        "DELAYED_ACQUISITION_AND_RELEASE_AFTER_STATEMENT")));
    }
}
