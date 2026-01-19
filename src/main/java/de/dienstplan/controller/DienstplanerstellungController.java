package de.dienstplan.controller;

import de.dienstplan.algorithm.DienstplanGenerator;
import de.dienstplan.database.*;
import de.dienstplan.model.*;
import de.dienstplan.service.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller für die Dienstplanerstellung mit Kalender-Ansicht
 */
public class DienstplanerstellungController implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(DienstplanerstellungController.class);
    
    // FXML Controls - Konfiguration
    @FXML private ComboBox<String> monatComboBox;
    @FXML private ComboBox<Integer> jahrComboBox;
    @FXML private TextField dienstplanNameField;
    @FXML private Button generiereButton;
    @FXML private Button speichernButton;
    @FXML private Button exportButton;
    @FXML private Button excelVorlageButton;
    @FXML private Button excelImportButton;
    
    // FXML Controls - Status
    @FXML private Label statusLabel;
    @FXML private Label statistikLabel;
    @FXML private VBox warnungenPanel;
    @FXML private ListView<String> warnungenList;
    
    // FXML Controls - Kalender
    @FXML private Button vorherigerMonatButton;
    @FXML private Button naechsterMonatButton;
    @FXML private Label kalenderTitelLabel;
    @FXML private ComboBox<String> ansichtComboBox;
    @FXML private GridPane kalenderGrid;
    
    // FXML Controls - Details
    @FXML private Label detailsTitel;
    @FXML private VBox dienstDetailsPanel;
    @FXML private Label dienstDatumLabel;
    @FXML private Label dienstArtLabel;
    @FXML private ComboBox<Person> dienstPersonComboBox;
    @FXML private ComboBox<DienstStatus> dienstStatusComboBox;
    @FXML private TextArea dienstBemerkungArea;
    @FXML private Button dienstSpeichernButton;
    @FXML private Button dienstZuruecksetzenButton;
    @FXML private Separator detailsSeparator;
    
    // FXML Controls - Statistiken
    @FXML private ProgressBar zuweisungsgradBar;
    @FXML private Label gesamtDiensteLabel;
    @FXML private Label zugewiesenLabel;
    @FXML private Label offenLabel;
    @FXML private TableView<PersonStatistik> personStatistikTabelle;
    @FXML private TableColumn<PersonStatistik, String> personNameColumn;
    @FXML private TableColumn<PersonStatistik, Integer> personAnzahlColumn;
    @FXML private TableColumn<PersonStatistik, Integer> person24hColumn;
    @FXML private TableColumn<PersonStatistik, Integer> personVistenColumn;
    @FXML private TableColumn<PersonStatistik, Integer> personDaVinciColumn;

    // FXML Controls - Wunsch-Info und Statistik
    @FXML private HBox wunschInfoPanel;
    @FXML private Label wunschInfoLabel;
    @FXML private VBox wunscherfuellungPanel;
    @FXML private Label freiwunschStatLabel;
    @FXML private Label dienstwunschStatLabel;
    @FXML private Label gesamtQuoteLabel;
    @FXML private Button wunschDetailsButton;
    
    // FXML Controls - Aktionen
    @FXML private Button alleBestaetigenButton;
    @FXML private Button offeneAnzeigenButton;
    @FXML private Button konflikteFindenButton;
    @FXML private Button dienstplanLeeren;
    
    // FXML Controls - Footer
    @FXML private Label bottomStatusLabel;
    @FXML private Label letzteAktualisierungLabel;
    
    // Services und Daten
    private final PersonDAO personDAO;
    private final DienstplanDAO dienstplanDAO;
    private final DienstDAO dienstDAO;
    private final DienstplanService dienstplanService;
    private final CommandManager commandManager;
    private final MonatsWunschDAO monatsWunschDAO;
    private final FairnessHistorieDAO fairnessHistorieDAO;

    private List<Person> verfuegbarePersonen;
    private Dienstplan aktuellerDienstplan;
    private YearMonth aktuellerMonat;
    private Dienst ausgewaehlterDienst;

    // MonatsWunsch Daten
    private List<MonatsWunsch> aktuelleWuensche = new ArrayList<>();
    private Map<Long, WunschStatistik> aktuelleWunschStatistiken;

    // UI-Datenstrukturen
    private final ObservableList<PersonStatistik> personStatistikListe = FXCollections.observableArrayList();
    private Map<LocalDate, List<Dienst>> tagesDienste = new HashMap<>();

    // Flag um Listener-Konflikte bei programmatischen ComboBox-Updates zu vermeiden
    private boolean programmaticUpdate = false;

    // UI-Konstanten (statt Emojis im Code)
    private static final String ICON_SUCCESS = "[OK]";
    private static final String ICON_WARNING = "[!]";

    public DienstplanerstellungController() {
        this.personDAO = new PersonDAO();
        this.dienstplanDAO = new DienstplanDAO();
        this.dienstDAO = new DienstDAO();
        this.dienstplanService = new DienstplanService(personDAO, dienstplanDAO);
        this.commandManager = new CommandManager();
        this.monatsWunschDAO = new MonatsWunschDAO();
        this.fairnessHistorieDAO = new FairnessHistorieDAO();
        this.aktuellerMonat = YearMonth.now();
    }

    /**
     * Konstruktor mit Dependency Injection (für Tests)
     */
    public DienstplanerstellungController(PersonDAO personDAO, DienstplanDAO dienstplanDAO,
                                          DienstDAO dienstDAO, DienstplanService dienstplanService) {
        this.personDAO = personDAO;
        this.dienstplanDAO = dienstplanDAO;
        this.dienstDAO = dienstDAO;
        this.dienstplanService = dienstplanService;
        this.commandManager = new CommandManager();
        this.monatsWunschDAO = new MonatsWunschDAO();
        this.fairnessHistorieDAO = new FairnessHistorieDAO();
        this.aktuellerMonat = YearMonth.now();
    }
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initialisiere Dienstplanerstellung...");

        try {
            initializeDatabase();
            initializeComponents();
            loadPersonen();
            ladeExistierendenDienstplan();
            updateKalender();

            setStatus("Dienstplanerstellung bereit");
            logger.info("Dienstplanerstellung erfolgreich initialisiert");

        } catch (Exception e) {
            logger.error("Fehler beim Initialisieren der Dienstplanerstellung", e);
            setStatus("Fehler beim Laden: " + e.getMessage());
            showError("Initialisierungsfehler", "Die Dienstplanerstellung konnte nicht gestartet werden.", e);
        }
    }
    
    private void initializeDatabase() throws SQLException {
        DatabaseManager.initializeDatabase();
    }
    
    private void initializeComponents() {
        // Monat ComboBox
        String[] monate = {"Januar", "Februar", "März", "April", "Mai", "Juni",
                          "Juli", "August", "September", "Oktober", "November", "Dezember"};
        monatComboBox.getItems().addAll(monate);
        monatComboBox.setValue(monate[aktuellerMonat.getMonthValue() - 1]);
        
        // Jahr ComboBox
        int aktuellesJahr = aktuellerMonat.getYear();
        for (int jahr = aktuellesJahr - 1; jahr <= aktuellesJahr + 3; jahr++) {
            jahrComboBox.getItems().add(jahr);
        }
        jahrComboBox.setValue(aktuellesJahr);
        
        // Ansicht ComboBox
        ansichtComboBox.getItems().addAll("Monatsansicht", "3-Monats-Ansicht");
        ansichtComboBox.setValue("Monatsansicht");
        
        // Dienstplan Name automatisch setzen
        updateDienstplanName();
        
        // Event Listeners - nur reagieren wenn keine programmatische Änderung
        monatComboBox.valueProperty().addListener((obs, old, neu) -> {
            if (neu != null && !programmaticUpdate) {
                updateAktuellerMonat();
                updateDienstplanName();
                ladeExistierendenDienstplan();
                updateKalender();
            }
        });

        jahrComboBox.valueProperty().addListener((obs, old, neu) -> {
            if (neu != null && !programmaticUpdate) {
                updateAktuellerMonat();
                updateDienstplanName();
                ladeExistierendenDienstplan();
                updateKalender();
            }
        });
        
        // Person ComboBox konfigurieren
        dienstPersonComboBox.setCellFactory(param -> new ListCell<Person>() {
            @Override
            protected void updateItem(Person person, boolean empty) {
                super.updateItem(person, empty);
                if (empty || person == null) {
                    setText(null);
                } else {
                    setText(person.getName());
                }
            }
        });
        
        dienstPersonComboBox.setButtonCell(new ListCell<Person>() {
            @Override
            protected void updateItem(Person person, boolean empty) {
                super.updateItem(person, empty);
                if (empty || person == null) {
                    setText("Keine Zuweisung");
                } else {
                    setText(person.getName());
                }
            }
        });
        
        // Status ComboBox
        dienstStatusComboBox.getItems().addAll(DienstStatus.values());
        
        // Statistik Tabelle konfigurieren
        initializeStatistikTabelle();
    }
    
    private void initializeStatistikTabelle() {
        personNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        personAnzahlColumn.setCellValueFactory(new PropertyValueFactory<>("gesamtDienste"));
        person24hColumn.setCellValueFactory(new PropertyValueFactory<>("dienst24h"));
        personVistenColumn.setCellValueFactory(new PropertyValueFactory<>("visten"));
        personDaVinciColumn.setCellValueFactory(new PropertyValueFactory<>("davinci"));
        
        personStatistikTabelle.setItems(personStatistikListe);
    }
    
    private void loadPersonen() throws SQLException {
        verfuegbarePersonen = personDAO.findAll();

        // Person ComboBox aktualisieren
        dienstPersonComboBox.getItems().clear();
        dienstPersonComboBox.getItems().add(null); // "Keine Zuweisung" Option
        dienstPersonComboBox.getItems().addAll(verfuegbarePersonen);

        logger.info("Personen geladen: {}", verfuegbarePersonen.size());
    }

    /**
     * Lädt einen existierenden Dienstplan für den aktuellen Monat aus der Datenbank.
     * Es wird immer nur ein Dienstplan pro Monat unterstützt.
     */
    private void ladeExistierendenDienstplan() {
        try {
            List<Dienstplan> dienstplaene = dienstplanDAO.findByMonat(aktuellerMonat);

            if (!dienstplaene.isEmpty()) {
                // Es gibt einen Dienstplan für diesen Monat - lade den ersten (und einzigen)
                aktuellerDienstplan = dienstplaene.get(0);

                // Name im Textfeld setzen
                dienstplanNameField.setText(aktuellerDienstplan.getName());

                // Tages-Dienste Map aktualisieren
                updateTagesDiensteMap();

                // Statistiken aktualisieren
                updateStatistiken();

                // Buttons aktivieren
                speichernButton.setDisable(false);
                if (exportButton != null) {
                    exportButton.setDisable(false);
                }

                logger.info("Existierenden Dienstplan geladen: {} für {}",
                    aktuellerDienstplan.getName(), aktuellerMonat);
                setStatus("Dienstplan geladen: " + aktuellerDienstplan.getName());
                bottomStatusLabel.setText("Dienstplan: " + aktuellerDienstplan.getName());

            } else {
                // Kein Dienstplan vorhanden - UI zurücksetzen
                clearAktuellerDienstplan();
                logger.info("Kein Dienstplan für {} vorhanden", aktuellerMonat);
            }

        } catch (SQLException e) {
            logger.error("Fehler beim Laden des Dienstplans für {}", aktuellerMonat, e);
            clearAktuellerDienstplan();
        }
    }
    
    // Event Handler
    
    @FXML
    private void onDienstplanGenerieren() {
        if (verfuegbarePersonen.isEmpty()) {
            showWarning("Keine Personen", "Es sind keine Personen in der Personenverwaltung vorhanden.");
            return;
        }

        // UI für Generierung vorbereiten
        generiereButton.setDisable(true);
        setStatus("Generiere Dienstplan...");
        zuweisungsgradBar.setProgress(0);

        // Thread-safe: Defensive Kopie der Personenliste und des Monats erstellen
        final List<Person> personenKopie = new ArrayList<>(verfuegbarePersonen);
        final YearMonth monatKopie = aktuellerMonat;

        // MonatsWünsche und Fairness-Daten laden
        final List<MonatsWunsch> wuenscheKopie = new ArrayList<>(aktuelleWuensche);
        final Map<Long, FairnessScore> fairnessScores = new HashMap<>();
        try {
            List<FairnessScore> scores = fairnessHistorieDAO.calculateAllFairnessScores();
            for (FairnessScore score : scores) {
                fairnessScores.put(score.getPersonId(), score);
            }
        } catch (SQLException e) {
            logger.warn("Konnte Fairness-Scores nicht laden: {}", e.getMessage());
        }

        // Generierung in Background-Thread
        Task<DienstplanGenerator.DienstplanGenerierungResult> task = new Task<>() {
            @Override
            protected DienstplanGenerator.DienstplanGenerierungResult call() {
                DienstplanGenerator generator = new DienstplanGenerator(
                    personenKopie, monatKopie, wuenscheKopie, fairnessScores);

                // Fortschritts-Callback setzen
                generator.setProgressCallback(progress -> {
                    Platform.runLater(() -> {
                        updateProgress(progress, 1.0);
                        zuweisungsgradBar.setProgress(progress);
                        setStatus(String.format("Generiere Dienstplan... %.0f%%", progress * 100));
                    });
                });

                return generator.generiereDienstplan();
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    DienstplanGenerator.DienstplanGenerierungResult result = getValue();
                    onGenerierungAbgeschlossen(result);
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    zuweisungsgradBar.progressProperty().unbind();
                    logger.error("Generierung fehlgeschlagen", getException());
                    setStatus("Generierung fehlgeschlagen: " + getException().getMessage());
                    generiereButton.setDisable(false);
                    zuweisungsgradBar.setProgress(0);
                });
            }
        };

        // Progress-Property binden
        zuweisungsgradBar.progressProperty().bind(task.progressProperty());

        Thread generierungsThread = new Thread(task);
        generierungsThread.setDaemon(true);
        generierungsThread.setName("Dienstplan-Generator");
        generierungsThread.start();
    }
    
    private void onGenerierungAbgeschlossen(DienstplanGenerator.DienstplanGenerierungResult result) {
        // ProgressBar-Bindung entfernen, damit sie wieder manuell gesetzt werden kann
        zuweisungsgradBar.progressProperty().unbind();

        try {
            aktuellerDienstplan = result.getDienstplan();

            if (aktuellerDienstplan != null) {
                // Dienstplan Name setzen falls leer
                if (aktuellerDienstplan.getName() == null || aktuellerDienstplan.getName().trim().isEmpty()) {
                    aktuellerDienstplan.setName(dienstplanNameField.getText());
                }

                // Tage-Dienste Map für Kalender erstellen
                updateTagesDiensteMap();

                // UI aktualisieren
                updateKalender();
                updateStatistiken();

                // Wunschstatistiken speichern und anzeigen
                aktuelleWunschStatistiken = result.getWunschStatistiken();
                updateWunschStatistikAnzeige();

                // Warnungen anzeigen falls vorhanden
                if (result.hatWarnungen()) {
                    showWarnungen(result.getWarnungen());
                }

                setStatus(result.getZusammenfassung());
                speichernButton.setDisable(false);
                if (exportButton != null) {
                    exportButton.setDisable(false);
                }

                logger.info("Dienstplan erfolgreich generiert: {}", result.getZusammenfassung());

            } else {
                setStatus("Generierung fehlgeschlagen - kein Dienstplan erstellt");
            }
            
        } catch (Exception e) {
            logger.error("Fehler beim Verarbeiten des generierten Dienstplans", e);
            setStatus("Fehler beim Verarbeiten: " + e.getMessage());
        }
        
        generiereButton.setDisable(false);
    }
    
    @FXML
    private void onDienstplanSpeichern() {
        if (aktuellerDienstplan == null) {
            showWarning("Kein Dienstplan", "Es ist kein Dienstplan zum Speichern vorhanden.");
            return;
        }

        try {
            // Name aus UI übernehmen
            String name = dienstplanNameField.getText().trim();
            if (name.isEmpty()) {
                showWarning("Name fehlt", "Bitte geben Sie einen Namen für den Dienstplan ein.");
                return;
            }

            aktuellerDienstplan.setName(name);

            // Prüfen ob bereits ein Dienstplan für diesen Monat existiert
            List<Dienstplan> existierendeDienstplaene = dienstplanDAO.findByMonat(aktuellerMonat);

            if (!existierendeDienstplaene.isEmpty()) {
                // Es existiert bereits ein Dienstplan - automatisch überschreiben
                Dienstplan existierenderPlan = existierendeDienstplaene.get(0);
                aktuellerDienstplan.setId(existierenderPlan.getId());
                dienstplanDAO.update(aktuellerDienstplan);
                setStatus("Dienstplan aktualisiert: " + name);
                logger.info("Dienstplan aktualisiert: {} (ID: {})", name, aktuellerDienstplan.getId());
            } else {
                // Neuen Plan erstellen
                aktuellerDienstplan = dienstplanDAO.create(aktuellerDienstplan);
                setStatus("Dienstplan gespeichert: " + name);
                logger.info("Neuer Dienstplan erstellt: {} (ID: {})", name, aktuellerDienstplan.getId());
            }

            updateLetzteAktualisierung();
            showInfo("Erfolgreich gespeichert", "Der Dienstplan wurde erfolgreich gespeichert.");

        } catch (SQLException e) {
            logger.error("Fehler beim Speichern des Dienstplans", e);
            showError("Speicherfehler", "Der Dienstplan konnte nicht gespeichert werden.", e);
        }
    }
    
    @FXML
    private void onVorherigerMonat() {
        aktuellerMonat = aktuellerMonat.minusMonths(1);
        updateMonatJahrComboBoxes();
        updateDienstplanName();
        ladeExistierendenDienstplan();
        updateKalender();
    }

    @FXML
    private void onNaechsterMonat() {
        aktuellerMonat = aktuellerMonat.plusMonths(1);
        updateMonatJahrComboBoxes();
        updateDienstplanName();
        ladeExistierendenDienstplan();
        updateKalender();
    }
    
    @FXML
    private void onAnsichtWechsel() {
        // TODO: 3-Monats-Ansicht implementieren
        updateKalender();
    }
    
    @FXML
    private void onPersonZuweisungChange() {
        if (ausgewaehlterDienst != null) {
            Person neuePerson = dienstPersonComboBox.getValue();
            
            if (neuePerson != null) {
                ausgewaehlterDienst.zuweisen(neuePerson);
                ausgewaehlterDienst.setStatus(DienstStatus.GEPLANT);
            } else {
                ausgewaehlterDienst.zuweisingEntfernen();
                ausgewaehlterDienst.setStatus(DienstStatus.GEPLANT);
            }
            
            dienstStatusComboBox.setValue(ausgewaehlterDienst.getStatus());
            updateKalender();
            updateStatistiken();
        }
    }
    
    @FXML
    private void onStatusChange() {
        if (ausgewaehlterDienst != null) {
            DienstStatus neuerStatus = dienstStatusComboBox.getValue();
            if (neuerStatus != null) {
                ausgewaehlterDienst.setStatus(neuerStatus);
                updateKalender();
            }
        }
    }
    
    @FXML
    private void onDienstSpeichern() {
        if (ausgewaehlterDienst != null && aktuellerDienstplan != null && aktuellerDienstplan.getId() != null) {
            try {
                // Bemerkung übernehmen
                ausgewaehlterDienst.setBemerkung(dienstBemerkungArea.getText().trim());
                
                // In Datenbank speichern
                dienstDAO.update(ausgewaehlterDienst);
                
                // UI aktualisieren
                updateKalender();
                updateStatistiken();
                
                setStatus("Dienst gespeichert: " + ausgewaehlterDienst.getDatum() + " " + 
                         ausgewaehlterDienst.getArt().getKurzName());
                
            } catch (SQLException e) {
                logger.error("Fehler beim Speichern des Dienstes", e);
                showError("Speicherfehler", "Der Dienst konnte nicht gespeichert werden.", e);
            }
        } else {
            showWarning("Nicht speicherbar", "Der Dienstplan muss erst gespeichert werden, bevor einzelne Dienste geändert werden können.");
        }
    }
    
    @FXML
    private void onDienstZuruecksetzen() {
        if (ausgewaehlterDienst != null) {
            loadDienstDetails(ausgewaehlterDienst);
        }
    }
    
    @FXML
    private void onAlleDiensteBestaetigen() {
        if (aktuellerDienstplan != null) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Alle Dienste bestätigen");
            confirm.setHeaderText("Möchten Sie alle zugewiesenen Dienste als bestätigt markieren?");
            confirm.setContentText("Diese Aktion kann nicht rückgängig gemacht werden.");
            
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                
                aktuellerDienstplan.getDienste().stream()
                    .filter(Dienst::istZugewiesen)
                    .forEach(dienst -> dienst.setStatus(DienstStatus.BESTAETIGT));
                
                updateKalender();
                updateStatistiken();
                setStatus("Alle zugewiesenen Dienste wurden bestätigt");
            }
        }
    }
    
    @FXML
    private void onOffeneDiensteAnzeigen() {
        if (aktuellerDienstplan != null) {
            List<Dienst> offeneDienste = aktuellerDienstplan.getOffeneDienste();
            
            if (offeneDienste.isEmpty()) {
                showInfo("Keine offenen Dienste", "Alle Dienste sind zugewiesen! " + ICON_SUCCESS);
            } else {
                StringBuilder message = new StringBuilder();
                message.append("Folgende Dienste sind noch nicht zugewiesen:\n\n");
                
                for (Dienst dienst : offeneDienste) {
                    message.append("• ").append(dienst.getDatum())
                           .append(" - ").append(dienst.getArt().getVollName()).append("\n");
                }
                
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Offene Dienste");
                alert.setHeaderText(offeneDienste.size() + " offene Dienste gefunden");
                alert.setContentText(message.toString());
                alert.getDialogPane().setPrefWidth(400);
                alert.showAndWait();
            }
        }
    }
    
    @FXML
    private void onKonflikteAnzeigen() {
        if (aktuellerDienstplan != null) {
            List<String> konflikte = aktuellerDienstplan.getKonflikte();
            
            if (konflikte.isEmpty()) {
                showInfo("Keine Konflikte", "Der Dienstplan enthält keine Konflikte! " + ICON_SUCCESS);
            } else {
                StringBuilder message = new StringBuilder();
                message.append("Folgende Konflikte wurden gefunden:\n\n");
                
                for (String konflikt : konflikte) {
                    message.append(ICON_WARNING).append(" ").append(konflikt).append("\n");
                }
                
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Konflikte gefunden");
                alert.setHeaderText(konflikte.size() + " Konflikt(e) im Dienstplan");
                alert.setContentText(message.toString());
                alert.getDialogPane().setPrefWidth(500);
                alert.showAndWait();
            }
        }
    }
    
    @FXML
    private void onDienstplanLeeren() {
        if (aktuellerDienstplan != null) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Dienstplan leeren");
            confirm.setHeaderText("Möchten Sie wirklich alle Zuweisungen löschen?");
            confirm.setContentText("Diese Aktion kann nicht rückgängig gemacht werden.");
            
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                
                // Alle Zuweisungen entfernen
                aktuellerDienstplan.getDienste().forEach(dienst -> {
                    dienst.zuweisingEntfernen();
                    dienst.setStatus(DienstStatus.GEPLANT);
                    dienst.setBemerkung(null);
                });
                
                // UI aktualisieren
                clearDienstDetails();
                updateKalender();
                updateStatistiken();
                setStatus("Alle Zuweisungen wurden entfernt");
            }
        }
    }
    
    // Helper Methods
    
    private void updateAktuellerMonat() {
        int monatIndex = monatComboBox.getSelectionModel().getSelectedIndex();
        Integer jahr = jahrComboBox.getValue();
        
        if (monatIndex >= 0 && jahr != null) {
            aktuellerMonat = YearMonth.of(jahr, monatIndex + 1);
        }
    }
    
    private void updateMonatJahrComboBoxes() {
        // Flag setzen um Listener-Konflikte zu vermeiden
        programmaticUpdate = true;
        try {
            monatComboBox.getSelectionModel().select(aktuellerMonat.getMonthValue() - 1);
            jahrComboBox.setValue(aktuellerMonat.getYear());
        } finally {
            programmaticUpdate = false;
        }
    }
    
    private void updateDienstplanName() {
        String defaultName = "Dienstplan " + 
            aktuellerMonat.getMonth().getDisplayName(TextStyle.FULL, Locale.GERMAN) + 
            " " + aktuellerMonat.getYear();
        dienstplanNameField.setText(defaultName);
    }
    
    private void updateKalender() {
        kalenderTitelLabel.setText(aktuellerMonat.getMonth().getDisplayName(TextStyle.FULL, Locale.GERMAN) + 
                                  " " + aktuellerMonat.getYear());
        
        // Grid leeren
        kalenderGrid.getChildren().clear();
        kalenderGrid.getRowConstraints().clear();
        kalenderGrid.getColumnConstraints().clear();
        
        // Wochentag-Header erstellen
        String[] wochentage = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
        for (int col = 0; col < 7; col++) {
            Label headerLabel = new Label(wochentage[col]);
            headerLabel.setStyle("-fx-font-weight: bold; -fx-alignment: center;");
            headerLabel.setMaxWidth(Double.MAX_VALUE);
            headerLabel.setAlignment(Pos.CENTER);
            kalenderGrid.add(headerLabel, col, 0);
        }
        
        // Tage des Monats erstellen
        LocalDate startDatum = aktuellerMonat.atDay(1);
        LocalDate endDatum = aktuellerMonat.atEndOfMonth();
        
        // Erste Woche - möglicherweise mit Lücken am Anfang
        int startWochentag = startDatum.getDayOfWeek().getValue() - 1; // 0=Montag
        int row = 1;
        int col = startWochentag;
        
        for (LocalDate datum = startDatum; !datum.isAfter(endDatum); datum = datum.plusDays(1)) {
            VBox tagesBox = createTagesBox(datum);
            kalenderGrid.add(tagesBox, col, row);
            
            col++;
            if (col > 6) {
                col = 0;
                row++;
            }
        }
        
        // Column Constraints für gleichmäßige Verteilung
        for (int i = 0; i < 7; i++) {
            ColumnConstraints colConstraint = new ColumnConstraints();
            colConstraint.setPercentWidth(100.0 / 7);
            kalenderGrid.getColumnConstraints().add(colConstraint);
        }
    }
    
    private VBox createTagesBox(LocalDate datum) {
        VBox tagesBox = new VBox(2);
        tagesBox.setPadding(new Insets(5));
        tagesBox.setStyle("-fx-border-color: #CCCCCC; -fx-border-width: 1;");
        tagesBox.setMinHeight(80);
        tagesBox.setMaxWidth(Double.MAX_VALUE);
        
        // Datum-Label
        Label datumLabel = new Label(String.valueOf(datum.getDayOfMonth()));
        datumLabel.setStyle("-fx-font-weight: bold;");
        tagesBox.getChildren().add(datumLabel);
        
        // Dienste für diesen Tag anzeigen
        List<Dienst> tagDienste = tagesDienste.getOrDefault(datum, new ArrayList<>());
        
        for (Dienst dienst : tagDienste) {
            Label dienstLabel = createDienstLabel(dienst);
            
            // Click-Handler für Dienst-Auswahl
            dienstLabel.setOnMouseClicked(event -> {
                selectDienst(dienst);
            });
            
            tagesBox.getChildren().add(dienstLabel);
        }
        
        // Wenn Tag am Wochenende, anderen Hintergrund
        if (datum.getDayOfWeek().getValue() >= 6) { // Samstag oder Sonntag
            tagesBox.setStyle(tagesBox.getStyle() + "; -fx-background-color: #F8F8F8;");
        }
        
        return tagesBox;
    }
    
    private Label createDienstLabel(Dienst dienst) {
        Label label = new Label(dienst.getArt().getKurzName());
        label.setMaxWidth(Double.MAX_VALUE);
        label.setStyle("-fx-padding: 2px; -fx-font-size: 10px; -fx-cursor: hand;");
        
        // Farbe je nach Dienstart und Status - passend zur Legende
        if (dienst.istZugewiesen()) {
            switch (dienst.getArt()) {
                case DIENST_24H:
                    label.setStyle(label.getStyle() + "; -fx-background-color: #f9ecec; -fx-text-fill: #7a1d21;");
                    break;
                case VISTEN:
                    label.setStyle(label.getStyle() + "; -fx-background-color: #fff9e6; -fx-text-fill: #806600;");
                    break;
                case DAVINCI:
                    label.setStyle(label.getStyle() + "; -fx-background-color: #fce4f0; -fx-text-fill: #c82285;");
                    break;
            }

            // Status-spezifische Anpassungen
            if (dienst.getStatus() == DienstStatus.BESTAETIGT) {
                label.setStyle(label.getStyle() + "; -fx-font-weight: bold;");
            }
        } else {
            // Nicht zugewiesen = rot (Offen)
            label.setStyle(label.getStyle() + "; -fx-background-color: #ffebee; -fx-text-fill: #c62828;");
        }
        
        // Tooltip mit Details
        if (dienst.istZugewiesen()) {
            label.setText(dienst.getArt().getKurzName() + "\n" + dienst.getPersonName());
        }
        
        return label;
    }
    
    private void selectDienst(Dienst dienst) {
        ausgewaehlterDienst = dienst;
        loadDienstDetails(dienst);
        
        // Panel sichtbar machen
        dienstDetailsPanel.setVisible(true);
        dienstDetailsPanel.setManaged(true);
        detailsSeparator.setVisible(true);
        detailsSeparator.setManaged(true);
    }
    
    private void loadDienstDetails(Dienst dienst) {
        if (dienst != null) {
            dienstDatumLabel.setText(dienst.getDatum().toString());
            dienstArtLabel.setText(dienst.getArt().getVollName());
            dienstPersonComboBox.setValue(
                verfuegbarePersonen.stream()
                    .filter(p -> Objects.equals(p.getId(), dienst.getPersonId()))
                    .findFirst()
                    .orElse(null)
            );
            dienstStatusComboBox.setValue(dienst.getStatus());
            dienstBemerkungArea.setText(dienst.getBemerkung() != null ? dienst.getBemerkung() : "");
            
            detailsTitel.setText("Dienst Details: " + dienst.getDatum());
        }
    }
    
    private void clearDienstDetails() {
        ausgewaehlterDienst = null;
        dienstDetailsPanel.setVisible(false);
        dienstDetailsPanel.setManaged(false);
        detailsSeparator.setVisible(false);
        detailsSeparator.setManaged(false);
        detailsTitel.setText("Dienst Details");
    }
    
    private void updateTagesDiensteMap() {
        tagesDienste.clear();
        
        if (aktuellerDienstplan != null) {
            for (Dienst dienst : aktuellerDienstplan.getDienste()) {
                tagesDienste.computeIfAbsent(dienst.getDatum(), k -> new ArrayList<>()).add(dienst);
            }
        }
    }
    
    private void updateStatistiken() {
        if (aktuellerDienstplan == null) {
            clearStatistiken();
            return;
        }
        
        List<Dienst> dienste = aktuellerDienstplan.getDienste();
        int gesamt = dienste.size();
        int zugewiesen = (int) dienste.stream().filter(Dienst::istZugewiesen).count();
        int offen = gesamt - zugewiesen;
        double zuweisungsgrad = gesamt > 0 ? (double) zugewiesen / gesamt : 0.0;
        
        // Haupt-Statistiken
        gesamtDiensteLabel.setText(String.valueOf(gesamt));
        zugewiesenLabel.setText(String.valueOf(zugewiesen));
        offenLabel.setText(String.valueOf(offen));
        zuweisungsgradBar.setProgress(zuweisungsgrad);
        
        statistikLabel.setText(String.format("Zuweisungsgrad: %.1f%% (%d/%d)", 
                                           zuweisungsgrad * 100, zugewiesen, gesamt));
        
        // Person-Statistiken
        updatePersonStatistiken();
    }
    
    private void updatePersonStatistiken() {
        personStatistikListe.clear();
        
        if (aktuellerDienstplan == null || verfuegbarePersonen.isEmpty()) {
            return;
        }
        
        Map<Long, PersonStatistik> statistiken = new HashMap<>();
        
        // Alle Personen initialisieren
        for (Person person : verfuegbarePersonen) {
            statistiken.put(person.getId(), new PersonStatistik(person.getName()));
        }
        
        // Dienste zählen
        for (Dienst dienst : aktuellerDienstplan.getDienste()) {
            if (dienst.istZugewiesen()) {
                PersonStatistik stats = statistiken.get(dienst.getPersonId());
                if (stats != null) {
                    stats.gesamtDienste.set(stats.gesamtDienste.get() + 1);
                    
                    switch (dienst.getArt()) {
                        case DIENST_24H:
                            stats.dienst24h.set(stats.dienst24h.get() + 1);                            break;
                        case VISTEN:
                            stats.visten.set(stats.visten.get() + 1);
                            break;
                        case DAVINCI:
                            stats.davinci.set(stats.davinci.get() + 1);
                            break;
                    }
                }
            }
        }
        
        personStatistikListe.addAll(statistiken.values());
personStatistikListe.sort((a, b) -> Integer.compare(b.gesamtDienste.get(), a.gesamtDienste.get()));    }
    
    private void clearStatistiken() {
        gesamtDiensteLabel.setText("0");
        zugewiesenLabel.setText("0");
        offenLabel.setText("0");
        zuweisungsgradBar.setProgress(0);
        statistikLabel.setText("");
        personStatistikListe.clear();
    }
    
    private void clearAktuellerDienstplan() {
        aktuellerDienstplan = null;
        tagesDienste.clear();
        clearDienstDetails();
        clearStatistiken();
        speichernButton.setDisable(true);
        hideWarnungen();
        bottomStatusLabel.setText("Kein Dienstplan geladen");
        letzteAktualisierungLabel.setText("");
    }
    
    private void showWarnungen(Set<String> warnungen) {
        if (warnungen != null && !warnungen.isEmpty()) {
            warnungenList.getItems().clear();
            warnungenList.getItems().addAll(warnungen);
            warnungenPanel.setVisible(true);
            warnungenPanel.setManaged(true);
        }
    }
    
    private void hideWarnungen() {
        warnungenPanel.setVisible(false);
        warnungenPanel.setManaged(false);
    }
    
    private void updateLetzteAktualisierung() {
        letzteAktualisierungLabel.setText("Letzte Änderung: " + LocalDate.now());
    }
    
    private void setStatus(String message) {
        statusLabel.setText(message);
        bottomStatusLabel.setText(message);
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

    // ===== Excel-Funktionen =====

    @FXML
    private void onExcelVorlageHerunterladen() {
        if (verfuegbarePersonen.isEmpty()) {
            showWarning("Keine Personen", "Es sind keine Personen vorhanden. Bitte zuerst Personen anlegen.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Excel-Vorlage speichern");
        fileChooser.setInitialFileName(ExcelTemplateGenerator.generateDefaultFileName(aktuellerMonat));
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Excel-Dateien", "*.xlsx"));

        File file = fileChooser.showSaveDialog(kalenderGrid.getScene().getWindow());
        if (file != null) {
            try {
                ExcelTemplateGenerator generator = new ExcelTemplateGenerator();
                generator.generiereVorlage(verfuegbarePersonen, aktuellerMonat, file.toPath());
                setStatus("Excel-Vorlage gespeichert: " + file.getName());
                showInfo("Vorlage erstellt", "Die Excel-Vorlage wurde erfolgreich erstellt.\n\n" +
                        "Bitte ausfuellen und dann importieren.");
            } catch (IOException e) {
                logger.error("Fehler beim Erstellen der Excel-Vorlage", e);
                showError("Fehler", "Die Vorlage konnte nicht erstellt werden.", e);
            }
        }
    }

    @FXML
    private void onExcelWuenscheImportieren() {
        if (verfuegbarePersonen.isEmpty()) {
            showWarning("Keine Personen", "Es sind keine Personen vorhanden.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Excel-Wuensche importieren");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Excel-Dateien", "*.xlsx"));

        File file = fileChooser.showOpenDialog(kalenderGrid.getScene().getWindow());
        if (file != null) {
            try {
                ExcelWunschImporter importer = new ExcelWunschImporter(verfuegbarePersonen);
                ExcelWunschImporter.ImportResult result = importer.importiereVorschau(file.toPath());

                if (result.hatFehler()) {
                    showError("Import-Fehler", "Der Import ist fehlgeschlagen.",
                        new Exception(String.join("\n", result.getFehler())));
                    return;
                }

                // Vorschau-Dialog anzeigen
                if (showImportVorschauDialog(result)) {
                    // Wuensche uebernehmen
                    aktuelleWuensche = new ArrayList<>(result.getWuensche());
                    updateWunschInfoPanel();
                    setStatus("Import erfolgreich: " + result.getWuensche().size() + " Wuensche");
                }
            } catch (Exception e) {
                logger.error("Fehler beim Importieren", e);
                showError("Import-Fehler", "Die Datei konnte nicht importiert werden.", e);
            }
        }
    }

    private boolean showImportVorschauDialog(ExcelWunschImporter.ImportResult result) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Import-Vorschau");
        alert.setHeaderText("Wuensche importieren?");

        StringBuilder content = new StringBuilder();
        content.append(result.getZusammenfassung()).append("\n");

        if (result.hatWarnungen()) {
            content.append("\nWarnungen:\n");
            for (String warnung : result.getWarnungen()) {
                content.append("- ").append(warnung).append("\n");
            }
        }

        alert.setContentText(content.toString());
        alert.getDialogPane().setPrefWidth(500);

        Optional<ButtonType> buttonResult = alert.showAndWait();
        return buttonResult.isPresent() && buttonResult.get() == ButtonType.OK;
    }

    @FXML
    private void onDienstplanExportieren() {
        if (aktuellerDienstplan == null) {
            showWarning("Kein Dienstplan", "Es ist kein Dienstplan zum Exportieren vorhanden.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Dienstplan exportieren");
        fileChooser.setInitialFileName(ExcelDienstplanExporter.generateDefaultFileName(aktuellerDienstplan));
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Excel-Dateien", "*.xlsx"));

        File file = fileChooser.showSaveDialog(kalenderGrid.getScene().getWindow());
        if (file != null) {
            try {
                ExcelDienstplanExporter exporter = new ExcelDienstplanExporter();
                exporter.exportiereDienstplan(aktuellerDienstplan, verfuegbarePersonen,
                    aktuelleWunschStatistiken, file.toPath());
                setStatus("Dienstplan exportiert: " + file.getName());
                showInfo("Export erfolgreich", "Der Dienstplan wurde erfolgreich exportiert.");
            } catch (IOException e) {
                logger.error("Fehler beim Exportieren", e);
                showError("Export-Fehler", "Der Dienstplan konnte nicht exportiert werden.", e);
            }
        }
    }

    private void updateWunschInfoPanel() {
        if (wunschInfoPanel == null) return;

        if (aktuelleWuensche.isEmpty()) {
            wunschInfoPanel.setVisible(false);
            wunschInfoPanel.setManaged(false);
        } else {
            wunschInfoPanel.setVisible(true);
            wunschInfoPanel.setManaged(true);

            Map<WunschTyp, Long> counts = aktuelleWuensche.stream()
                .collect(Collectors.groupingBy(MonatsWunsch::getTyp, Collectors.counting()));

            long urlaub = counts.getOrDefault(WunschTyp.URLAUB, 0L);
            long frei = counts.getOrDefault(WunschTyp.FREIWUNSCH, 0L);
            long dienst = counts.getOrDefault(WunschTyp.DIENSTWUNSCH, 0L);

            wunschInfoLabel.setText(String.format("Importiert: %d Urlaub, %d Frei-, %d Dienstwuensche",
                urlaub, frei, dienst));
        }
    }

    private void updateWunschStatistikAnzeige() {
        if (wunscherfuellungPanel == null) return;

        if (aktuelleWunschStatistiken == null || aktuelleWunschStatistiken.isEmpty()) {
            wunscherfuellungPanel.setVisible(false);
            wunscherfuellungPanel.setManaged(false);
            return;
        }

        wunscherfuellungPanel.setVisible(true);
        wunscherfuellungPanel.setManaged(true);

        int gesamtFrei = 0, erfuelltFrei = 0;
        int gesamtDienst = 0, erfuelltDienst = 0;

        for (WunschStatistik stat : aktuelleWunschStatistiken.values()) {
            gesamtFrei += stat.getAnzahlFreiwuensche();
            erfuelltFrei += stat.getErfuellteFreiwuensche();
            gesamtDienst += stat.getAnzahlDienstwuensche();
            erfuelltDienst += stat.getErfuellteDienstwuensche();
        }

        freiwunschStatLabel.setText(String.format("%d/%d (%.0f%%)",
            erfuelltFrei, gesamtFrei, gesamtFrei > 0 ? (double) erfuelltFrei / gesamtFrei * 100 : 100));
        dienstwunschStatLabel.setText(String.format("%d/%d (%.0f%%)",
            erfuelltDienst, gesamtDienst, gesamtDienst > 0 ? (double) erfuelltDienst / gesamtDienst * 100 : 100));

        int gesamtWeich = gesamtFrei + gesamtDienst;
        int erfuelltWeich = erfuelltFrei + erfuelltDienst;
        double quote = gesamtWeich > 0 ? (double) erfuelltWeich / gesamtWeich * 100 : 100;
        gesamtQuoteLabel.setText(String.format("%.0f%%", quote));
    }

    @FXML
    private void onWunschDetailsAnzeigen() {
        if (aktuelleWunschStatistiken == null || aktuelleWunschStatistiken.isEmpty()) {
            showInfo("Keine Statistiken", "Es sind keine Wunschstatistiken vorhanden.");
            return;
        }

        StringBuilder details = new StringBuilder();
        details.append("Wunscherfuellung pro Person:\n\n");

        for (WunschStatistik stat : aktuelleWunschStatistiken.values()) {
            details.append(stat.getPersonName()).append(":\n");
            details.append("  Freiwuensche: ").append(stat.getFreiwuenscheZusammenfassung()).append("\n");
            details.append("  Dienstwuensche: ").append(stat.getDienstwuenscheZusammenfassung()).append("\n");
            details.append("  Quote: ").append(stat.getErfuellungsquoteAlsProzent()).append("\n\n");
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Wunscherfuellung Details");
        alert.setHeaderText("Detaillierte Wunscherfuellung");

        TextArea textArea = new TextArea(details.toString());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(15);

        alert.getDialogPane().setExpandableContent(textArea);
        alert.getDialogPane().setExpanded(true);
        alert.showAndWait();
    }

    // Datenklasse für Statistik-Tabelle
    public static class PersonStatistik {
        private final SimpleStringProperty name;
        private final SimpleIntegerProperty gesamtDienste = new SimpleIntegerProperty(0);
        private final SimpleIntegerProperty dienst24h = new SimpleIntegerProperty(0);
        private final SimpleIntegerProperty visten = new SimpleIntegerProperty(0);
        private final SimpleIntegerProperty davinci = new SimpleIntegerProperty(0);

        public PersonStatistik(String name) {
            this.name = new SimpleStringProperty(name);
        }

        // Getters für TableView
        public String getName() { return name.get(); }
        public int getGesamtDienste() { return gesamtDienste.get(); }
        public int getDienst24h() { return dienst24h.get(); }
        public int getVisten() { return visten.get(); }
        public int getDavinci() { return davinci.get(); }
    }
}