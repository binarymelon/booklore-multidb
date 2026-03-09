# Booklore Multi-DB Wrapper

## Overview

This project wraps the upstream [Booklore](https://github.com/booklore-app/booklore) application to add MariaDB, PostgreSQL, and SQLite support. The upstream only supports MariaDB. We use a **Gradle source overlay** pattern: upstream sources are pulled via a git submodule and filtered at build time, with our replacement files taking precedence.

## Architecture

### Source Overlay (build.gradle)

- **Git submodule**: `upstream/` → `https://github.com/booklore-app/booklore.git`
- **Sync tasks** copy upstream sources to `build/generated/` with exclusions for files we replace
- Both overlay (`src/`) and filtered upstream (`build/generated/`) directories are compiled together
- `setupTestFixtures` copies binary test resources (`cbx/`) that use filesystem paths (not classpath)

### Overlay Files (what we provide)

| File | Purpose |
|------|---------|
| `src/main/java/.../config/DatabaseMigrationConfig.java` | Liquibase primary + legacy Flyway catch-up migration |
| `src/main/java/.../config/DataSourceConfig.java` | SQLite JDBC driver configuration (epoch millis timestamps) |
| `src/main/java/.../config/TimezoneFunctionContributor.java` | Hibernate FunctionContributor for cross-DB timezone HQL functions |
| `src/main/java/.../repository/ReadingSessionRepository.java` | Native queries rewritten as JPQL using custom HQL functions |
| `src/main/resources/application.yaml` | Multi-DB datasource config, defaults to SQLite |
| `src/main/resources/db/changelog/` | Liquibase changelogs (replacing upstream Flyway migrations) |
| `src/main/resources/META-INF/services/org.hibernate.boot.model.FunctionContributor` | SPI registration |
| `src/test/resources/application-test.yml` | H2 in-memory test config with Liquibase disabled |

### Key Design Decisions

1. **Hibernate FunctionContributor SPI** registers `tz_convert`, `tz_date`, `tz_day_of_week` as custom HQL functions with dialect-specific SQL patterns (MariaDB/PostgreSQL/SQLite/H2). This avoids native queries entirely.

2. **Liquibase instead of Flyway** for database-agnostic DDL. The `DatabaseMigrationConfig` detects existing Flyway installations and runs catch-up migrations before handing off to Liquibase.

3. **SQLite compatibility constraints**:
   - `addUniqueConstraint` → `createIndex` with `unique: true` (SQLite lacks `ALTER TABLE ADD CONSTRAINT`)
   - `addPrimaryKey` → inline `primaryKey: true` on columns in `createTable`
   - Auto-increment PKs use `${id.type}` property substitution (`integer` for SQLite, `bigint` elsewhere)
   - `DataSourceConfig` appends `date_class=INTEGER&date_precision=MILLISECONDS` to SQLite JDBC URLs (Hibernate writes timestamps as epoch millis)

4. **H2 for tests**: Liquibase is skipped for `jdbc:h2:mem:` URLs; Hibernate DDL manages test schemas. The `TimezoneFunctionContributor` has an H2 branch with identity/passthrough patterns.

## Build & Test

```bash
# Build (requires JDK 25)
./gradlew build

# Run tests (all 3379 upstream tests should pass)
./gradlew test

# Docker
docker build -t booklore-multidb .
```

## Per-Upstream-Release Maintenance

When upstream releases a new version:

1. Update submodule: `cd upstream && git fetch && git checkout <tag> && cd .. && git add upstream`
2. Port new Flyway migrations to Liquibase changelogs in `src/main/resources/db/changelog/changelogs/`
3. Check for new native queries: `grep -r "nativeQuery = true" upstream/booklore-api/src/`
4. Diff upstream `build.gradle` for dependency changes
5. Build and run full test suite

## Database Configuration

Set `DATABASE_URL` environment variable:

| Database   | URL Format |
|------------|-----------|
| SQLite (default) | `jdbc:sqlite:/path/to/booklore.db` |
| MariaDB    | `jdbc:mariadb://host:3306/booklore` |
| PostgreSQL | `jdbc:postgresql://host:5432/booklore` |

For MariaDB/PostgreSQL, also set `DATABASE_USERNAME` and `DATABASE_PASSWORD`.

## Known SQLite Limitations

- Generated/virtual columns require SQLite 3.31+ (2020-01-22)
- No `ALTER TABLE ADD CONSTRAINT` — all constraints must be inline or use indexes
- `AUTOINCREMENT` requires `INTEGER PRIMARY KEY` (not `BIGINT`)
- Timestamps stored as epoch milliseconds (configured via JDBC URL parameters)
- Single-writer concurrency model — use `maximum-pool-size=1` for write-heavy workloads

## Stack

- Java 25, Spring Boot 4.0.3, Hibernate 7.2.6, Gradle 9.4.0
- hibernate-community-dialects (SQLiteDialect)
- Liquibase (schema management), Flyway (legacy catch-up only)
- SQLite JDBC 3.49.1.0, PostgreSQL driver, MariaDB driver
