@echo off
setlocal

set "JAVA_HOME=D:\Program Files\Java\jdk-17"
set "MAVEN_CMD=D:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd"

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo JAVA_HOME not found: %JAVA_HOME%
  exit /b 1
)

if not exist "%MAVEN_CMD%" (
  echo Maven command not found: %MAVEN_CMD%
  exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
call "%MAVEN_CMD%" %*
