@echo off
cd /d C:\dev\codex-demo
set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot
set MAVEN_HOME=C:\tools\apache-maven-3.9.11
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%
"%JAVA_HOME%\bin\java.exe" -Dspring.profiles.active=mariadb -jar target\codex-demo-0.0.1-SNAPSHOT.jar --spring.main.web-application-type=none --app.signal.refresh.enabled=false --app.batch.historical-backtest.enabled=true --app.batch.historical-backtest.exit-on-complete=true
