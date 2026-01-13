#!/bin/bash

# Wechsle in das Verzeichnis des Skripts
cd "$(dirname "$0")"

echo "========================================"
echo "   Dienstplan-Manager wird gestartet"
echo "========================================"
echo ""

# Prüfe ob Java installiert ist
if ! command -v java &> /dev/null; then
    echo "FEHLER: Java wurde nicht gefunden!"
    echo ""
    echo "Bitte installieren Sie Java 17 oder höher:"
    echo ""
    echo "Ubuntu/Debian:"
    echo "  sudo apt update"
    echo "  sudo apt install openjdk-17-jdk"
    echo ""
    echo "Fedora:"
    echo "  sudo dnf install java-17-openjdk"
    echo ""
    echo "Oder laden Sie Java hier herunter:"
    echo "https://adoptium.net/de/temurin/releases/?version=17"
    echo ""
    read -p "Drücken Sie Enter zum Beenden..."
    exit 1
fi

# Zeige Java-Version
echo "Gefundene Java-Version:"
java -version 2>&1 | head -1
echo ""

# Finde die JAR-Datei
JAR_FILE=$(ls dienstplan-manager*.jar 2>/dev/null | head -1)

if [ -z "$JAR_FILE" ]; then
    echo "FEHLER: Keine JAR-Datei gefunden!"
    echo ""
    echo "Bitte stellen Sie sicher, dass die Datei"
    echo "\"dienstplan-manager-linux.jar\" im selben"
    echo "Ordner wie dieses Skript liegt."
    echo ""
    read -p "Drücken Sie Enter zum Beenden..."
    exit 1
fi

echo "Starte: $JAR_FILE"
echo ""

# Starte die Anwendung
java -jar "$JAR_FILE"

# Falls die Anwendung abstürzt
if [ $? -ne 0 ]; then
    echo ""
    echo "========================================"
    echo "Die Anwendung wurde mit einem Fehler beendet."
    echo "========================================"
    echo ""
    read -p "Drücken Sie Enter zum Beenden..."
fi
