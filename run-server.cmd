@echo off
cd /d C:\dev\codex-demo
set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot
set MAVEN_HOME=C:\tools\apache-maven-3.9.11
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%
"%MAVEN_HOME%\bin\mvn.cmd" "-Dmaven.repo.local=.m2/repository" spring-boot:run
