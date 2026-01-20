package de.dienstplan.controller;

import de.dienstplan.database.*;
import de.dienstplan.model.Person;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller für das Hauptfenster mit Navigation und Dashboard
 */
public class HauptfensterController implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(HauptfensterController.class);
    
    // FXML Controls - Menu und Toolbar
    @FXML private MenuBar menuBar;
    @FXML private ToolBar toolBar;
    @FXML private TextField globalSuchfeld;
    
    // FXML Controls - Tabs
    @FXML private TabPane mainTabPane;
    @FXML private Tab dashboardTab;
    @FXML private Tab personenverwaltungTab;
    @FXML private Tab dienstplanerstellungTab;
    @FXML private Tab statistikenTab;
    
    // FXML Controls - Dashboard
    @FXML private Label dashboardPersonenAnzahl;
    @FXML private Label dashboardDienstplaeneAnzahl;
    @FXML private Label dashboardDiensteAnzahl;
    @FXML private ListView<String> aktivitaetenList;
    @FXML private ListView<String> aufgabenList;
    @FXML private Label datenbankStatusLabel;
    @FXML private Label datenbankGroesseLabel;
    @FXML private Label letzteSicherungLabel;
    @FXML private Label versionLabel;
    
    // FXML Controls - Status
    @FXML private HBox statusLeiste;
    @FXML private Label hauptStatusLabel;
    @FXML private Label personenStatusLabel;
    @FXML private Label dienstplaeneStatusLabel;
    @FXML private Label uhrzeitLabel;
    
    // FXML Controls - Menü Items
    @FXML private CheckMenuItem statusleisteMenuItem;
    @FXML private CheckMenuItem toolbarMenuItem;
    
    // Services
    private final PersonDAO personDAO;
    private final DienstplanDAO dienstplanDAO;
    private final DienstDAO dienstDAO;
    
    // UI-Daten
    private final ObservableList<String> aktivitaetenListe = FXCollections.observableArrayList();
    private final ObservableList<String> aufgabenListe = FXCollections.observableArrayList();
    private Timeline uhrzeitTimer;
    private Timeline dashboardUpdateTimer;
    
    public HauptfensterController() {
        this.personDAO = new PersonDAO();
        this.dienstplanDAO = new DienstplanDAO();
        this.dienstDAO = new DienstDAO();
    }
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initialisiere Hauptfenster...");
        
        try {
            initializeDatabase();
            initializeComponents();
            initializeTimers();
            updateDashboard();
            loadAktivitaeten();
            
            setHauptStatus("Dienstplan-Generator erfolgreich gestartet");
            logger.info("Hauptfenster erfolgreich initialisiert");
            
        } catch (Exception e) {
            logger.error("Fehler beim Initialisieren des Hauptfensters", e);
            setHauptStatus("Fehler beim Laden: " + e.getMessage());
            showError("Initialisierungsfehler", "Das Hauptfenster konnte nicht vollständig initialisiert werden.", e);
        }
    }
    
    private void initializeDatabase() throws SQLException {
        try {
            DatabaseManager.initializeDatabase();
            datenbankStatusLabel.setText("✅ Verbunden");
            datenbankStatusLabel.setStyle("-fx-text-fill: green;");
            
            // Datenbankgröße laden
            updateDatenbankInfo();
            
        } catch (Exception e) {
            datenbankStatusLabel.setText("❌ Fehler");
            datenbankStatusLabel.setStyle("-fx-text-fill: red;");
            throw e;
        }
    }
    
    private void initializeComponents() {
        // Listen konfigurieren
        aktivitaetenList.setItems(aktivitaetenListe);
        aufgabenList.setItems(aufgabenListe);
        
        // Global-Suche Handler
        globalSuchfeld.textProperty().addListener((obs, oldText, newText) -> {
            // TODO: Implementiere Live-Suche
        });
        
        // Tab-Wechsel Handler
        mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                onTabWechsel(newTab);
            }
        });
        
        // Version setzen
        versionLabel.setText("v1.0.0-BETA");
    }
    
    private void initializeTimers() {
        // Uhrzeit-Timer (jede Sekunde)
        uhrzeitTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateUhrzeit()));
        uhrzeitTimer.setCycleCount(Timeline.INDEFINITE);
        uhrzeitTimer.play();
        
        // Dashboard-Update Timer (alle 30 Sekunden)
        dashboardUpdateTimer = new Timeline(new KeyFrame(Duration.seconds(30), e -> {
            try {
                updateDashboard();
            } catch (Exception ex) {
                logger.warn("Fehler beim Dashboard-Update", ex);
            }
        }));
        dashboardUpdateTimer.setCycleCount(Timeline.INDEFINITE);
        dashboardUpdateTimer.play();
    }
    
    // Menu Event Handler
    
    @FXML
    private void onNeueDaten() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Neue Daten");
        confirm.setHeaderText("Möchten Sie alle Daten löschen und neu beginnen?");
        confirm.setContentText("WARNUNG: Alle Personen und Dienstpläne werden gelöscht!");
        
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                DatabaseManager.dropAllTables();
                DatabaseManager.initializeDatabase();
                updateDashboard();
                addAktivitaet("🗑️ Alle Daten zurückgesetzt");
                setHauptStatus("Alle Daten wurden zurückgesetzt");
                showInfo("Erfolgreich", "Alle Daten wurden gelöscht. Sie können nun neu beginnen.");
                
            } catch (SQLException e) {
                logger.error("Fehler beim Zurücksetzen der Daten", e);
                showError("Fehler", "Die Daten konnten nicht zurückgesetzt werden.", e);
            }
        }
    }
    
    @FXML
    private void onImport() {
        showInfo("Import", "Import-Funktion wird in einer zukünftigen Version implementiert.");
    }
    
    @FXML
    private void onExport() {
        showInfo("Export", "Export-Funktion wird in einer zukünftigen Version implementiert.");
    }
    
    @FXML
    private void onEinstellungen() {
        showInfo("Einstellungen", "Einstellungen werden in einer zukünftigen Version implementiert.");
    }
    
    @FXML
    private void onBeenden() {
        Platform.exit();
    }
    
    @FXML
    private void onNeuePersonHinzufuegen() {
        mainTabPane.getSelectionModel().select(personenverwaltungTab);
        // TODO: Trigger "Neue Person" Action in PersonenverwaltungController
        addAktivitaet("👤 Zur Personenverwaltung gewechselt");
    }
    
    @FXML
    private void onNeuerDienstplan() {
        mainTabPane.getSelectionModel().select(dienstplanerstellungTab);
        addAktivitaet("📅 Zur Dienstplanerstellung gewechselt");
    }
    
    @FXML
    private void onAllePersonenLoeschen() {
        try {
            int anzahl = personDAO.count();
            if (anzahl == 0) {
                showInfo("Keine Personen", "Es sind keine Personen zum Löschen vorhanden.");
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Alle Personen löschen");
            confirm.setHeaderText("Möchten Sie wirklich alle " + anzahl + " Personen löschen?");
            confirm.setContentText("WARNUNG: Diese Aktion kann nicht rückgängig gemacht werden!");

            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                int geloescht = personDAO.deleteAll();
                addAktivitaet("🗑️ " + geloescht + " Personen gelöscht");
                updateDashboard();
                setHauptStatus(geloescht + " Personen wurden gelöscht");
                showInfo("Erfolgreich", geloescht + " Personen wurden gelöscht.");
            }
        } catch (SQLException e) {
            logger.error("Fehler beim Löschen aller Personen", e);
            showError("Fehler", "Die Personen konnten nicht gelöscht werden.", e);
        }
    }

    @FXML
    private void onAlleDienstplaeneLoeschen() {
        try {
            int anzahl = dienstplanDAO.count();
            if (anzahl == 0) {
                showInfo("Keine Dienstpläne", "Es sind keine Dienstpläne zum Löschen vorhanden.");
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Alle Dienstpläne löschen");
            confirm.setHeaderText("Möchten Sie wirklich alle " + anzahl + " Dienstpläne löschen?");
            confirm.setContentText("WARNUNG: Diese Aktion kann nicht rückgängig gemacht werden!");

            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                int geloescht = dienstplanDAO.deleteAll();
                addAktivitaet("🗑️ " + geloescht + " Dienstpläne gelöscht");
                updateDashboard();
                setHauptStatus(geloescht + " Dienstpläne wurden gelöscht");
                showInfo("Erfolgreich", geloescht + " Dienstpläne wurden gelöscht.");
            }
        } catch (SQLException e) {
            logger.error("Fehler beim Löschen aller Dienstpläne", e);
            showError("Fehler", "Die Dienstpläne konnten nicht gelöscht werden.", e);
        }
    }
    
    @FXML
    private void onStatusleisteToggle() {
        boolean sichtbar = statusleisteMenuItem.isSelected();
        statusLeiste.setVisible(sichtbar);
        statusLeiste.setManaged(sichtbar);
    }
    
    @FXML
    private void onToolbarToggle() {
        boolean sichtbar = toolbarMenuItem.isSelected();
        toolBar.setVisible(sichtbar);
        toolBar.setManaged(sichtbar);
    }
    
    @FXML
    private void onVollbild() {
        Stage stage = (Stage) mainTabPane.getScene().getWindow();
        stage.setFullScreen(!stage.isFullScreen());
    }
    
    @FXML
    private void onDatenbankInfo() {
        try {
            String info = DatabaseManager.getDatabaseInfo();
            
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Datenbank-Informationen");
            alert.setHeaderText("SQLite Datenbank Status");
            alert.setContentText(info);
            alert.getDialogPane().setPrefWidth(400);
            alert.showAndWait();
            
        } catch (SQLException e) {
            logger.error("Fehler beim Laden der Datenbank-Info", e);
            showError("Fehler", "Datenbank-Informationen konnten nicht geladen werden.", e);
        }
    }
    
    @FXML
    private void onTestdatenErstellen() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Testdaten erstellen");
        confirm.setHeaderText("Möchten Sie Testdaten erstellen?");
        confirm.setContentText("Es werden Beispiel-Personen erstellt.");
        
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                DatabaseTest.createTestData();
                updateDashboard();
                addAktivitaet("🧪 Testdaten erstellt");
                setHauptStatus("Testdaten wurden erfolgreich erstellt");
                showInfo("Erfolgreich", "Testdaten wurden erstellt. Wechseln Sie zur Personenverwaltung um sie zu sehen.");
                
            } catch (SQLException e) {
                logger.error("Fehler beim Erstellen der Testdaten", e);
                showError("Fehler", "Testdaten konnten nicht erstellt werden.", e);
            }
        }
    }
    
    @FXML
    private void onDatenbankBackup() {
        showInfo("Backup", "Backup-Funktion wird in einer zukünftigen Version implementiert.");
    }
    
    @FXML
    private void onLogAnzeigen() {
        showInfo("Log", "Log-Anzeige wird in einer zukünftigen Version implementiert.");
    }
    
    @FXML
    private void onHilfe() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Hilfe");
        alert.setHeaderText("Dienstplan-Generator - Hilfe");
        alert.setContentText("""
            🏥 Dienstplan-Generator v1.0.0
            
            📋 Verwendung:
            1. Personen in der Personenverwaltung anlegen
            2. Arbeitszeiten und Dienstarten konfigurieren
            3. Monatswünsche per Excel importieren
            4. Dienstplan automatisch generieren lassen
            5. Manuell nachbearbeiten falls nötig

            💡 Tipps:
            • Mindestens 3-4 Personen für optimale Verteilung
            • Excel-Vorlage für Wünsche herunterladen
            • Verschiedene Dienstarten pro Person aktivieren
            
            🔧 Bei Problemen:
            Prüfen Sie die Konsole auf Fehlermeldungen oder
            erstellen Sie Testdaten über das Extras-Menü.
            """);
        alert.getDialogPane().setPrefWidth(500);
        alert.showAndWait();
    }
    
    @FXML
    private void onTastaturkuerzel() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Tastaturkürzel");
        alert.setHeaderText("Verfügbare Tastaturkürzel");
        alert.setContentText("""
            ⌨️ Allgemein:
            Ctrl+N  - Neue Person
            Ctrl+D  - Neuer Dienstplan
            Ctrl+S  - Speichern
            Ctrl+F  - Suchen
            F11     - Vollbild
            
            🔄 Navigation:
            Ctrl+1  - Dashboard
            Ctrl+2  - Personenverwaltung  
            Ctrl+3  - Dienstplanerstellung
            Ctrl+4  - Statistiken
            
            📅 Dienstplan:
            Ctrl+G  - Generieren
            Ctrl+R  - Zurücksetzen
            
            (Tastaturkürzel werden in einer zukünftigen Version implementiert)
            """);
        alert.getDialogPane().setPrefWidth(400);
        alert.showAndWait();
    }
    
    @FXML
    private void onUeber() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Über Dienstplan-Generator");
        alert.setHeaderText("🏥 Dienstplan-Generator");
        alert.setContentText("""
            Version: 1.0.0-BETA
            
            📋 Automatische Dienstplanerstellung für medizinische
            Einrichtungen mit intelligenter Personalverteilung.
            
            🛠️ Technologie:
            • Java 17 + JavaFX
            • SQLite Datenbank
            • Backtracking-Algorithmus
            • Maven Build System
            
            ⚖️ Features:
            • Faire Dienstverteilung
            • Excel-Import für Wünsche
            • Automatische Konfliktlösung
            • Manuelle Nachbearbeitung
            
            👨‍💻 Entwickelt für optimale Arbeitsplatz-Fairness
            und automatisierte Dienstplanverwaltung.
            """);
        alert.getDialogPane().setPrefWidth(500);
        alert.showAndWait();
    }
    
    // Toolbar Event Handler
    
    @FXML
    private void onSchnellGenerierung() {
        try {
            List<Person> personen = personDAO.findAll();
            if (personen.isEmpty()) {
                showWarning("Keine Personen", 
                    "Es sind keine Personen vorhanden. Bitte erstellen Sie zuerst Personen in der Personenverwaltung.");
                return;
            }
            
            Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
            dialog.setTitle("Schnell-Generierung");
            dialog.setHeaderText("Dienstplan für aktuellen Monat generieren?");
            dialog.setContentText("Ein neuer Dienstplan wird automatisch erstellt.");
            
            if (dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                // Zur Dienstplanerstellung wechseln und Generierung starten
                mainTabPane.getSelectionModel().select(dienstplanerstellungTab);
                // TODO: Trigger Generierung im DienstplanerstellungController
                addAktivitaet("🚀 Schnell-Generierung gestartet");
                setHauptStatus("Generiere Dienstplan...");
            }
            
        } catch (SQLException e) {
            logger.error("Fehler bei Schnell-Generierung", e);
            showError("Fehler", "Schnell-Generierung fehlgeschlagen.", e);
        }
    }
    
    @FXML
    private void onStatistikenAnzeigen() {
        mainTabPane.getSelectionModel().select(statistikenTab);
        addAktivitaet("📊 Statistiken angezeigt");
    }
    
    @FXML
    private void onGlobalSuche() {
        String suchtext = globalSuchfeld.getText().trim();
        if (suchtext.isEmpty()) {
            return;
        }
        
        // TODO: Implementiere globale Suche
        addAktivitaet("🔍 Gesucht: " + suchtext);
        showInfo("Suche", "Globale Suche wird in einer zukünftigen Version implementiert.\n\nGesucht wurde: " + suchtext);
    }
    
    // Dashboard Navigation Handler
    
    @FXML
    private void onPersonenverwaltungOeffnen() {
        mainTabPane.getSelectionModel().select(personenverwaltungTab);
        addAktivitaet("👥 Personenverwaltung geöffnet");
    }
    
    @FXML
    private void onDienstplanerstellungOeffnen() {
        mainTabPane.getSelectionModel().select(dienstplanerstellungTab);
        addAktivitaet("📅 Dienstplanerstellung geöffnet");
    }
    
    @FXML
    private void onStatistikenOeffnen() {
        mainTabPane.getSelectionModel().select(statistikenTab);
        addAktivitaet("📊 Statistiken geöffnet");
    }
    
    // Helper Methods
    
    private void onTabWechsel(Tab neuerTab) {
        String tabName = neuerTab.getText();
        setHauptStatus("Aktiv: " + tabName);
        
        // Tab-spezifische Aktionen
        if (neuerTab == dashboardTab) {
            updateDashboard();
        }
    }
    
    private void updateDashboard() {
        try {
            // Personen zählen
            int personenAnzahl = personDAO.count();
            dashboardPersonenAnzahl.setText(String.valueOf(personenAnzahl));
            personenStatusLabel.setText(personenAnzahl + " Person" + (personenAnzahl != 1 ? "en" : ""));
            
            // Dienstpläne zählen
            int dienstplaeneAnzahl = dienstplanDAO.count();
            dashboardDienstplaeneAnzahl.setText(String.valueOf(dienstplaeneAnzahl));
            dienstplaeneStatusLabel.setText(dienstplaeneAnzahl + " Dienstplan" + (dienstplaeneAnzahl != 1 ? "pläne" : ""));
            
            // Dienste zählen
            int diensteAnzahl = dienstDAO.count();
            dashboardDiensteAnzahl.setText(String.valueOf(diensteAnzahl));
            
            // Aufgaben aktualisieren
            updateAufgaben(personenAnzahl);
            
        } catch (SQLException e) {
            logger.error("Fehler beim Dashboard-Update", e);
            dashboardPersonenAnzahl.setText("?");
            dashboardDienstplaeneAnzahl.setText("?");
            dashboardDiensteAnzahl.setText("?");
        }
    }
    
    private void updateAufgaben(int personenAnzahl) {
        aufgabenListe.clear();
        
        if (personenAnzahl == 0) {
            aufgabenListe.add("❗ Keine Personen vorhanden");
            aufgabenListe.add("💡 Erstellen Sie zuerst Mitarbeiter");
        } else if (personenAnzahl < 3) {
            aufgabenListe.add("⚠️ Wenige Personen für optimale Planung");
            aufgabenListe.add("💡 Mindestens 3-4 Personen empfohlen");
        }
        
        // TODO: MonatsWunsch-Prüfung für aktuellen Monat hinzufügen (Phase 3)

        if (aufgabenListe.isEmpty()) {
            aufgabenListe.add("✅ System bereit für Dienstplan-Generierung");
            aufgabenListe.add("🚀 Alle Grunddaten vollständig");
        }
    }
    
    private void updateDatenbankInfo() {
        try {
            String info = DatabaseManager.getDatabaseInfo();
            // Extrahiere Größe aus der Info
            String[] lines = info.split("\n");
            for (String line : lines) {
                if (line.startsWith("Datenbankgröße:")) {
                    datenbankGroesseLabel.setText(line.substring(15).trim());
                    break;
                }
            }
        } catch (SQLException e) {
            datenbankGroesseLabel.setText("Unbekannt");
        }
    }
    
    private void loadAktivitaeten() {
        addAktivitaet("🚀 Anwendung gestartet");
        addAktivitaet("💾 Datenbank initialisiert");
    }
    
    private void addAktivitaet(String aktivitaet) {
        String zeitstempel = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        aktivitaetenListe.add(0, zeitstempel + " - " + aktivitaet);
        
        // Nur die letzten 20 Aktivitäten behalten
        while (aktivitaetenListe.size() > 20) {
            aktivitaetenListe.remove(aktivitaetenListe.size() - 1);
        }
    }
    
    private void updateUhrzeit() {
        String zeit = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
        uhrzeitLabel.setText(zeit);
    }
    
    private void setHauptStatus(String message) {
        hauptStatusLabel.setText(message);
        logger.debug("Status: {}", message);
    }
    
    private void showError(String title, String message, Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(message);
        alert.setContentText(e != null ? e.getMessage() : "Unbekannter Fehler");
        alert.showAndWait();
    }
    
    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(message);
        alert.showAndWait();
    }
    
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(message);
        alert.showAndWait();
    }
    
    public void shutdown() {
        if (uhrzeitTimer != null) {
            uhrzeitTimer.stop();
        }
        if (dashboardUpdateTimer != null) {
            dashboardUpdateTimer.stop();
        }
        logger.info("Hauptfenster wird beendet");
    }
}