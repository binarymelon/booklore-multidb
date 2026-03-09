package org.booklore.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnExpression("'${spring.datasource.url:}'.startsWith('jdbc:sqlite:')")
public class SQLiteDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties) {
        HikariDataSource ds = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();

        // Enable WAL journal mode and busy_timeout so concurrent writers retry on
        // lock contention instead of immediately returning SQLITE_BUSY.
        //
        // IMPORTANT: do NOT set maxPoolSize=1 here — library scans use
        // @Transactional(REQUIRES_NEW) which suspends the outer connection and
        // opens a second one; pool size must be >= 2 or every book insert deadlocks.
        String url = ds.getJdbcUrl();
        String separator = url.contains("?") ? "&" : "?";
        ds.setJdbcUrl(url + separator + "journal_mode=WAL&busy_timeout=30000");

        return ds;
    }
}
