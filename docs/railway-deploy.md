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
SQL_INIT_MODE=always
DB_URL=jdbc:mariadb://mariadb.railway.internal:3306/king_yurina?useSsl=false&allowPublicKeyRetrieval=true
DB_USERNAME=king_yurina
DB_PASSWORD=<same-as-MARIADB_PASSWORD>
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

Use `SQL_INIT_MODE=always` for the first deploy so Railway creates the schema and base seed rows. After the first successful deploy, change it to `SQL_INIT_MODE=never` unless you intentionally want schema/data init to run on every app boot.

## Minimal Data Import Without Committing DB Rows

Do not commit local research DB dumps to GitHub. For Trial/Hobby deployments, create a compact dump locally and import it directly into Railway MariaDB.

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

After Railway MariaDB is running and the app has created the schema once, import the compact dump through the Railway MariaDB public host/port:

```powershell
.\scripts\import-railway-minimal-dump.ps1 `
  -HostName "<railway-public-db-host>" `
  -Port <railway-public-db-port> `
  -Database "king_yurina" `
  -User "king_yurina" `
  -Password "<MARIADB_PASSWORD>"
```

If public networking is disabled on the MariaDB service, enable Railway TCP proxy/public networking temporarily, run the import, then disable it again.

## Deploy Steps

1. Push this repository to GitHub.
2. In Railway, choose **New Project**.
3. Add the MariaDB Docker image service first.
4. Add the GitHub repository service.
5. Set the app service branch to `codex/railway-deploy`.
6. Add the app service variables above.
7. Deploy the app service.
8. Confirm the deployment healthcheck passes at `/healthz`.
9. Open the generated Railway domain.

## Notes

- The web service uses Railway's `PORT` variable through `server.port=${PORT:8080}`.
- The deployment healthcheck uses `/healthz`, which intentionally does not query MariaDB.
- Startup signal refresh is disabled in the `railway` profile to keep web deploys fast and stable.
- Run heavy data collection as separate one-off jobs, not on every web boot.
- Keep the MariaDB volume mounted at `/var/lib/mysql`; without a volume, Railway redeploys can lose database files.
- Do not use weak database passwords in production. Temporary Trial passwords should still be replaced before any public deployment.
