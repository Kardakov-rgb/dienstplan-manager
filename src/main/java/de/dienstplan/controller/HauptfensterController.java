package de.dienstplan.controller;

import de.dienstplan.database.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.sql.SQLException;
import java.util.ResourceBundle;

/**
 * Controller für das Hauptfenster - vereinfachte Version
 */
public class HauptfensterController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(HauptfensterController.class);

    // FXML Controls - Basis
    @FXML private MenuBar menuBar;
    @FXML private ToolBar toolBar;

    // FXML Controls - Tabs
    @FXML private TabPane mainTabPane;
    @FXML private Tab dashboardTab;
    @FXML private Tab personenverwaltungTab;
    @FXML private Tab dienstplanerstellungTab;

    // FXML Controls - Dashboard
    @FXML private Label dashboardPersonenAnzahl;
    @FXML private Label dashboardDienstplaeneAnzahl;
    @FXML private Label hinweisLabel;
    @FXML private VBox hinweiseBox;

    // FXML Controls - Status
    @FXML private HBox statusLeiste;
    @FXML private Label hauptStatusLabel;

    // Services
    private final PersonDAO personDAO;
    private final DienstplanDAO dienstplanDAO;

    // Timer
    private Timeline dashboardUpdateTimer;

    public HauptfensterController() {
        this.personDAO = new PersonDAO();
        this.dienstplanDAO = new DienstplanDAO();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initialisiere Hauptfenster...");

        try {
            initializeDatabase();
            initializeTimers();
            updateDashboard();

            setHauptStatus("Bereit");
            logger.info("Hauptfenster erfolgreich initialisiert");

        } catch (Exception e) {
            logger.error("Fehler beim Initialisieren des Hauptfensters", e);
            setHauptStatus("Fehler beim Laden");
            showError("Initialisierungsfehler", "Das Hauptfenster konnte nicht vollständig initialisiert werden.", e);
        }
    }

    private void initializeDatabase() throws SQLException {
        DatabaseManager.initializeDatabase();
    }

    private void initializeTimers() {
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
    private void onBeenden() {
        Platform.exit();
    }

    @FXML
    private void onHilfe() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Hilfe");
        alert.setHeaderText("Dienstplan-Generator");
        alert.setContentText("""
            So erstellen Sie einen Dienstplan:

            1. Personen anlegen
               Legen Sie alle Mitarbeiter mit ihren
               Arbeitstagen und Dienstarten an.

            2. Dienstplan erstellen
               - Monat und Jahr auswaehlen
               - Optional: Wuensche per Excel importieren
               - Dienstplan automatisch generieren
               - Bei Bedarf manuell anpassen
               - Als Excel exportieren
            """);
        alert.getDialogPane().setPrefWidth(400);
        alert.showAndWait();
    }

    @FXML
    private void onUeber() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Ueber");
        alert.setHeaderText("Dienstplan-Generator");
        alert.setContentText("Version 1.0.0\n\nAutomatische Dienstplanerstellung mit fairer Verteilung.");
        alert.showAndWait();
    }

    // Toolbar Event Handler

    @FXML
    private void onNeuePersonHinzufuegen() {
        mainTabPane.getSelectionModel().select(personenverwaltungTab);
    }

    @FXML
    private void onNeuerDienstplan() {
        mainTabPane.getSelectionModel().select(dienstplanerstellungTab);
    }

    // Dashboard Navigation Handler

    @FXML
    private void onPersonenverwaltungOeffnen() {
        mainTabPane.getSelectionModel().select(personenverwaltungTab);
    }

    @FXML
    private void onDienstplanerstellungOeffnen() {
        mainTabPane.getSelectionModel().select(dienstplanerstellungTab);
    }

    // Helper Methods

    private void updateDashboard() {
        try {
            int personenAnzahl = personDAO.count();
            int dienstplaeneAnzahl = dienstplanDAO.count();

            // Dashboard-Labels aktualisieren
            dashboardPersonenAnzahl.setText(personenAnzahl + " Personen angelegt");
            dashboardDienstplaeneAnzahl.setText(dienstplaeneAnzahl + " Dienstplaene vorhanden");

            // Hinweis aktualisieren
            updateHinweis(personenAnzahl);

        } catch (SQLException e) {
            logger.error("Fehler beim Dashboard-Update", e);
            dashboardPersonenAnzahl.setText("? Personen");
            dashboardDienstplaeneAnzahl.setText("? Dienstplaene");
        }
    }

    private void updateHinweis(int personenAnzahl) {
        if (personenAnzahl == 0) {
            hinweisLabel.setText("Legen Sie zuerst Personen an, um einen Dienstplan zu erstellen.");
            hinweisLabel.setStyle("-fx-text-fill: #e65100; -fx-font-size: 14px;");
        } else if (personenAnzahl < 3) {
            hinweisLabel.setText("Empfehlung: Mindestens 3 Personen fuer eine optimale Verteilung.");
            hinweisLabel.setStyle("-fx-text-fill: #f57c00; -fx-font-size: 14px;");
        } else {
            hinweisLabel.setText("Alles bereit! Sie koennen jetzt einen Dienstplan erstellen.");
            hinweisLabel.setStyle("-fx-text-fill: #388e3c; -fx-font-size: 14px;");
        }
    }

    private void setHauptStatus(String message) {
        hauptStatusLabel.setText(message);
    }

    private void showError(String title, String message, Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(message);
        alert.setContentText(e != null ? e.getMessage() : "Unbekannter Fehler");
        alert.showAndWait();
    }

    public void shutdown() {
        if (dashboardUpdateTimer != null) {
            dashboardUpdateTimer.stop();
        }
        logger.info("Hauptfenster wird beendet");
    }
}
