# Dienstplan-Manager - Anleitung für Tester

Vielen Dank, dass Sie uns beim Testen des Dienstplan-Managers helfen! Diese Anleitung führt Sie Schritt für Schritt durch die Installation und Nutzung.

---

## Inhaltsverzeichnis

1. [Voraussetzungen](#1-voraussetzungen)
2. [Java installieren](#2-java-installieren)
3. [Programm herunterladen](#3-programm-herunterladen)
4. [Programm starten](#4-programm-starten)
5. [Häufige Probleme und Lösungen](#5-häufige-probleme-und-lösungen)
6. [Feedback geben](#6-feedback-geben)

---

## 1. Voraussetzungen

Um den Dienstplan-Manager nutzen zu können, benötigen Sie:

- Einen Computer mit **Windows**, **macOS** oder **Linux**
- **Java 17** oder höher (kostenlos, Anleitung unten)
- Ca. **100 MB** freien Speicherplatz

---

## 2. Java installieren

Der Dienstplan-Manager benötigt Java. Prüfen Sie zunächst, ob Java bereits installiert ist.

### Java-Installation prüfen

#### Windows:
1. Drücken Sie die **Windows-Taste** + **R**
2. Geben Sie `cmd` ein und drücken Sie **Enter**
3. Geben Sie `java -version` ein und drücken Sie **Enter**
4. Wenn eine Versionsnummer angezeigt wird (z.B. "17.0.x"), ist Java installiert

#### macOS:
1. Öffnen Sie das **Terminal** (Programme → Dienstprogramme → Terminal)
2. Geben Sie `java -version` ein und drücken Sie **Enter**
3. Wenn eine Versionsnummer angezeigt wird, ist Java installiert

#### Linux:
1. Öffnen Sie ein **Terminal**
2. Geben Sie `java -version` ein und drücken Sie **Enter**
3. Wenn eine Versionsnummer angezeigt wird, ist Java installiert

### Java herunterladen und installieren

Wenn Java nicht installiert ist oder die Version zu alt ist (unter 17), laden Sie Java hier herunter:

**Empfohlener Download:** [Adoptium Temurin 17](https://adoptium.net/de/temurin/releases/?version=17)

#### Installationsanleitung für Windows:

1. Öffnen Sie den Link oben
2. Wählen Sie:
   - **Operating System:** Windows
   - **Architecture:** x64
   - **Package Type:** .msi (Installer)
3. Klicken Sie auf den Download-Button
4. Führen Sie die heruntergeladene `.msi`-Datei aus
5. Folgen Sie dem Installationsassistenten (alle Standardeinstellungen beibehalten)
6. **Wichtig:** Aktivieren Sie die Option "Set JAVA_HOME variable"

#### Installationsanleitung für macOS:

1. Öffnen Sie den Link oben
2. Wählen Sie:
   - **Operating System:** macOS
   - **Architecture:** x64 (Intel) oder aarch64 (Apple Silicon/M1/M2)
   - **Package Type:** .pkg (Installer)
3. Klicken Sie auf den Download-Button
4. Führen Sie die heruntergeladene `.pkg`-Datei aus
5. Folgen Sie dem Installationsassistenten

#### Installationsanleitung für Linux (Ubuntu/Debian):

Öffnen Sie ein Terminal und führen Sie aus:
```bash
sudo apt update
sudo apt install openjdk-17-jdk
```

---

## 3. Programm herunterladen

### Schritt 1: Zur Release-Seite gehen

1. Öffnen Sie Ihren Webbrowser (Chrome, Firefox, Edge, Safari)
2. Gehen Sie zur GitHub-Seite des Projekts
3. Klicken Sie rechts auf **"Releases"** (oder suchen Sie den Bereich "Releases")

### Schritt 2: Die richtigen Dateien herunterladen

Laden Sie **zwei Dateien** herunter - passend zu Ihrem Betriebssystem:

| Ihr Betriebssystem | JAR-Datei herunterladen | Startskript herunterladen |
|--------------------|-------------------------|---------------------------|
| **Windows** | `dienstplan-manager-windows.jar` | `start-windows.bat` |
| **macOS** | `dienstplan-manager-mac.jar` | `start-mac.command` |
| **Linux** | `dienstplan-manager-linux.jar` | `start-linux.sh` |

### Schritt 3: Dateien speichern

1. Erstellen Sie einen neuen Ordner auf Ihrem Computer, z.B.:
   - Windows: `C:\Programme\Dienstplan-Manager\`
   - macOS: `Programme/Dienstplan-Manager/`
   - Linux: `~/Dienstplan-Manager/`
2. Verschieben Sie **beide heruntergeladenen Dateien** in diesen Ordner
3. Die JAR-Datei und das Startskript müssen im **selben Ordner** sein!

---

## 4. Programm starten

### Windows

1. Öffnen Sie den Ordner, in dem Sie die Dateien gespeichert haben
2. **Doppelklicken** Sie auf `start-windows.bat`
3. Falls eine Windows-Sicherheitswarnung erscheint:
   - Klicken Sie auf "Weitere Informationen"
   - Klicken Sie auf "Trotzdem ausführen"
4. Das Programm sollte nun starten

### macOS

1. Öffnen Sie den Ordner, in dem Sie die Dateien gespeichert haben
2. **Beim ersten Start:** Rechtsklicken Sie auf `start-mac.command` und wählen Sie "Öffnen"
3. Falls eine Sicherheitswarnung erscheint:
   - Klicken Sie auf "Öffnen"
4. Bei weiteren Starts können Sie einfach doppelklicken

**Alternative für macOS:**
1. Öffnen Sie das Terminal
2. Navigieren Sie zum Ordner: `cd /Pfad/zum/Ordner`
3. Führen Sie aus: `java -jar dienstplan-manager-mac.jar`

### Linux

1. Öffnen Sie ein Terminal
2. Navigieren Sie zum Ordner: `cd /pfad/zum/ordner`
3. Machen Sie das Skript ausführbar: `chmod +x start-linux.sh`
4. Führen Sie aus: `./start-linux.sh`

**Alternative:**
```bash
java -jar dienstplan-manager-linux.jar
```

---

## 5. Häufige Probleme und Lösungen

### Problem: "Java wurde nicht gefunden"

**Lösung:**
- Installieren Sie Java wie in Abschnitt 2 beschrieben
- Starten Sie Ihren Computer neu nach der Java-Installation
- Versuchen Sie es erneut

### Problem: "Keine JAR-Datei gefunden"

**Lösung:**
- Stellen Sie sicher, dass die JAR-Datei im selben Ordner wie das Startskript liegt
- Prüfen Sie, ob die Dateiendung `.jar` ist (nicht `.jar.txt`)

### Problem: Windows blockiert das Startskript

**Lösung:**
1. Rechtsklicken Sie auf die `.bat`-Datei
2. Wählen Sie "Eigenschaften"
3. Unten bei "Sicherheit" klicken Sie auf "Zulassen"
4. Klicken Sie auf "OK"

### Problem: macOS zeigt "Programm kann nicht geöffnet werden"

**Lösung:**
1. Öffnen Sie **Systemeinstellungen** → **Sicherheit & Datenschutz**
2. Im Tab "Allgemein" sehen Sie eine Meldung über die blockierte App
3. Klicken Sie auf "Trotzdem öffnen"

### Problem: Das Programm startet, aber stürzt sofort ab

**Mögliche Ursachen:**
- Java-Version zu alt → Installieren Sie Java 17
- Nicht genug Arbeitsspeicher → Schließen Sie andere Programme

**Für technischen Support:** Notieren Sie die Fehlermeldung im schwarzen Fenster

### Problem: Die Schrift ist zu klein/groß

**Lösung:**
- Dies ist eine bekannte Einschränkung bei JavaFX
- Versuchen Sie, die Bildschirmauflösung anzupassen

---

## 6. Feedback geben

Wir freuen uns über jedes Feedback! Bitte teilen Sie uns mit:

### Was wir wissen möchten:

1. **Gefundene Fehler (Bugs)**
   - Was haben Sie gemacht, bevor der Fehler auftrat?
   - Was ist passiert?
   - Was hätten Sie erwartet?
   - Fehlermeldungen (falls vorhanden)

2. **Verbesserungsvorschläge**
   - Was könnte besser/einfacher sein?
   - Welche Funktionen fehlen Ihnen?

3. **Allgemeines Feedback**
   - Ist das Programm verständlich?
   - Ist die Bedienung intuitiv?

### So geben Sie Feedback:

#### Option 1: GitHub Issues (empfohlen)

1. Gehen Sie zur GitHub-Seite des Projekts
2. Klicken Sie auf den Tab **"Issues"**
3. Klicken Sie auf **"New Issue"**
4. Geben Sie einen aussagekräftigen Titel ein
5. Beschreiben Sie das Problem oder den Vorschlag
6. Klicken Sie auf "Submit new issue"

#### Option 2: Direkter Kontakt

Kontaktieren Sie den Entwickler direkt per E-Mail oder über den vereinbarten Kommunikationskanal.

---

## Systemanforderungen

| Anforderung | Minimum | Empfohlen |
|-------------|---------|-----------|
| Betriebssystem | Windows 10, macOS 10.14, Ubuntu 20.04 | Neueste Version |
| Java | Version 17 | Version 17 oder 21 |
| RAM | 2 GB | 4 GB |
| Bildschirm | 800 x 600 Pixel | 1920 x 1080 Pixel |
| Speicherplatz | 100 MB | 200 MB |

---

## Tastenkürzel

| Aktion | Windows/Linux | macOS |
|--------|---------------|-------|
| Rückgängig | Strg + Z | Cmd + Z |
| Wiederholen | Strg + Y | Cmd + Shift + Z |
| Speichern | Strg + S | Cmd + S |

---

**Vielen Dank für Ihre Hilfe beim Testen!**

Ihr Feedback hilft uns, den Dienstplan-Manager zu verbessern.
