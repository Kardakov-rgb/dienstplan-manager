@echo off
chcp 65001 >nul
title Dienstplan-Manager - Entwicklungsmodus

echo ========================================
echo   Dienstplan-Manager - Entwicklungsstart
echo ========================================
echo.

REM Prueffe ob Maven installiert ist
where mvn >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo FEHLER: Maven wurde nicht gefunden!
    echo.
    echo Bitte installieren Sie Maven und stellen Sie sicher,
    echo dass mvn.cmd im PATH verfuegbar ist.
    echo https://maven.apache.org/download.cgi
    echo.
    pause
    exit /b 1
)

REM Prueffe ob Java installiert ist
where java >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo FEHLER: Java wurde nicht gefunden!
    echo.
    echo Bitte installieren Sie Java 17 oder hoeher.
    echo https://adoptium.net/de/temurin/releases/?version=17
    echo.
    pause
    exit /b 1
)

echo Starte Anwendung mit Maven...
echo Befehl: mvn clean javafx:run
echo.

cd /d "%~dp0.."
mvn clean javafx:run

if %ERRORLEVEL% neq 0 (
    echo.
    echo ========================================
    echo Build oder Start fehlgeschlagen.
    echo Fehlercode: %ERRORLEVEL%
    echo ========================================
    echo.
    pause
)
