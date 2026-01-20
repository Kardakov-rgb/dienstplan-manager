package de.dienstplan.controller;

import de.dienstplan.database.DatabaseManager;
import de.dienstplan.database.PersonDAO;
import de.dienstplan.model.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Controller für die Personenverwaltung.
 * Abwesenheiten wurden durch MonatsWunsch ersetzt und werden separat über Excel importiert.
 */
public class PersonenverwaltungController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(PersonenverwaltungController.class);

    // FXML Controls - Suche
    @FXML private TextField suchfeld;
    @FXML private Button suchenButton;
    @FXML private Button alleAnzeigenButton;
    @FXML private Button neuePersonButton;

    // FXML Controls - Personentabelle
    @FXML private TableView<Person> personenTabelle;
    @FXML private TableColumn<Person, String> nameColumn;
    @FXML private TableColumn<Person, Integer> dienstanzahlColumn;
    @FXML private TableColumn<Person, String> arbeitstagColumn;
    @FXML private TableColumn<Person, String> dienstartColumn;
    @FXML private Button loeschenButton;
    @FXML private Label anzahlLabel;

    // FXML Controls - Formular
    @FXML private Label formularTitel;
    @FXML private TextField nameField;
    @FXML private Spinner<Integer> anzahlDiensteSpinner;

    // FXML Controls - Arbeitstage
    @FXML private CheckBox montagCheck;
    @FXML private CheckBox dienstagCheck;
    @FXML private CheckBox mittwochCheck;
    @FXML private CheckBox donnerstagCheck;
    @FXML private CheckBox freitagCheck;
    @FXML private CheckBox samstagCheck;
    @FXML private CheckBox sonntagCheck;
    @FXML private Button alleWochentagButton;
    @FXML private Button keineWochentagButton;
    @FXML private Button wocheButton;

    // FXML Controls - Dienstarten
    @FXML private CheckBox dienst24hCheck;
    @FXML private CheckBox vistenCheck;
    @FXML private CheckBox davinciCheck;
    @FXML private Button alleDienstartButton;
    @FXML private Button keineDienstartButton;

    // FXML Controls - Aktionen
    @FXML private Button speichernButton;
    @FXML private Button abbrechenButton;

    // FXML Controls - Status
    @FXML private Label statusLabel;
    @FXML private Label datenbankStatusLabel;

    // Data und Services
    private final PersonDAO personDAO;
    private final ObservableList<Person> personenListe = FXCollections.observableArrayList();
    private Person aktuellePersonBearbeitung = null;
    private boolean istBearbeitungsmodus = false;

    public PersonenverwaltungController() {
        this.personDAO = new PersonDAO();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initialisiere Personenverwaltung...");

        try {
            initializeDatenbank();
            initializeTabellen();
            initializeFormular();
            initializeEventHandler();
            loadPersonenListe();

            setStatus("Personenverwaltung bereit");
            logger.info("Personenverwaltung erfolgreich initialisiert");

        } catch (Exception e) {
            logger.error("Fehler beim Initialisieren der Personenverwaltung", e);
            setStatus("Fehler beim Laden: " + e.getMessage());
            showError("Initialisierungsfehler", "Die Personenverwaltung konnte nicht gestartet werden.", e);
        }
    }

    private void initializeDatenbank() throws SQLException {
        if (DatabaseManager.testConnection()) {
            DatabaseManager.initializeDatabase();
            datenbankStatusLabel.setText("Datenbank: Verbunden");
            datenbankStatusLabel.setStyle("-fx-text-fill: green;");
        } else {
            datenbankStatusLabel.setText("Datenbank: Fehler");
            datenbankStatusLabel.setStyle("-fx-text-fill: red;");
            throw new SQLException("Datenbankverbindung fehlgeschlagen");
        }
    }

    private void initializeTabellen() {
        // Personentabelle konfigurieren
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        dienstanzahlColumn.setCellValueFactory(new PropertyValueFactory<>("anzahlDienste"));

        arbeitstagColumn.setCellValueFactory(cellData -> {
            EnumSet<Wochentag> arbeitsTage = cellData.getValue().getArbeitsTage();
            String arbeitstagText = arbeitsTage.stream()
                    .map(Wochentag::getKurzName)
                    .collect(Collectors.joining(", "));
            return new SimpleStringProperty(arbeitstagText);
        });

        dienstartColumn.setCellValueFactory(cellData -> {
            EnumSet<DienstArt> dienstArten = cellData.getValue().getVerfuegbareDienstArten();
            String dienstartText = dienstArten.stream()
                    .map(DienstArt::getKurzName)
                    .collect(Collectors.joining(", "));
            return new SimpleStringProperty(dienstartText);
        });

        personenTabelle.setItems(personenListe);
    }

    private void initializeFormular() {
        // Spinner für Anzahl Dienste konfigurieren
        SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 50, 20);
        anzahlDiensteSpinner.setValueFactory(valueFactory);

        // Formular initial deaktivieren
        setFormularAktiviert(false);
    }

    private void initializeEventHandler() {
        // Personenauswahl - Direktes Bearbeiten beim Klick
        personenTabelle.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldSelection, newSelection) -> {
                loeschenButton.setDisable(newSelection == null);

                if (newSelection != null) {
                    // Bei ungespeicherten Änderungen nachfragen
                    if (istBearbeitungsmodus && hatUnsavedChanges() && oldSelection != null && !oldSelection.equals(newSelection)) {
                        if (!confirmUnsavedChanges()) {
                            // Auswahl zurücksetzen
                            javafx.application.Platform.runLater(() ->
                                personenTabelle.getSelectionModel().select(oldSelection));
                            return;
                        }
                    }
                    // Direkt in den Bearbeitungsmodus wechseln
                    startePersonBearbeitung(newSelection);
                }
            });

        // Suchfeld Enter-Taste
        suchfeld.setOnAction(e -> onSuchen());

        // Formular-Änderungen überwachen
        nameField.textProperty().addListener((obs, oldText, newText) -> updateSpeichernButton());
    }

    // Event Handler Methoden

    @FXML
    private void onSuchen() {
        String suchtext = suchfeld.getText().trim();
        if (suchtext.isEmpty()) {
            onAlleAnzeigen();
            return;
        }

        try {
            List<Person> suchergebnis = personDAO.search(suchtext, null, null);
            personenListe.setAll(suchergebnis);
            updateAnzahlLabel();
            setStatus("Suche nach '" + suchtext + "': " + suchergebnis.size() + " Treffer");

        } catch (SQLException e) {
            logger.error("Fehler bei der Suche", e);
            showError("Suchfehler", "Die Suche konnte nicht durchgeführt werden.", e);
        }
    }

    @FXML
    private void onAlleAnzeigen() {
        suchfeld.clear();
        loadPersonenListe();
    }

    @FXML
    private void onNeuePersonErstellen() {
        if (istBearbeitungsmodus && hatUnsavedChanges()) {
            if (!confirmUnsavedChanges()) {
                return;
            }
        }

        starteNeuePersonErstellung();
    }

    @FXML
    private void onPersonBearbeiten() {
        Person selectedPerson = personenTabelle.getSelectionModel().getSelectedItem();
        if (selectedPerson != null) {
            if (istBearbeitungsmodus && hatUnsavedChanges()) {
                if (!confirmUnsavedChanges()) {
                    return;
                }
            }

            startePersonBearbeitung(selectedPerson);
        }
    }

    @FXML
    private void onPersonLoeschen() {
        Person selectedPerson = personenTabelle.getSelectionModel().getSelectedItem();
        if (selectedPerson == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Person löschen");
        alert.setHeaderText("Person '" + selectedPerson.getName() + "' löschen?");
        alert.setContentText("Diese Aktion kann nicht rückgängig gemacht werden.");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                personDAO.delete(selectedPerson.getId());
                personenListe.remove(selectedPerson);
                updateAnzahlLabel();
                clearFormular();
                setStatus("Person '" + selectedPerson.getName() + "' gelöscht");

            } catch (SQLException e) {
                logger.error("Fehler beim Löschen der Person", e);
                showError("Löschfehler", "Die Person konnte nicht gelöscht werden.", e);
            }
        }
    }

    @FXML
    private void onSpeichern() {
        if (!validateFormular()) {
            return;
        }

        try {
            Person person = erstellePersonAusFormular();

            if (aktuellePersonBearbeitung == null) {
                // Neue Person erstellen
                Person gespeichertePerson = personDAO.create(person);
                personenListe.add(gespeichertePerson);
                setStatus("Person '" + gespeichertePerson.getName() + "' erstellt");

            } else {
                // Bestehende Person aktualisieren
                person.setId(aktuellePersonBearbeitung.getId());
                personDAO.update(person);

                // Person in Liste aktualisieren
                int index = personenListe.indexOf(aktuellePersonBearbeitung);
                if (index >= 0) {
                    personenListe.set(index, person);
                }

                setStatus("Person '" + person.getName() + "' aktualisiert");
            }

            updateAnzahlLabel();
            beendeBearbeitungsmodus();

        } catch (SQLException e) {
            logger.error("Fehler beim Speichern der Person", e);
            showError("Speicherfehler", "Die Person konnte nicht gespeichert werden.", e);
        }
    }

    @FXML
    private void onAbbrechen() {
        if (istBearbeitungsmodus && hatUnsavedChanges()) {
            if (!confirmUnsavedChanges()) {
                return;
            }
        }

        beendeBearbeitungsmodus();
    }

    // Wochentag Event Handler

    @FXML
    private void onAlleWochentageWaehlen() {
        setAlleWochentage(true);
    }

    @FXML
    private void onKeineWochentageWaehlen() {
        setAlleWochentage(false);
    }

    @FXML
    private void onWocheWaehlen() {
        setAlleWochentage(false);
        montagCheck.setSelected(true);
        dienstagCheck.setSelected(true);
        mittwochCheck.setSelected(true);
        donnerstagCheck.setSelected(true);
        freitagCheck.setSelected(true);
    }

    // Dienstart Event Handler

    @FXML
    private void onAlleDienstartenWaehlen() {
        setAlleDienstarten(true);
    }

    @FXML
    private void onKeineDienstartenWaehlen() {
        setAlleDienstarten(false);
    }

    // Helper Methoden

    private void loadPersonenListe() {
        try {
            List<Person> personen = personDAO.findAll();
            personenListe.setAll(personen);
            updateAnzahlLabel();
            setStatus("Personen geladen: " + personen.size());

        } catch (SQLException e) {
            logger.error("Fehler beim Laden der Personen", e);
            showError("Ladefehler", "Die Personen konnten nicht geladen werden.", e);
        }
    }

    private void updateAnzahlLabel() {
        int anzahl = personenListe.size();
        anzahlLabel.setText(anzahl + " Person" + (anzahl != 1 ? "en" : ""));
    }

    private void zeigePersonDetails(Person person) {
        ladePersonInFormular(person);
        formularTitel.setText("Person Details: " + person.getName());
    }

    private void starteNeuePersonErstellung() {
        aktuellePersonBearbeitung = null;
        istBearbeitungsmodus = true;
        clearFormular();
        setFormularAktiviert(true);
        formularTitel.setText("Neue Person erstellen");
        nameField.requestFocus();
        setStatus("Neue Person wird erstellt...");
    }

    private void startePersonBearbeitung(Person person) {
        aktuellePersonBearbeitung = person;
        istBearbeitungsmodus = true;
        ladePersonInFormular(person);
        setFormularAktiviert(true);
        formularTitel.setText("Person bearbeiten: " + person.getName());
        nameField.requestFocus();
        setStatus("Person wird bearbeitet...");
    }

    private void beendeBearbeitungsmodus() {
        istBearbeitungsmodus = false;
        aktuellePersonBearbeitung = null;
        setFormularAktiviert(false);
        formularTitel.setText("Person Details");

        // Aktuell ausgewählte Person anzeigen oder Formular leeren
        Person selectedPerson = personenTabelle.getSelectionModel().getSelectedItem();
        if (selectedPerson != null) {
            zeigePersonDetails(selectedPerson);
        } else {
            clearFormular();
        }

        setStatus("Bereit");
    }

    private void ladePersonInFormular(Person person) {
        nameField.setText(person.getName());
        anzahlDiensteSpinner.getValueFactory().setValue(person.getAnzahlDienste());

        // Arbeitstage laden
        EnumSet<Wochentag> arbeitsTage = person.getArbeitsTage();
        montagCheck.setSelected(arbeitsTage.contains(Wochentag.MONTAG));
        dienstagCheck.setSelected(arbeitsTage.contains(Wochentag.DIENSTAG));
        mittwochCheck.setSelected(arbeitsTage.contains(Wochentag.MITTWOCH));
        donnerstagCheck.setSelected(arbeitsTage.contains(Wochentag.DONNERSTAG));
        freitagCheck.setSelected(arbeitsTage.contains(Wochentag.FREITAG));
        samstagCheck.setSelected(arbeitsTage.contains(Wochentag.SAMSTAG));
        sonntagCheck.setSelected(arbeitsTage.contains(Wochentag.SONNTAG));

        // Dienstarten laden
        EnumSet<DienstArt> dienstArten = person.getVerfuegbareDienstArten();
        dienst24hCheck.setSelected(dienstArten.contains(DienstArt.DIENST_24H));
        vistenCheck.setSelected(dienstArten.contains(DienstArt.VISTEN));
        davinciCheck.setSelected(dienstArten.contains(DienstArt.DAVINCI));
    }

    private void clearFormular() {
        nameField.clear();
        anzahlDiensteSpinner.getValueFactory().setValue(20);
        setAlleWochentage(false);
        setAlleDienstarten(false);
    }

    private void setFormularAktiviert(boolean aktiviert) {
        nameField.setDisable(!aktiviert);
        anzahlDiensteSpinner.setDisable(!aktiviert);

        montagCheck.setDisable(!aktiviert);
        dienstagCheck.setDisable(!aktiviert);
        mittwochCheck.setDisable(!aktiviert);
        donnerstagCheck.setDisable(!aktiviert);
        freitagCheck.setDisable(!aktiviert);
        samstagCheck.setDisable(!aktiviert);
        sonntagCheck.setDisable(!aktiviert);
        alleWochentagButton.setDisable(!aktiviert);
        keineWochentagButton.setDisable(!aktiviert);
        wocheButton.setDisable(!aktiviert);

        dienst24hCheck.setDisable(!aktiviert);
        vistenCheck.setDisable(!aktiviert);
        davinciCheck.setDisable(!aktiviert);
        alleDienstartButton.setDisable(!aktiviert);
        keineDienstartButton.setDisable(!aktiviert);

        speichernButton.setDisable(!aktiviert);
    }

    private void setAlleWochentage(boolean selected) {
        montagCheck.setSelected(selected);
        dienstagCheck.setSelected(selected);
        mittwochCheck.setSelected(selected);
        donnerstagCheck.setSelected(selected);
        freitagCheck.setSelected(selected);
        samstagCheck.setSelected(selected);
        sonntagCheck.setSelected(selected);
    }

    private void setAlleDienstarten(boolean selected) {
        dienst24hCheck.setSelected(selected);
        vistenCheck.setSelected(selected);
        davinciCheck.setSelected(selected);
    }

    private boolean validateFormular() {
        StringBuilder errors = new StringBuilder();

        // Name prüfen
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            errors.append("- Name ist erforderlich\n");
        }

        // Arbeitstage prüfen
        if (!hatArbeitstageAusgewaehlt()) {
            errors.append("- Mindestens ein Arbeitstag muss ausgewählt werden\n");
        }

        // Dienstarten prüfen
        if (!hatDienstartenAusgewaehlt()) {
            errors.append("- Mindestens eine Dienstart muss ausgewählt werden\n");
        }

        if (errors.length() > 0) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Validierungsfehler");
            alert.setHeaderText("Bitte korrigieren Sie folgende Fehler:");
            alert.setContentText(errors.toString());
            alert.showAndWait();
            return false;
        }

        return true;
    }

    private boolean hatArbeitstageAusgewaehlt() {
        return montagCheck.isSelected() || dienstagCheck.isSelected() ||
               mittwochCheck.isSelected() || donnerstagCheck.isSelected() ||
               freitagCheck.isSelected() || samstagCheck.isSelected() ||
               sonntagCheck.isSelected();
    }

    private boolean hatDienstartenAusgewaehlt() {
        return dienst24hCheck.isSelected() || vistenCheck.isSelected() || davinciCheck.isSelected();
    }

    private Person erstellePersonAusFormular() {
        Person person = new Person();

        // Grunddaten
        person.setName(nameField.getText().trim());
        person.setAnzahlDienste(anzahlDiensteSpinner.getValue());

        // Arbeitstage
        EnumSet<Wochentag> arbeitsTage = EnumSet.noneOf(Wochentag.class);
        if (montagCheck.isSelected()) arbeitsTage.add(Wochentag.MONTAG);
        if (dienstagCheck.isSelected()) arbeitsTage.add(Wochentag.DIENSTAG);
        if (mittwochCheck.isSelected()) arbeitsTage.add(Wochentag.MITTWOCH);
        if (donnerstagCheck.isSelected()) arbeitsTage.add(Wochentag.DONNERSTAG);
        if (freitagCheck.isSelected()) arbeitsTage.add(Wochentag.FREITAG);
        if (samstagCheck.isSelected()) arbeitsTage.add(Wochentag.SAMSTAG);
        if (sonntagCheck.isSelected()) arbeitsTage.add(Wochentag.SONNTAG);
        person.setArbeitsTage(arbeitsTage);

        // Dienstarten
        EnumSet<DienstArt> dienstArten = EnumSet.noneOf(DienstArt.class);
        if (dienst24hCheck.isSelected()) dienstArten.add(DienstArt.DIENST_24H);
        if (vistenCheck.isSelected()) dienstArten.add(DienstArt.VISTEN);
        if (davinciCheck.isSelected()) dienstArten.add(DienstArt.DAVINCI);
        person.setVerfuegbareDienstArten(dienstArten);

        return person;
    }

    private boolean hatUnsavedChanges() {
        if (!istBearbeitungsmodus) return false;

        try {
            Person formularPerson = erstellePersonAusFormular();

            if (aktuellePersonBearbeitung == null) {
                // Neue Person - prüfe ob Formular leer ist
                return !nameField.getText().trim().isEmpty() ||
                       anzahlDiensteSpinner.getValue() != 20 ||
                       hatArbeitstageAusgewaehlt() ||
                       hatDienstartenAusgewaehlt();
            } else {
                // Bestehende Person - prüfe Änderungen
                return !formularPerson.getName().equals(aktuellePersonBearbeitung.getName()) ||
                       formularPerson.getAnzahlDienste() != aktuellePersonBearbeitung.getAnzahlDienste() ||
                       !formularPerson.getArbeitsTage().equals(aktuellePersonBearbeitung.getArbeitsTage()) ||
                       !formularPerson.getVerfuegbareDienstArten().equals(aktuellePersonBearbeitung.getVerfuegbareDienstArten());
            }
        } catch (Exception e) {
            return true; // Im Zweifel annehmen, dass Änderungen vorhanden sind
        }
    }

    private boolean confirmUnsavedChanges() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Ungespeicherte Änderungen");
        alert.setHeaderText("Es gibt ungespeicherte Änderungen.");
        alert.setContentText("Möchten Sie die Änderungen verwerfen?");

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void updateSpeichernButton() {
        if (istBearbeitungsmodus) {
            speichernButton.setDisable(false);
        }
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
        logger.debug("Status: {}", message);
    }

    private void showError(String title, String message, Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(message);
        alert.setContentText(e != null ? e.getMessage() : "Unbekannter Fehler");
        alert.showAndWait();
    }
}
