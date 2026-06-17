@echo off
cd /d C:\dev\codex-demo
set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot
set MAVEN_HOME=C:\tools\apache-maven-3.9.11
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%
set DB_URL=jdbc:mariadb://127.0.0.1:3306/king_yurina?useSsl=false
set DB_USERNAME=root
set DB_PASSWORD=
"%MAVEN_HOME%\bin\mvn.cmd" "-Dmaven.repo.local=.m2/repository" spring-boot:run "-Dspring-boot.run.profiles=mariadb" "-Dspring-boot.run.arguments=--app.batch.sec-edgar.enabled=true --app.batch.sec-edgar.sync-enabled=true --app.batch.sec-edgar.exit-on-complete=true --app.batch.sec-edgar.symbol-limit=1000 --app.batch.sec-edgar.index-codes=SP500,NASDAQ100,DOW30 --app.batch.finnhub.enabled=false --app.batch.yahoo-candle.enabled=false --app.batch.stooq-candle.enabled=false --app.batch.index.sp500-sync-enabled=false --app.batch.index.nasdaq100-sync-enabled=false --app.batch.index.dow30-sync-enabled=false --app.signal.refresh.enabled=false --app.signal.refresh.run-on-startup=false --spring.main.web-application-type=none"
