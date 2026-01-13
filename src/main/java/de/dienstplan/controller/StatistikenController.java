package de.dienstplan.controller;

import de.dienstplan.database.*;
import de.dienstplan.model.*;
import de.dienstplan.service.StatistikService;
import de.dienstplan.service.StatistikService.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Controller für die Statistik-Seite.
 * Zeigt Fairness-Analysen, Dienstverteilung und Planungsqualität.
 */
public class StatistikenController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(StatistikenController.class);
    private static final DateTimeFormatter MONAT_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.GERMAN);
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    // Root Container
    @FXML private VBox rootContainer;

    // Zeitraum-Auswahl
    @FXML private ComboBox<String> zeitraumComboBox;
    @FXML private ComboBox<YearMonth> monatVonComboBox;
    @FXML private ComboBox<YearMonth> monatBisComboBox;
    @FXML private Button aktualisierenButton;
    @FXML private Button exportButton;

    // Schnell-Übersicht
    @FXML private Label abdeckungsgradLabel;
    @FXML private ProgressBar abdeckungsgradBar;
    @FXML private Label offeneDiensteLabel;
    @FXML private Label offeneDiensteDetailLabel;
    @FXML private Label wunscherfuellungLabel;
    @FXML private Label wunscherfuellungDetailLabel;
    @FXML private Label konflikteLabel;
    @FXML private Label konflikteDetailLabel;

    // Benachteiligte Warnung
    @FXML private HBox benachteiligteBox;
    @FXML private Label benachteiligteLabel;
    @FXML private Label benachteiligteNamenLabel;

    // Wunscherfüllung
    @FXML private TableView<WunscherfuellungRow> wunscherfuellungTable;
    @FXML private TableColumn<WunscherfuellungRow, String> wunschPersonColumn;
    @FXML private TableColumn<WunscherfuellungRow, String> wunschFreiColumn;
    @FXML private TableColumn<WunscherfuellungRow, String> wunschDienstColumn;
    @FXML private TableColumn<WunscherfuellungRow, String> wunschGesamtColumn;
    @FXML private TableColumn<WunscherfuellungRow, String> wunschStatusColumn;
    @FXML private BarChart<String, Number> wunscherfuellungChart;
    @FXML private CategoryAxis wunschChartXAxis;
    @FXML private NumberAxis wunschChartYAxis;

    // Fairness Trend
    @FXML private LineChart<String, Number> fairnessTrendChart;
    @FXML private CategoryAxis trendChartXAxis;
    @FXML private NumberAxis trendChartYAxis;

    // Soll/Ist Vergleich
    @FXML private TableView<SollIstRow> sollIstTable;
    @FXML private TableColumn<SollIstRow, String> sollIstPersonColumn;
    @FXML private TableColumn<SollIstRow, String> sollIstSollColumn;
    @FXML private TableColumn<SollIstRow, String> sollIstIstColumn;
    @FXML private TableColumn<SollIstRow, String> sollIstDiffColumn;
    @FXML private BarChart<String, Number> sollIstChart;
    @FXML private CategoryAxis sollIstChartXAxis;
    @FXML private NumberAxis sollIstChartYAxis;

    // Dienstarten Verteilung
    @FXML private PieChart dienstArtenPieChart;
    @FXML private TableView<DienstArtRow> dienstArtenTable;
    @FXML private TableColumn<DienstArtRow, String> dienstArtColumn;
    @FXML private TableColumn<DienstArtRow, String> dienstArtAnzahlColumn;
    @FXML private TableColumn<DienstArtRow, String> dienstArtProzentColumn;

    // Wochentag Verteilung
    @FXML private BarChart<String, Number> wochentagChart;
    @FXML private CategoryAxis wochentagChartXAxis;
    @FXML private NumberAxis wochentagChartYAxis;

    // Offene Dienste
    @FXML private TableView<OffenerDienstRow> offeneDiensteTable;
    @FXML private TableColumn<OffenerDienstRow, String> offenDatumColumn;
    @FXML private TableColumn<OffenerDienstRow, String> offenWochentagColumn;
    @FXML private TableColumn<OffenerDienstRow, String> offenDienstartColumn;
    @FXML private TableColumn<OffenerDienstRow, String> offenDienstplanColumn;

    // Konflikte
    @FXML private TableView<KonfliktRow> konflikteTable;
    @FXML private TableColumn<KonfliktRow, String> konfliktDatumColumn;
    @FXML private TableColumn<KonfliktRow, String> konfliktPersonColumn;
    @FXML private TableColumn<KonfliktRow, String> konfliktAnzahlColumn;
    @FXML private TableColumn<KonfliktRow, String> konfliktDetailsColumn;

    // Status
    @FXML private Label statusLabel;
    @FXML private Label letzteAktualisierungLabel;

    // Services
    private StatistikService statistikService;

    // Daten
    private final ObservableList<WunscherfuellungRow> wunscherfuellungData = FXCollections.observableArrayList();
    private final ObservableList<SollIstRow> sollIstData = FXCollections.observableArrayList();
    private final ObservableList<DienstArtRow> dienstArtenData = FXCollections.observableArrayList();
    private final ObservableList<OffenerDienstRow> offeneDiensteData = FXCollections.observableArrayList();
    private final ObservableList<KonfliktRow> konflikteData = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initialisiere Statistiken-Controller...");

        try {
            statistikService = new StatistikService();
            initializeZeitraumAuswahl();
            initializeTables();
            initializeCharts();

            // Initial laden
            loadStatistiken();

            logger.info("Statistiken-Controller erfolgreich initialisiert");
        } catch (Exception e) {
            logger.error("Fehler beim Initialisieren des Statistiken-Controllers", e);
            setStatus("Fehler beim Laden: " + e.getMessage());
        }
    }

    private void initializeZeitraumAuswahl() {
        // Zeitraum-Typen
        zeitraumComboBox.setItems(FXCollections.observableArrayList(
                "Aktueller Monat",
                "Letzten 3 Monate",
                "Letzten 6 Monate",
                "Letzten 12 Monate",
                "Gesamtes Jahr",
                "Benutzerdefiniert"
        ));
        zeitraumComboBox.setValue("Aktueller Monat");
        zeitraumComboBox.setOnAction(e -> onZeitraumChanged());

        // Monate für Von/Bis ComboBoxen
        List<YearMonth> verfuegbareMonate = getVerfuegbareMonate();
        monatVonComboBox.setItems(FXCollections.observableArrayList(verfuegbareMonate));
        monatBisComboBox.setItems(FXCollections.observableArrayList(verfuegbareMonate));

        // Custom Cell Factory für Monat-Anzeige
        monatVonComboBox.setCellFactory(lv -> createMonatCell());
        monatVonComboBox.setButtonCell(createMonatCell());
        monatBisComboBox.setCellFactory(lv -> createMonatCell());
        monatBisComboBox.setButtonCell(createMonatCell());

        // Initial: Benutzerdefiniert ausgeblendet
        monatVonComboBox.setVisible(false);
        monatVonComboBox.setManaged(false);
        monatBisComboBox.setVisible(false);
        monatBisComboBox.setManaged(false);
    }

    private ListCell<YearMonth> createMonatCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(YearMonth item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.format(MONAT_FORMAT));
                }
            }
        };
    }

    private List<YearMonth> getVerfuegbareMonate() {
        List<YearMonth> monate = new ArrayList<>();
        YearMonth current = YearMonth.now();
        // Letzte 24 Monate + nächste 3 Monate
        for (int i = -24; i <= 3; i++) {
            monate.add(current.plusMonths(i));
        }
        Collections.reverse(monate);
        return monate;
    }

    private void initializeTables() {
        // Wunscherfüllung Tabelle
        wunschPersonColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().personName()));
        wunschFreiColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().freiwuensche()));
        wunschDienstColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().dienstwuensche()));
        wunschGesamtColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().gesamtProzent()));
        wunschStatusColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().status()));
        wunscherfuellungTable.setItems(wunscherfuellungData);

        // Soll/Ist Tabelle
        sollIstPersonColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().personName()));
        sollIstSollColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().soll()));
        sollIstIstColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().ist()));
        sollIstDiffColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().differenz()));
        sollIstTable.setItems(sollIstData);

        // Dienstarten Tabelle
        dienstArtColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().dienstArt()));
        dienstArtAnzahlColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().anzahl()));
        dienstArtProzentColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().prozent()));
        dienstArtenTable.setItems(dienstArtenData);

        // Offene Dienste Tabelle
        offenDatumColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().datum()));
        offenWochentagColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().wochentag()));
        offenDienstartColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().dienstArt()));
        offenDienstplanColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().dienstplan()));
        offeneDiensteTable.setItems(offeneDiensteData);

        // Konflikte Tabelle
        konfliktDatumColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().datum()));
        konfliktPersonColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().person()));
        konfliktAnzahlColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().anzahl()));
        konfliktDetailsColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().details()));
        konflikteTable.setItems(konflikteData);
    }

    private void initializeCharts() {
        // Chart-Einstellungen
        wunscherfuellungChart.setAnimated(false);
        fairnessTrendChart.setAnimated(false);
        sollIstChart.setAnimated(false);
        wochentagChart.setAnimated(false);
        dienstArtenPieChart.setAnimated(false);

        // Y-Achsen Konfiguration
        wunschChartYAxis.setAutoRanging(false);
        wunschChartYAxis.setLowerBound(0);
        wunschChartYAxis.setUpperBound(100);
        wunschChartYAxis.setTickUnit(10);

        trendChartYAxis.setAutoRanging(false);
        trendChartYAxis.setLowerBound(0);
        trendChartYAxis.setUpperBound(100);
        trendChartYAxis.setTickUnit(10);
    }

    private void onZeitraumChanged() {
        String auswahl = zeitraumComboBox.getValue();
        boolean benutzerdefiniert = "Benutzerdefiniert".equals(auswahl);

        monatVonComboBox.setVisible(benutzerdefiniert);
        monatVonComboBox.setManaged(benutzerdefiniert);
        monatBisComboBox.setVisible(benutzerdefiniert);
        monatBisComboBox.setManaged(benutzerdefiniert);

        if (!benutzerdefiniert) {
            loadStatistiken();
        }
    }

    @FXML
    private void onAktualisieren() {
        loadStatistiken();
    }

    @FXML
    private void onExport() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Statistiken exportieren");
        fileChooser.setInitialFileName("Statistiken_" + YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM")) + ".xlsx");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel-Dateien", "*.xlsx"),
                new FileChooser.ExtensionFilter("PDF-Dateien", "*.pdf")
        );

        File file = fileChooser.showSaveDialog(rootContainer.getScene().getWindow());
        if (file != null) {
            try {
                ZeitraumInfo zeitraum = getSelectedZeitraum();
                statistikService.exportStatistiken(file, zeitraum.von(), zeitraum.bis());
                setStatus("Export erfolgreich: " + file.getName());

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Export erfolgreich");
                alert.setHeaderText("Statistiken wurden exportiert");
                alert.setContentText("Datei: " + file.getAbsolutePath());
                alert.showAndWait();

            } catch (Exception e) {
                logger.error("Fehler beim Export", e);
                setStatus("Export fehlgeschlagen: " + e.getMessage());

                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Export fehlgeschlagen");
                alert.setHeaderText("Die Statistiken konnten nicht exportiert werden");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        }
    }

    private void loadStatistiken() {
        setStatus("Lade Statistiken...");

        try {
            ZeitraumInfo zeitraum = getSelectedZeitraum();
            logger.info("Lade Statistiken für Zeitraum: {} bis {}", zeitraum.von(), zeitraum.bis());

            // Alle Statistiken laden
            GesamtStatistik stats = statistikService.berechneGesamtStatistik(zeitraum.von(), zeitraum.bis());

            // UI aktualisieren
            updateSchnellUebersicht(stats);
            updateBenachteiligteWarnung(stats);
            updateWunscherfuellung(stats);
            updateFairnessTrend(zeitraum.von(), zeitraum.bis());
            updateSollIstVergleich(stats);
            updateDienstArtenVerteilung(stats);
            updateWochentagVerteilung(stats);
            updateOffeneDienste(stats);
            updateKonflikte(stats);

            letzteAktualisierungLabel.setText("Letzte Aktualisierung: " +
                    LocalDateTime.now().format(DATETIME_FORMAT));
            setStatus("Statistiken geladen");

        } catch (SQLException e) {
            logger.error("Fehler beim Laden der Statistiken", e);
            setStatus("Fehler: " + e.getMessage());
        }
    }

    private ZeitraumInfo getSelectedZeitraum() {
        String auswahl = zeitraumComboBox.getValue();
        YearMonth jetzt = YearMonth.now();

        return switch (auswahl) {
            case "Letzten 3 Monate" -> new ZeitraumInfo(jetzt.minusMonths(2), jetzt);
            case "Letzten 6 Monate" -> new ZeitraumInfo(jetzt.minusMonths(5), jetzt);
            case "Letzten 12 Monate" -> new ZeitraumInfo(jetzt.minusMonths(11), jetzt);
            case "Gesamtes Jahr" -> new ZeitraumInfo(YearMonth.of(jetzt.getYear(), 1), YearMonth.of(jetzt.getYear(), 12));
            case "Benutzerdefiniert" -> {
                YearMonth von = monatVonComboBox.getValue();
                YearMonth bis = monatBisComboBox.getValue();
                if (von == null) von = jetzt;
                if (bis == null) bis = jetzt;
                if (von.isAfter(bis)) {
                    YearMonth temp = von;
                    von = bis;
                    bis = temp;
                }
                yield new ZeitraumInfo(von, bis);
            }
            default -> new ZeitraumInfo(jetzt, jetzt); // Aktueller Monat
        };
    }

    private void updateSchnellUebersicht(GesamtStatistik stats) {
        // Abdeckungsgrad
        double abdeckung = stats.abdeckungsgrad();
        abdeckungsgradLabel.setText(String.format("%.0f%%", abdeckung));
        abdeckungsgradBar.setProgress(abdeckung / 100.0);

        // Farbe je nach Abdeckungsgrad
        String abdeckungFarbe = abdeckung >= 90 ? "#4CAF50" : abdeckung >= 70 ? "#FF9800" : "#F44336";
        abdeckungsgradLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: " + abdeckungFarbe + ";");

        // Offene Dienste
        offeneDiensteLabel.setText(String.valueOf(stats.offeneDienste()));
        offeneDiensteDetailLabel.setText("von " + stats.gesamtDienste() + " gesamt");

        // Wunscherfüllung
        double wunschQuote = stats.durchschnittlicheWunscherfuellung();
        wunscherfuellungLabel.setText(String.format("%.0f%%", wunschQuote));

        // Konflikte
        konflikteLabel.setText(String.valueOf(stats.anzahlKonflikte()));
    }

    private void updateBenachteiligteWarnung(GesamtStatistik stats) {
        List<String> benachteiligte = stats.benachteiligtePersonen();

        if (benachteiligte.isEmpty()) {
            benachteiligteBox.setVisible(false);
            benachteiligteBox.setManaged(false);
        } else {
            benachteiligteBox.setVisible(true);
            benachteiligteBox.setManaged(true);
            benachteiligteNamenLabel.setText(String.join(", ", benachteiligte));
        }
    }

    private void updateWunscherfuellung(GesamtStatistik stats) {
        wunscherfuellungData.clear();
        wunscherfuellungChart.getData().clear();

        XYChart.Series<String, Number> freiSeries = new XYChart.Series<>();
        freiSeries.setName("Freiwünsche");
        XYChart.Series<String, Number> dienstSeries = new XYChart.Series<>();
        dienstSeries.setName("Dienstwünsche");

        for (PersonWunschStatistik pw : stats.personWunschStatistiken()) {
            // Tabelle
            String status = pw.erfuellungsQuote() < 70 ? "⚠️" : "✓";
            wunscherfuellungData.add(new WunscherfuellungRow(
                    pw.personName(),
                    pw.erfuellteFreiwuensche() + "/" + pw.gesamtFreiwuensche(),
                    pw.erfuellteDienstwuensche() + "/" + pw.gesamtDienstwuensche(),
                    String.format("%.0f%%", pw.erfuellungsQuote()),
                    status
            ));

            // Chart
            freiSeries.getData().add(new XYChart.Data<>(pw.personName(), pw.freiwunschQuote()));
            dienstSeries.getData().add(new XYChart.Data<>(pw.personName(), pw.dienstwunschQuote()));
        }

        wunscherfuellungChart.getData().addAll(freiSeries, dienstSeries);
    }

    private void updateFairnessTrend(YearMonth von, YearMonth bis) {
        fairnessTrendChart.getData().clear();

        try {
            Map<Long, List<MonatlicheErfuellung>> trendDaten = statistikService.berechneFairnessTrend(von, bis);

            for (Map.Entry<Long, List<MonatlicheErfuellung>> entry : trendDaten.entrySet()) {
                XYChart.Series<String, Number> series = new XYChart.Series<>();
                series.setName(entry.getValue().isEmpty() ? "Person " + entry.getKey()
                        : entry.getValue().get(0).personName());

                for (MonatlicheErfuellung me : entry.getValue()) {
                    series.getData().add(new XYChart.Data<>(
                            me.monat().format(MONAT_FORMAT),
                            me.erfuellungsQuote()
                    ));
                }

                if (!series.getData().isEmpty()) {
                    fairnessTrendChart.getData().add(series);
                }
            }
        } catch (SQLException e) {
            logger.error("Fehler beim Laden des Fairness-Trends", e);
        }
    }

    private void updateSollIstVergleich(GesamtStatistik stats) {
        sollIstData.clear();
        sollIstChart.getData().clear();

        XYChart.Series<String, Number> sollSeries = new XYChart.Series<>();
        sollSeries.setName("Soll");
        XYChart.Series<String, Number> istSeries = new XYChart.Series<>();
        istSeries.setName("Ist");

        for (PersonDienstStatistik pd : stats.personDienstStatistiken()) {
            int diff = pd.istDienste() - pd.sollDienste();
            String diffStr = diff >= 0 ? "+" + diff : String.valueOf(diff);

            sollIstData.add(new SollIstRow(
                    pd.personName(),
                    String.valueOf(pd.sollDienste()),
                    String.valueOf(pd.istDienste()),
                    diffStr
            ));

            sollSeries.getData().add(new XYChart.Data<>(pd.personName(), pd.sollDienste()));
            istSeries.getData().add(new XYChart.Data<>(pd.personName(), pd.istDienste()));
        }

        sollIstChart.getData().addAll(sollSeries, istSeries);
    }

    private void updateDienstArtenVerteilung(GesamtStatistik stats) {
        dienstArtenData.clear();
        dienstArtenPieChart.getData().clear();

        int gesamt = stats.dienstArtenVerteilung().values().stream().mapToInt(Integer::intValue).sum();

        for (Map.Entry<DienstArt, Integer> entry : stats.dienstArtenVerteilung().entrySet()) {
            int anzahl = entry.getValue();
            double prozent = gesamt > 0 ? (double) anzahl / gesamt * 100 : 0;

            dienstArtenData.add(new DienstArtRow(
                    entry.getKey().getVollName(),
                    String.valueOf(anzahl),
                    String.format("%.1f%%", prozent)
            ));

            dienstArtenPieChart.getData().add(new PieChart.Data(
                    entry.getKey().getVollName() + " (" + anzahl + ")",
                    anzahl
            ));
        }
    }

    private void updateWochentagVerteilung(GesamtStatistik stats) {
        wochentagChart.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Dienste");

        String[] wochentage = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
        for (String tag : wochentage) {
            int anzahl = stats.wochentagVerteilung().getOrDefault(tag, 0);
            series.getData().add(new XYChart.Data<>(tag, anzahl));
        }

        wochentagChart.getData().add(series);
    }

    private void updateOffeneDienste(GesamtStatistik stats) {
        offeneDiensteData.clear();

        DateTimeFormatter datumFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        for (OffenerDienst od : stats.offeneDiensteListe()) {
            offeneDiensteData.add(new OffenerDienstRow(
                    od.datum().format(datumFormat),
                    od.wochentag(),
                    od.dienstArt(),
                    od.dienstplanName()
            ));
        }
    }

    private void updateKonflikte(GesamtStatistik stats) {
        konflikteData.clear();

        DateTimeFormatter datumFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        for (Konflikt k : stats.konflikte()) {
            konflikteData.add(new KonfliktRow(
                    k.datum().format(datumFormat),
                    k.personName(),
                    String.valueOf(k.anzahlDienste()),
                    k.details()
            ));
        }
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
        logger.debug("Status: {}", message);
    }

    // Record-Klassen für Tabellen-Zeilen
    public record WunscherfuellungRow(String personName, String freiwuensche, String dienstwuensche,
                                       String gesamtProzent, String status) {}

    public record SollIstRow(String personName, String soll, String ist, String differenz) {}

    public record DienstArtRow(String dienstArt, String anzahl, String prozent) {}

    public record OffenerDienstRow(String datum, String wochentag, String dienstArt, String dienstplan) {}

    public record KonfliktRow(String datum, String person, String anzahl, String details) {}

    private record ZeitraumInfo(YearMonth von, YearMonth bis) {}
}
