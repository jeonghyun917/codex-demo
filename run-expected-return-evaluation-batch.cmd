@echo off
cd /d C:\dev\codex-demo
set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot
set MAVEN_HOME=C:\tools\apache-maven-3.9.11
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%
set DB_URL=jdbc:mariadb://127.0.0.1:3306/king_yurina?useSsl=false
set DB_USERNAME=root
set DB_PASSWORD=
"%MAVEN_HOME%\bin\mvn.cmd" "-Dmaven.repo.local=.m2/repository" spring-boot:run "-Dspring-boot.run.profiles=mariadb" "-Dspring-boot.run.arguments=--app.batch.expected-return-evaluation.enabled=true --app.batch.expected-return-evaluation.exit-on-complete=true --app.batch.expected-return-evaluation.index-code=SP500 --app.signal.refresh.run-on-startup=false --spring.main.web-application-type=none"

