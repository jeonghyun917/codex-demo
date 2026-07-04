@echo off
setlocal
cd /d C:\dev\codex-demo
set "JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
set "MAVEN_HOME=C:\tools\apache-maven-3.9.11"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"
set "DB_URL=jdbc:mariadb://127.0.0.1:3306/king_yurina?useSsl=false"
set "DB_USERNAME=root"
set "DB_PASSWORD="

for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "[Environment]::GetEnvironmentVariable('FRED_API_KEY','Machine')"`) do set "FRED_API_KEY=%%A"

set "FRED_SERIES=%~1"
if "%FRED_SERIES%"=="" set "FRED_SERIES=DGS3MO,FEDFUNDS,CPIAUCSL,UNRATE"
set "FRED_YEARS=%~2"
if "%FRED_YEARS%"=="" set "FRED_YEARS=5"

"%MAVEN_HOME%\bin\mvn.cmd" "-Dmaven.repo.local=.m2/repository" spring-boot:run "-Dspring-boot.run.profiles=mariadb" "-Dspring-boot.run.arguments=--app.batch.macro-vintage.enabled=true --app.batch.macro-vintage.exit-on-complete=true --app.batch.macro-vintage.fetch-fred-api=true --app.batch.macro-vintage.series-codes=%FRED_SERIES% --app.batch.macro-vintage.years=%FRED_YEARS% --app.batch.finnhub.enabled=false --app.batch.sec-edgar.enabled=false --app.batch.yahoo-candle.enabled=false --app.batch.stooq-candle.enabled=false --app.batch.institution-13f.enabled=false --app.signal.refresh.enabled=false --app.signal.refresh.run-on-startup=false --spring.main.web-application-type=none"
