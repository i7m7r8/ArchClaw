@rem Gradle wrapper script for Windows
@echo off
set JAVA_HOME=%JAVA_HOME%
java -Xmx64m -Xms64m ^
    "-Dorg.gradle.appname=gradlew" ^
    -jar "%~dp0gradle\wrapper\gradle-wrapper.jar" ^
    %*
