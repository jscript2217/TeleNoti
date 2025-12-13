@echo off
REM Gradle start up script for Windows
REM This wrapper expects gradle/wrapper/gradle-wrapper.jar to exist.
setlocal
set BASEDIR=%~dp0
set WRAPPER_JAR=%BASEDIR%gradle\wrapper\gradle-wrapper.jar

if not exist "%WRAPPER_JAR%" (
  echo ERROR: gradle-wrapper.jar not found at %WRAPPER_JAR%
  echo Run: gradle wrapper --gradle-version 8.4.1
  exit /b 1
)

java -jar "%WRAPPER_JAR%" %*
@echo off
setlocal enabledelayedexpansion
set GRADLE_VERSION=8.4.1
set DIST_URL=https://github.com/gradle/gradle-distributions/releases/download/v%GRADLE_VERSION%/gradle-%GRADLE_VERSION%-bin.zip
set DIST_URL_FALLBACK=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip
set WRAPPER_ROOT=%~dp0.gradle-wrappers
set WRAPPER_DIR=%WRAPPER_ROOT%\gradle-%GRADLE_VERSION%

if not exist "%WRAPPER_DIR%\bin\gradle.bat" (
  echo Gradle %GRADLE_VERSION% not found locally. Downloading...
  set ZIP_PATH=%~dp0gradle-%GRADLE_VERSION%-bin.zip
  powershell -Command "Try { Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%ZIP_PATH%' -UseBasicParsing } Catch { Exit 1 }"
  if errorlevel 1 (
    echo Primary download failed, attempting fallback mirror...
    powershell -Command "Try { Invoke-WebRequest -Uri '%DIST_URL_FALLBACK%' -OutFile '%ZIP_PATH%' -UseBasicParsing } Catch { Exit 2 }"
    if errorlevel 1 (
      echo Failed to download from both sources.
      exit /b 1
    )
  )
  mkdir "%WRAPPER_DIR%" >nul 2>&1
  powershell -Command "Try { Expand-Archive -Path '%ZIP_PATH%' -DestinationPath '%WRAPPER_ROOT%' -Force } Catch { Exit 2 }"
  if errorlevel 1 (
    echo Failed to extract %ZIP_PATH%
    exit /b 2
  )
  del "%ZIP_PATH%" >nul 2>&1
  rem The zip extracts to a folder named gradle-<version> under %WRAPPER_ROOT%.
  rem If that folder exists inside WRAPPER_DIR (nested), move its contents up.
  if exist "%WRAPPER_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat" (
    xcopy "%WRAPPER_DIR%\gradle-%GRADLE_VERSION%\*" "%WRAPPER_DIR%\" /E /I /Y >nul
    rmdir /S /Q "%WRAPPER_DIR%\gradle-%GRADLE_VERSION%" >nul 2>&1
  )
)

set GRADLE_CMD=%WRAPPER_DIR%\bin\gradle.bat
if not exist "%GRADLE_CMD%" (
  echo Could not locate Gradle executable at %GRADLE_CMD%
  exit /b 3
)

rem Forward all args to the downloaded gradle
"%GRADLE_CMD%" %*
@echo off
set DIR=%~dp0
setlocal
set JAVA_CMD=
if defined JAVA_HOME (
  set JAVA_CMD=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_CMD=java
)
"%JAVA_CMD%" -cp "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
endlocal
