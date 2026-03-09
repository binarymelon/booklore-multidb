# BookLore Multi-DB

An unofficial wrapper around [BookLore](https://github.com/booklore-app/booklore) that adds **SQLite** and **PostgreSQL** support. The upstream project supports MariaDB only. This image is a drop-in replacement that accepts the same environment variables and volumes, with the addition that the `DATABASE_URL` environment variable will accept JDBC urls for SQLite and PostgreSQL as well.

> All BookLore features, configuration options, and documentation apply unchanged.
> See the [official docs](https://booklore.org/docs/getting-started) for the full reference.

---

## Supported databases

| Database | `DATABASE_URL` format |
|---|---|
| SQLite | `jdbc:sqlite:/path/to/booklore.db` |
| PostgreSQL | `jdbc:postgresql://host:5432/booklore` |
| MariaDB | `jdbc:mariadb://host:3306/booklore` |

> [!WARNING]
> Please don't use this project with MariaDB.  Whlie it should work in theory, I likely won't test it and it's an unnecessary complication with no benefit.

---

## Quick start

### SQLite (simplest setup, no separate database required)

```bash
docker run -d \
  --name booklore \
  -p 6060:6060 \
  -v /path/to/data:/app/data \
  -v /path/to/books:/books \
  -v /path/to/bookdrop:/bookdrop \
  -e USER_ID=1000 \
  -e GROUP_ID=1000 \
  -e TZ=Etc/UTC \
  -e DATABASE_URL=jdbc:sqlite:/app/data/db/booklore.db \
  binarymelon/booklore-multidb:latest
```

The database file is created automatically on first run inside `/app/data/db/`, which is already mapped to your host via the `/app/data` volume.

### PostgreSQL

```yaml
# docker-compose.yml
services:
  booklore:
    image: binarymelon/booklore-multidb:latest
    container_name: booklore
    environment:
      - USER_ID=1000
      - GROUP_ID=1000
      - TZ=Etc/UTC
      - DATABASE_URL=jdbc:postgresql://postgres:5432/booklore
      - DATABASE_USERNAME=booklore
      - DATABASE_PASSWORD=your_secure_password
    depends_on:
      postgres:
        condition: service_healthy
    ports:
      - "6060:6060"
    volumes:
      - ./data:/app/data
      - ./books:/books
      - ./bookdrop:/bookdrop
    healthcheck:
      test: wget -q -O - http://localhost:6060/api/v1/healthcheck
      interval: 60s
      retries: 5
      start_period: 60s
      timeout: 10s
    restart: unless-stopped

  postgres:
    image: postgres:17
    container_name: postgres
    environment:
      - POSTGRES_DB=booklore
      - POSTGRES_USER=booklore
      - POSTGRES_PASSWORD=your_secure_password
    volumes:
      - ./pgdata:/var/lib/postgresql/data
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U booklore"]
      interval: 5s
      timeout: 5s
      retries: 10
```

```bash
docker compose up -d
```

---

## Configuration

All upstream environment variables are supported. Key ones:

| Variable | Default | Description |
|---|---|---|
| `DATABASE_URL` | — | JDBC URL selecting the database backend (required) |
| `DATABASE_USERNAME` | — | Database username (not needed for SQLite) |
| `DATABASE_PASSWORD` | — | Database password (not needed for SQLite) |
| `USER_ID` / `GROUP_ID` | `1000` | UID/GID the process runs as |
| `TZ` | `Etc/UTC` | Container timezone |
| `BOOKLORE_PORT` | `6060` | HTTP port |
| `DISK_TYPE` | `LOCAL` | Set to `NETWORK` to disable file writes when using NAS/NFS storage |

For OIDC, Kobo sync, remote auth, Swagger, and all other options see the
[official BookLore documentation](https://booklore.org/docs/getting-started).

---

## SQLite notes

- The database file is created automatically; no setup required.
- SQLite is a single-writer database. Concurrent library scans are handled safely via WAL journal mode and connection handling tuned for this workload, but very large libraries with many simultaneous users may be better served by PostgreSQL.
- Requires SQLite 3.31+ (2020-01-22) for generated column support.

---

## Source

- This wrapper: https://github.com/binarymelon/booklore-multidb
- Upstream BookLore: https://github.com/booklore-app/booklore
- Official docs: https://booklore.org/docs/getting-started
