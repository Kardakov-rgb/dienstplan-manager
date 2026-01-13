@echo off
chcp 65001 >nul
title Dienstplan-Manager

echo ========================================
echo    Dienstplan-Manager wird gestartet
echo ========================================
echo.

REM Prüfe ob Java installiert ist
where java >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo FEHLER: Java wurde nicht gefunden!
    echo.
    echo Bitte installieren Sie Java 17 oder höher:
    echo https://adoptium.net/de/temurin/releases/?version=17
    echo.
    echo Drücken Sie eine beliebige Taste zum Beenden...
    pause >nul
    exit /b 1
)

REM Zeige Java-Version
echo Gefundene Java-Version:
java -version 2>&1 | findstr /i "version"
echo.

REM Finde die JAR-Datei im aktuellen Verzeichnis
set JAR_FILE=
for %%f in (dienstplan-manager*.jar) do set JAR_FILE=%%f

if "%JAR_FILE%"=="" (
    echo FEHLER: Keine JAR-Datei gefunden!
    echo.
    echo Bitte stellen Sie sicher, dass die Datei
    echo "dienstplan-manager-windows.jar" im selben
    echo Ordner wie dieses Skript liegt.
    echo.
    echo Drücken Sie eine beliebige Taste zum Beenden...
    pause >nul
    exit /b 1
)

echo Starte: %JAR_FILE%
echo.

REM Starte die Anwendung
java -jar "%JAR_FILE%"

REM Falls die Anwendung abstürzt
if %ERRORLEVEL% neq 0 (
    echo.
    echo ========================================
    echo Die Anwendung wurde mit einem Fehler beendet.
    echo Fehlercode: %ERRORLEVEL%
    echo ========================================
    echo.
    echo Drücken Sie eine beliebige Taste zum Beenden...
    pause >nul
)
