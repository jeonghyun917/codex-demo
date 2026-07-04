@echo off
setlocal
cd /d C:\dev\codex-demo
set "JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
set "MAVEN_HOME=C:\tools\apache-maven-3.9.11"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"
set "DB_URL=jdbc:mariadb://127.0.0.1:3306/king_yurina?useSsl=false"
set "DB_USERNAME=root"
set "DB_PASSWORD="

for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "[Environment]::GetEnvironmentVariable('TOSS_API_KEY','Machine')"`) do set "TOSS_API_KEY=%%A"
for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "[Environment]::GetEnvironmentVariable('TOSS_SECRET_KEY','Machine')"`) do set "TOSS_SECRET_KEY=%%A"

set "TOSS_SYMBOLS=%~1"
if "%TOSS_SYMBOLS%"=="" set "TOSS_SYMBOLS=NVDA,AAPL,MSFT"
set "TOSS_SYMBOLS=%TOSS_SYMBOLS:;=,%"
set "TOSS_SYMBOL_LIMIT=%~2"
if "%TOSS_SYMBOL_LIMIT%"=="" set "TOSS_SYMBOL_LIMIT=40"
set "TOSS_CANDLE_COUNT=%~3"
if "%TOSS_CANDLE_COUNT%"=="" set "TOSS_CANDLE_COUNT=120"

set "APP_BATCH_TOSS_SYMBOLS=%TOSS_SYMBOLS%"
set "APP_BATCH_TOSS_SYMBOL_LIMIT=%TOSS_SYMBOL_LIMIT%"
set "APP_BATCH_TOSS_CANDLE_COUNT=%TOSS_CANDLE_COUNT%"

"%MAVEN_HOME%\bin\mvn.cmd" "-Dmaven.repo.local=.m2/repository" spring-boot:run "-Dspring-boot.run.profiles=mariadb" "-Dspring-boot.run.arguments=--app.batch.toss.enabled=true --app.batch.toss.exit-on-complete=true --app.batch.toss.delay-millis=250 --app.batch.finnhub.enabled=false --app.batch.sec-edgar.enabled=false --app.batch.yahoo-candle.enabled=false --app.batch.stooq-candle.enabled=false --app.batch.institution-13f.enabled=false --app.signal.refresh.enabled=false --app.signal.refresh.run-on-startup=false --spring.main.web-application-type=none"
