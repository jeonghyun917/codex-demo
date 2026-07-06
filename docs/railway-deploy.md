# Railway Deploy

This project deploys to Railway as a Dockerized Spring Boot web service.

## Why Docker

The app uses Java 21 and MariaDB-specific schema DDL. The Dockerfile pins Java 21 and avoids ambiguity in Railway's automatic build detection.

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
SPRING_PROFILES_ACTIVE=railway
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

If the MariaDB service has a different Railway service name, replace `mariadb.railway.internal` with `<service-name>.railway.internal`.

Use `SQL_INIT_MODE=always` for the first deploy so Railway creates the schema and seed rows. After the first successful deploy, change it to `SQL_INIT_MODE=never` unless you intentionally want schema/data init to run on every app boot.

## Deploy Steps

1. Push this repository to GitHub.
2. In Railway, choose **New Project**.
3. Add the MariaDB Docker image service first.
4. Add the GitHub repository service.
5. Add the app service variables above.
6. Deploy the app service.
7. Open the generated Railway domain.

## Notes

- The web service uses Railway's `PORT` variable through `server.port=${PORT:8080}`.
- Startup signal refresh is disabled in the `railway` profile to keep web deploys fast and stable.
- Run heavy data collection as separate one-off jobs, not on every web boot.
- Keep the MariaDB volume mounted at `/var/lib/mysql`; without a volume, Railway redeploys can lose database files.
