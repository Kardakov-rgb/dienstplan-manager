package de.dienstplan;

import de.dienstplan.controller.HauptfensterController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hauptklasse der Dienstplan-Generator Anwendung
 */
public class Main extends Application {
    
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private HauptfensterController hauptfensterController;
    
    @Override
    public void start(Stage primaryStage) {
        try {
            logger.info("Starte Dienstplan-Generator...");
            
            // FXML laden
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/fxml/hauptfenster.fxml"));
            
            // Scene erstellen
            Scene scene = new Scene(loader.load(), 1400, 900);

            // Controller für Shutdown speichern
            hauptfensterController = loader.getController();

            // CSS-Stylesheet hinzufügen
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            
            // Stage konfigurieren
            primaryStage.setTitle("🏥 Dienstplan-Generator v1.0.0 - Automatische Dienstplanerstellung");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);
            
            // Optional: Icon setzen
            try {
                primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/icons/app-icon.png")));
            } catch (Exception e) {
                logger.debug("App-Icon konnte nicht geladen werden: {}", e.getMessage());
            }

            // Window-Close Event
            primaryStage.setOnCloseRequest(e -> {
                logger.info("Anwendung wird beendet..." );
                if (hauptfensterController != null) {
                    hauptfensterController.shutdown();
                }
            });
            
            // Zentriert starten
            primaryStage.centerOnScreen();
            primaryStage.show();
            
            logger.info("Dienstplan-Generator erfolgreich gestartet");
            
        } catch (Exception e) {
            logger.error("Fehler beim Starten der Anwendung", e);
            
            // Fehler-Dialog anzeigen
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Anwendungsfehler");
            alert.setHeaderText("Die Anwendung konnte nicht gestartet werden.");
            alert.setContentText("Fehler: " + e.getMessage() + "\n\nBitte prüfen Sie die Konsole für weitere Details.");
            alert.showAndWait();
            
            System.exit(1);
        }
    }
    
    @Override
    public void stop() throws Exception {
        logger.info("Anwendung wird gestoppt...");
        if (hauptfensterController != null) {
            hauptfensterController.shutdown();
        }
        super.stop();
    }
    
    /**
     * Hauptmethode - Einstiegspunkt der Anwendung
     */
    public static void main(String[] args) {
        logger.info("=== 🏥 Dienstplan-Generator wird gestartet ===");
        logger.info("Java Version: {}", System.getProperty("java.version"));
        logger.info("JavaFX Version: {}", System.getProperty("javafx.version"));
        
        // JavaFX Anwendung starten
        launch(args);
        
        logger.info("=== Anwendung beendet ===");
    }
}