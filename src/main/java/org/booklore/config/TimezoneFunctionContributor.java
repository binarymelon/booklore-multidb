package org.booklore.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.community.dialect.SQLiteDialect;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.type.BasicType;

/**
 * Registers database-agnostic HQL functions for timezone-aware date/time operations.
 * <p>
 * These functions allow JPQL queries to use timezone conversion without native SQL:
 * <ul>
 *   <li>{@code tz_convert(timestamp, offset)} — shift a UTC timestamp by an offset (e.g. '+05:30')</li>
 *   <li>{@code tz_date(timestamp, offset)} — extract a DATE in the target timezone</li>
 *   <li>{@code tz_day_of_week(timestamp, offset)} — day of week, 1=Sunday..7=Saturday</li>
 * </ul>
 */
public class TimezoneFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        var registry = functionContributions.getFunctionRegistry();
        var typeConfig = functionContributions.getTypeConfiguration();

        Dialect dialect = functionContributions.getServiceRegistry()
                .requireService(JdbcEnvironment.class)
                .getDialect();

        BasicType<?> timestampType = typeConfig.getBasicTypeForJavaType(java.time.Instant.class);
        BasicType<?> dateType = typeConfig.getBasicTypeForJavaType(java.time.LocalDate.class);
        BasicType<?> intType = typeConfig.getBasicTypeForJavaType(Integer.class);

        String tzConvertPattern;
        String tzDatePattern;
        String tzDayOfWeekPattern;

        if (dialect instanceof MySQLDialect) {
            // Covers both MySQL and MariaDB (MariaDBDialect extends MySQLDialect)
            tzConvertPattern = "CONVERT_TZ(?1, '+00:00', ?2)";
            tzDatePattern = "DATE(CONVERT_TZ(?1, '+00:00', ?2))";
            tzDayOfWeekPattern = "DAYOFWEEK(CONVERT_TZ(?1, '+00:00', ?2))";

        } else if (dialect instanceof PostgreSQLDialect) {
            tzConvertPattern = "(?1 + CAST(?2 AS INTERVAL))";
            tzDatePattern = "CAST((?1 + CAST(?2 AS INTERVAL)) AS DATE)";
            tzDayOfWeekPattern = "(EXTRACT(DOW FROM (?1 + CAST(?2 AS INTERVAL)))::int + 1)";

        } else if (dialect instanceof SQLiteDialect) {
            tzConvertPattern = "datetime(?1, ?2)";
            tzDatePattern = "date(?1, ?2)";
            tzDayOfWeekPattern = "(CAST(strftime('%w', ?1, ?2) AS INTEGER) + 1)";

        } else if (dialect instanceof H2Dialect) {
            // H2 fallback: ignore timezone offset (sufficient for unit tests)
            tzConvertPattern = "?1";
            tzDatePattern = "CAST(?1 AS DATE)";
            tzDayOfWeekPattern = "DAYOFWEEK(?1)";

        } else {
            throw new UnsupportedOperationException(
                    "Unsupported database dialect for timezone functions: " + dialect.getClass().getName());
        }

        registry.patternDescriptorBuilder("tz_convert", tzConvertPattern)
                .setInvariantType(timestampType)
                .setExactArgumentCount(2)
                .register();

        registry.patternDescriptorBuilder("tz_date", tzDatePattern)
                .setInvariantType(dateType)
                .setExactArgumentCount(2)
                .register();

        registry.patternDescriptorBuilder("tz_day_of_week", tzDayOfWeekPattern)
                .setInvariantType(intType)
                .setExactArgumentCount(2)
                .register();
    }
}
