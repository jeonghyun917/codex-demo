@echo off
cd /d C:\dev\codex-demo
set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot
set MAVEN_HOME=C:\tools\apache-maven-3.9.11
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%
set DB_URL=jdbc:mariadb://127.0.0.1:3306/king_yurina?useSsl=false
set DB_USERNAME=root
set DB_PASSWORD=
"%MAVEN_HOME%\bin\mvn.cmd" "-Dmaven.repo.local=.m2/repository" spring-boot:run "-Dspring-boot.run.profiles=mariadb" "-Dspring-boot.run.arguments=--app.batch.finnhub.enabled=true --app.batch.finnhub.exit-on-complete=true --app.batch.finnhub.symbol-limit=40 --app.batch.finnhub.delay-millis=6000 --app.batch.finnhub.index-codes=SP500,NASDAQ100,DOW30 --app.batch.sec-edgar.enabled=false --app.batch.yahoo-candle.enabled=false --app.batch.stooq-candle.enabled=false --app.batch.index.sp500-sync-enabled=true --app.batch.index.nasdaq100-sync-enabled=true --app.batch.index.dow30-sync-enabled=true --app.signal.refresh.enabled=false --app.signal.refresh.run-on-startup=false --finnhub.news-enabled=true --finnhub.recommendation-enabled=true --finnhub.eps-surprises-enabled=true --finnhub.candle-enabled=false --spring.main.web-application-type=none"
