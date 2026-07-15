@echo off
rem Build helper for PendingWhitelist plugin
rem Usage: build-plugin.bat [wrapper]

setlocal enableextensions enabledelayedexpansion

if "%~1"=="wrapper" (
  rem Attempt to generate Gradle wrapper using available Gradle
  if exist gradlew.bat (
    echo Gradle wrapper already exists.
    exit /b 0
  )
  if exist .\gradle-9.0.0\gradle-9.0.0\bin\gradle.bat (
    echo Generating wrapper using local Gradle distribution...
    call .\gradle-9.0.0\gradle-9.0.0\bin\gradle.bat wrapper
    exit /b %ERRORLEVEL%
  )
  where gradle >nul 2>&1
  if %ERRORLEVEL%==0 (
    echo Generating wrapper using system Gradle...
    gradle wrapper
    exit /b %ERRORLEVEL%
  )
  echo No Gradle available to generate wrapper. Install Gradle or place a distribution in .\gradle-9.0.0
  exit /b 1
)

rem Build using gradlew if present
if exist gradlew.bat (
  echo Using gradlew.bat
  call gradlew.bat clean build -x test
  exit /b %ERRORLEVEL%
)

rem Build using local Gradle distribution shipped in repo
if exist .\gradle-9.0.0\gradle-9.0.0\bin\gradle.bat (
  echo Using local Gradle distribution
  call .\gradle-9.0.0\gradle-9.0.0\bin\gradle.bat clean build -x test
  exit /b %ERRORLEVEL%
)

rem Fallback to system Gradle if available
where gradle >nul 2>&1
if %ERRORLEVEL%==0 (
  echo Using system Gradle
  gradle clean build -x test
  exit /b %ERRORLEVEL%
)

echo.
echo No Gradle wrapper, local Gradle, or system Gradle found.
echo To build the plugin:
echo  - Install Gradle and add it to PATH, or
echo  - Place a Gradle distribution under .\gradle-9.0.0\gradle-9.0.0\ and re-run this script, or
echo  - Run this script with the 'wrapper' argument on a machine with Gradle to generate the wrapper:
echo      build-plugin.bat wrapper
exit /b 1
