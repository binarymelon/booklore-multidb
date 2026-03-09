package org.booklore.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties) {
        HikariDataSource ds = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();

        String url = properties.getUrl();
        if (url != null && url.startsWith("jdbc:sqlite:")) {
            String separator = url.contains("?") ? "&" : "?";
            ds.setJdbcUrl(url + separator + "date_class=INTEGER&date_precision=MILLISECONDS");
        }

        return ds;
    }
}
