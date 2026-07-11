# Railway Deploy

This project deploys to Railway as a Dockerized Spring Boot web service.

## Why Docker

The app uses Java 21 and MariaDB-specific schema DDL. The Dockerfile pins Java 21 and avoids ambiguity in Railway's automatic build detection.
Railway detects a root `Dockerfile` automatically, and `railway.json` pins the service to the Dockerfile builder with a `/healthz` deployment healthcheck.

## Recommended Railway Services

Create two services in one Railway project:

1. `mariadb`
   - Type: Docker Image
   - Image: `mariadb:11.8`
   - Add a volume mounted at `/var/lib/mysql`
2. `codex-demo`
   - Type: GitHub Repository
   - Repository: this project

Railway's default MySQL service is not the first choice for this project because `schema-mariadb.sql` uses MariaDB DDL such as `ADD COLUMN IF NOT EXISTS` and `CREATE INDEX IF NOT EXISTS`.

## MariaDB Service Variables

Set these variables on the `mariadb` service:

```text
MARIADB_ROOT_PASSWORD=<strong-root-password>
MARIADB_DATABASE=king_yurina
MARIADB_USER=king_yurina
MARIADB_PASSWORD=<strong-app-password>
```

## App Service Variables

Set these variables on the Spring Boot app service:

```text
SPRING_PROFILES_ACTIVE=mariadb,railway
SQL_INIT_MODE=never
DB_URL=jdbc:mariadb://mariadb.railway.internal:3306/king_yurina?useSsl=false&allowPublicKeyRetrieval=true
DB_USERNAME=king_yurina
DB_PASSWORD=${{mariadb.MARIADB_PASSWORD}}
FINNHUB_API_KEY=<optional>
FRED_API_KEY=<optional>
TOSS_API_KEY=<optional>
TOSS_SECRET_KEY=<optional>
SEC_EDGAR_USER_AGENT=king-yurina-stock-research/0.1 your-email@example.com
```

Keep the profile order as `mariadb,railway`. The `mariadb` profile enables the MyBatis mapper beans, and the later `railway` profile keeps Railway-specific datasource and startup settings in control.

Optional DB pool overrides:

```text
DB_POOL_MAX_SIZE=5
DB_POOL_MIN_IDLE=1
DB_CONNECTION_TIMEOUT_MS=30000
DB_INITIALIZATION_FAIL_TIMEOUT_MS=60000
```

If the MariaDB service has a different Railway service name, replace `mariadb.railway.internal` with `<service-name>.railway.internal`.

Keep `SQL_INIT_MODE=never` when restoring the full local database. The full dump creates the schema and loads every row before the app starts. This is also the safe default in `application-railway.yml`, so an omitted variable cannot unexpectedly rerun seed SQL against production data.

## Full Database Migration

Use this path when Railway should contain the complete local `king_yurina` database. The generated SQL dump, manifest, verification report, and local secret file live under `target/`, which is excluded from Git.

1. Stop local application and collection processes that write to MariaDB. All 64 current tables use InnoDB, so `--single-transaction` then produces a lock-free consistent snapshot.
2. Create the full dump and a manifest containing its SHA-256 hash plus an exact row count for every table:

```powershell
.\scripts\export-railway-full-dump.ps1
```

The default files are:

```text
target\railway-full.sql
target\railway-full-manifest.json
```

3. Deploy the Railway MariaDB service with its volume mounted at `/var/lib/mysql`.
4. Temporarily add a Railway TCP Proxy targeting MariaDB port `3306`.
5. Import through the generated public proxy host and port. The script verifies the dump hash before import, uses MariaDB protocol compression, refuses to overwrite an existing target by default, and then compares exact row counts for every table:

```powershell
.\scripts\import-railway-full-dump.ps1 `
  -HostName "<railway-tcp-proxy-host>" `
  -Port <railway-tcp-proxy-port>
```

By default the import password is read from `target\railway-migration-secrets.json`. Alternatively pass `-Password` explicitly. If a failed attempt left a partial database, fix the underlying error and rerun with `-AllowOverwrite`; the dump contains table replacement DDL.

6. Confirm `target\railway-import-verification.json` reports no missing tables, extra tables, or row-count mismatches.
7. Remove the public TCP Proxy immediately after verification. The app uses Railway private networking and does not require public database access.
8. Create a Railway volume backup before starting the app service.

## Optional Minimal Data Import

The minimal import remains available for a low-storage demo instead of a full migration. It requires the app schema to be initialized first and intentionally omits large history and raw payloads.

The compact dump includes enough rows for the pages to render with real values:

- current S&P 500, Nasdaq 100, and Dow 30 membership rows
- company profile, quote, metric, signal, data-quality, expected-return rows
- recent stock/ETF candles, news, recommendations, EPS surprise, institution flow, macro snapshots
- no large raw JSON payloads and no full multi-year Quant history

Create the local compact dump:

```powershell
.\scripts\export-railway-minimal-dump.ps1
```

The script creates `target\railway-minimal-seed.sql`. The file is intentionally generated under `target/` so it is not committed.

After Railway MariaDB is running and the app has created the schema once, import the compact dump through the temporary Railway TCP Proxy:

```powershell
.\scripts\import-railway-minimal-dump.ps1 `
  -HostName "<railway-public-db-host>" `
  -Port <railway-public-db-port> `
  -Database "king_yurina" `
  -User "king_yurina" `
  -Password "<MARIADB_PASSWORD>"
```

Remove the TCP Proxy after the import.

## Deploy Steps

1. Push this repository to GitHub.
2. In Railway, choose **New Project**.
3. Add the MariaDB Docker image service first.
4. Restore and verify the database, disable its TCP Proxy, and create a backup.
5. Add the GitHub repository service.
6. Set the app service branch to `codex/railway-deploy`.
7. Keep the app and MariaDB services in the same Railway region.
8. Add the app service variables above.
9. Deploy the app service.
10. Confirm the deployment healthcheck passes at `/healthz`.
11. Open the generated Railway domain.

## Notes

- The web service uses Railway's `PORT` variable through `server.port=${PORT:8080}`.
- The deployment healthcheck uses `/healthz`, which intentionally does not query MariaDB.
- Startup signal refresh is disabled in the `railway` profile to keep web deploys fast and stable.
- Run heavy data collection as separate one-off jobs, not on every web boot.
- Keep the MariaDB volume mounted at `/var/lib/mysql`; without a volume, Railway redeploys can lose database files.
- Do not use weak database passwords in production. Temporary Trial passwords should still be replaced before any public deployment.
