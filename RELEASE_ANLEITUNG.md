# Release-Anleitung für Entwickler

Diese Anleitung beschreibt, wie Sie neue Versionen des Dienstplan-Managers für Tester bereitstellen können.

---

## Übersicht

Das Projekt verwendet **GitHub Actions** für automatische Builds. Wenn Sie ein neues Release erstellen möchten, haben Sie zwei Möglichkeiten:

1. **Automatisch per Git-Tag** (empfohlen)
2. **Manuell über GitHub Actions**

---

## Option 1: Release per Git-Tag (Empfohlen)

### Schritt 1: Version aktualisieren

Aktualisieren Sie die Version in der `pom.xml`:

```xml
<version>1.1.0</version>
```

### Schritt 2: Änderungen committen

```bash
git add .
git commit -m "Release v1.1.0"
```

### Schritt 3: Tag erstellen und pushen

```bash
# Tag erstellen
git tag v1.1.0

# Tag pushen (triggert automatisch den Build)
git push origin v1.1.0

# Commit pushen
git push origin main
```

### Was passiert automatisch:

1. GitHub Actions erkennt den neuen Tag `v1.1.0`
2. Baut die Anwendung für Windows, macOS und Linux
3. Erstellt ein GitHub Release mit allen Dateien
4. Tester können das Release sofort herunterladen

---

## Option 2: Manuelles Release über GitHub

### Schritt 1: Zur Actions-Seite gehen

1. Öffnen Sie Ihr Repository auf GitHub
2. Klicken Sie auf den Tab **"Actions"**
3. Wählen Sie den Workflow **"Build and Release"**

### Schritt 2: Workflow manuell starten

1. Klicken Sie auf **"Run workflow"**
2. Wählen Sie den Branch (normalerweise `main`)
3. Geben Sie die Version ein (z.B. `1.1.0`)
4. Klicken Sie auf **"Run workflow"**

### Schritt 3: Warten und prüfen

1. Der Build dauert ca. 5-10 Minuten
2. Prüfen Sie den Status in der Actions-Übersicht
3. Nach Abschluss finden Sie das Release unter "Releases"

---

## Lokaler Build (ohne GitHub Actions)

Falls Sie lokal bauen möchten:

### Für Ihr aktuelles Betriebssystem:

```bash
mvn clean package
```

### Für ein spezifisches Betriebssystem:

```bash
# Windows
mvn clean package -P windows

# macOS
mvn clean package -P mac

# Linux
mvn clean package -P linux
```

### Ausgabe:

Die fertige JAR-Datei finden Sie unter:
```
target/dienstplan-manager-1.0.0.jar
```

---

## Tester informieren

Nach dem Release sollten Sie Ihre Tester informieren:

### E-Mail-Vorlage:

```
Betreff: Neue Version des Dienstplan-Managers verfügbar (v1.1.0)

Hallo [Name],

eine neue Version des Dienstplan-Managers steht zum Testen bereit!

Download: [Link zum GitHub Release]

Änderungen in dieser Version:
- [Änderung 1]
- [Änderung 2]
- [Bugfix 1]

Bei Fragen oder Problemen melden Sie sich gerne.

Viele Grüße
[Ihr Name]
```

---

## Projektstruktur für Releases

```
dienstplan-manager/
├── .github/
│   └── workflows/
│       └── release.yml          # GitHub Actions Workflow
├── scripts/
│   ├── start-windows.bat        # Windows Startskript
│   ├── start-mac.command        # macOS Startskript
│   └── start-linux.sh           # Linux Startskript
├── TESTER_ANLEITUNG.md          # Anleitung für Tester
├── RELEASE_ANLEITUNG.md         # Diese Datei
├── pom.xml                      # Maven Build-Konfiguration
└── src/                         # Quellcode
```

---

## Versionierung

Wir empfehlen [Semantic Versioning](https://semver.org/):

- **MAJOR.MINOR.PATCH** (z.B. 1.2.3)
- **MAJOR**: Inkompatible Änderungen
- **MINOR**: Neue Funktionen, abwärtskompatibel
- **PATCH**: Bugfixes, abwärtskompatibel

### Beispiele:

| Änderung | Alte Version | Neue Version |
|----------|--------------|--------------|
| Kleiner Bugfix | 1.0.0 | 1.0.1 |
| Neue Funktion | 1.0.1 | 1.1.0 |
| Große Überarbeitung | 1.1.0 | 2.0.0 |

---

## Fehlerbehebung

### Build schlägt fehl

1. Prüfen Sie die Logs in GitHub Actions
2. Häufige Probleme:
   - Tests schlagen fehl → `-DskipTests` ist bereits gesetzt
   - Maven-Fehler → Prüfen Sie die `pom.xml`

### Release wird nicht erstellt

1. Prüfen Sie, ob der Tag korrekt gepusht wurde
2. Tag muss mit `v` beginnen (z.B. `v1.0.0`)
3. Prüfen Sie die GitHub Actions Berechtigungen

### Tester können nicht starten

1. Prüfen Sie, ob Java korrekt installiert ist
2. Verweisen Sie auf die `TESTER_ANLEITUNG.md`

---

## Checkliste vor dem Release

- [ ] Alle Tests bestehen (`mvn test`)
- [ ] Version in `pom.xml` aktualisiert
- [ ] TESTER_ANLEITUNG.md ist aktuell
- [ ] Changelog dokumentiert (optional)
- [ ] Lokaler Build funktioniert (`mvn clean package`)
- [ ] Anwendung startet und funktioniert

---

## Support

Bei Problemen mit dem Build-Prozess:

1. Prüfen Sie die GitHub Actions Logs
2. Testen Sie den Build lokal
3. Prüfen Sie die Maven-Konfiguration

---

**Viel Erfolg mit Ihren Releases!**
