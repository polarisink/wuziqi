@echo off
setlocal enabledelayedexpansion

set APP_NAME=Wuziqi
set APP_VERSION=1.0.0
set MAIN_CLASS=com.example.wuziqi.WuziqiApp
set MAIN_JAR=wuziqi-1.0-SNAPSHOT.jar
set DIST_DIR=target\dist\windows
set PACKAGE_INPUT_DIR=target\jpackage-input

if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jpackage.exe" (
    set JPACKAGE=%JAVA_HOME%\bin\jpackage.exe
) else (
    where jpackage >nul 2>nul
    if errorlevel 1 (
        echo jpackage was not found. Please run this script with JDK 25+ on PATH or JAVA_HOME.
        exit /b 1
    )
    set JPACKAGE=jpackage
)

if not defined JPACKAGE (
    where jpackage >nul 2>nul
    if errorlevel 1 (
        echo jpackage was not found. Please run this script with JDK 25+ on PATH or JAVA_HOME.
        exit /b 1
    )
    set JPACKAGE=jpackage
)

where mvnd >nul 2>nul
if errorlevel 1 (
    echo mvnd was not found. Please install mvnd or replace mvnd with mvn in this script.
    exit /b 1
)

if /i not "%PROCESSOR_ARCHITECTURE%"=="AMD64" (
    if /i not "%PROCESSOR_ARCHITEW6432%"=="AMD64" (
        echo Windows x64 EXE packages must be created on Windows x64.
        exit /b 1
    )
)

if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
if exist "%PACKAGE_INPUT_DIR%" rmdir /s /q "%PACKAGE_INPUT_DIR%"
mkdir "%DIST_DIR%"
mkdir "%PACKAGE_INPUT_DIR%\lib"

call mvnd -DskipTests package
if errorlevel 1 exit /b 1

call mvnd -DincludeScope=runtime dependency:copy-dependencies -DoutputDirectory="%PACKAGE_INPUT_DIR%\lib"
if errorlevel 1 exit /b 1

copy "target\%MAIN_JAR%" "%PACKAGE_INPUT_DIR%\%MAIN_JAR%" >nul
if errorlevel 1 exit /b 1

set RUNTIME_CLASSPATH=
for %%F in ("%PACKAGE_INPUT_DIR%\lib\*.jar") do (
    if "!RUNTIME_CLASSPATH!"=="" (
        set RUNTIME_CLASSPATH=lib\%%~nxF
    ) else (
        set RUNTIME_CLASSPATH=!RUNTIME_CLASSPATH!;lib\%%~nxF
    )
)

"%JPACKAGE%" ^
  --type exe ^
  --name "%APP_NAME%" ^
  --app-version "%APP_VERSION%" ^
  --vendor "FXGL" ^
  --dest "%DIST_DIR%" ^
  --input "%PACKAGE_INPUT_DIR%" ^
  --main-jar "%MAIN_JAR%" ^
  --main-class "%MAIN_CLASS%" ^
  --class-path "%RUNTIME_CLASSPATH%" ^
  --win-menu ^
  --win-shortcut ^
  --java-options "-Dfile.encoding=UTF-8"

if errorlevel 1 exit /b 1

echo Created Windows EXE in %DIST_DIR%
