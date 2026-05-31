# King Yurina Demo

Spring Boot 4.0.6, Java 21, Spring Framework 7.0.7, Spring Security 7, and MariaDB-ready landing page.

## Run

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot'
$env:MAVEN_HOME='C:\tools\apache-maven-3.9.11'
$env:Path="$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"
mvn.cmd "-Dmaven.repo.local=.m2/repository" spring-boot:run
```

Open `http://127.0.0.1:8080/`.

## MariaDB Profile

The default `local` profile runs the page without requiring a database connection.
Use the `mariadb` profile when MariaDB is ready:

```powershell
$env:DB_URL='jdbc:mariadb://127.0.0.1:3306/king_yurina?useSsl=false'
$env:DB_USERNAME='root'
$env:DB_PASSWORD=''
mvn.cmd "-Dmaven.repo.local=.m2/repository" spring-boot:run "-Dspring-boot.run.profiles=mariadb"
```

## Local MariaDB

MariaDB 11.8.2 LTS is installed as a portable local server for this workspace:

- Server: `C:\dev\codex-demo\.tools\mariadb-11.8.2-winx64`
- Data: `C:\dev\codex-demo\.tools\mariadb-data`
- Config: `C:\dev\codex-demo\.tools\mariadb-my.ini`
- Start script: `C:\dev\codex-demo\.tools\start-mariadb.cmd`
- Host: `127.0.0.1`
- Port: `3306`
- Database: `king_yurina`
- User: `root`
- Password: empty

## Finnhub Batch

Run the one-shot Finnhub collection batch:

```powershell
$env:FINNHUB_API_KEY='your-finnhub-key'
.\run-finnhub-batch.cmd
```

The batch reads active symbols from `stock_symbol`, refreshes quote/profile/metric data, and writes run results to
`finnhub_batch_run`. The default limit is 10 symbols with a 4 second delay between symbols.
