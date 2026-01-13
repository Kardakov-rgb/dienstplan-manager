package de.dienstplan.service;

import de.dienstplan.database.*;
import de.dienstplan.model.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service für die Berechnung und Aggregation von Statistiken.
 */
public class StatistikService {

    private static final Logger logger = LoggerFactory.getLogger(StatistikService.class);

    private final PersonDAO personDAO;
    private final DienstplanDAO dienstplanDAO;
    private final DienstDAO dienstDAO;
    private final FairnessHistorieDAO fairnessHistorieDAO;

    public StatistikService() {
        this.personDAO = new PersonDAO();
        this.dienstplanDAO = new DienstplanDAO();
        this.dienstDAO = new DienstDAO();
        this.fairnessHistorieDAO = new FairnessHistorieDAO();
    }

    /**
     * Berechnet die Gesamtstatistik für einen Zeitraum.
     */
    public GesamtStatistik berechneGesamtStatistik(YearMonth von, YearMonth bis) throws SQLException {
        logger.info("Berechne Gesamtstatistik für {} bis {}", von, bis);

        // Alle Dienstpläne im Zeitraum laden
        List<Dienstplan> dienstplaene = dienstplanDAO.findByDateRange(von, bis);
        List<Person> personen = personDAO.findAll();

        // Alle Dienste sammeln
        List<Dienst> alleDienste = new ArrayList<>();
        for (Dienstplan dp : dienstplaene) {
            alleDienste.addAll(dp.getDienste());
        }

        // Grundlegende Zahlen
        int gesamtDienste = alleDienste.size();
        int zugewieseneDienste = (int) alleDienste.stream().filter(Dienst::istZugewiesen).count();
        int offeneDienste = gesamtDienste - zugewieseneDienste;
        double abdeckungsgrad = gesamtDienste > 0 ? (double) zugewieseneDienste / gesamtDienste * 100 : 0;

        // Wunscherfüllung berechnen
        List<PersonWunschStatistik> personWunschStatistiken = berechnePersonWunschStatistiken(personen, von, bis);
        double durchschnittlicheWunscherfuellung = personWunschStatistiken.stream()
                .mapToDouble(PersonWunschStatistik::erfuellungsQuote)
                .average()
                .orElse(0);

        // Benachteiligte finden
        List<String> benachteiligte = personWunschStatistiken.stream()
                .filter(pw -> pw.erfuellungsQuote() < 70 && (pw.gesamtFreiwuensche() + pw.gesamtDienstwuensche()) > 0)
                .map(PersonWunschStatistik::personName)
                .collect(Collectors.toList());

        // Dienst-Statistiken pro Person
        List<PersonDienstStatistik> personDienstStatistiken = berechnePersonDienstStatistiken(personen, alleDienste);

        // Dienstarten-Verteilung
        Map<DienstArt, Integer> dienstArtenVerteilung = berechneDienstArtenVerteilung(alleDienste);

        // Wochentag-Verteilung
        Map<String, Integer> wochentagVerteilung = berechneWochentagVerteilung(alleDienste);

        // Offene Dienste Liste
        List<OffenerDienst> offeneDiensteListe = berechneOffeneDiensteListe(dienstplaene);

        // Konflikte erkennen
        List<Konflikt> konflikte = berechneKonflikte(alleDienste);

        return new GesamtStatistik(
                gesamtDienste,
                zugewieseneDienste,
                offeneDienste,
                abdeckungsgrad,
                durchschnittlicheWunscherfuellung,
                konflikte.size(),
                benachteiligte,
                personWunschStatistiken,
                personDienstStatistiken,
                dienstArtenVerteilung,
                wochentagVerteilung,
                offeneDiensteListe,
                konflikte
        );
    }

    /**
     * Berechnet die Wunscherfüllungs-Statistiken pro Person.
     */
    private List<PersonWunschStatistik> berechnePersonWunschStatistiken(
            List<Person> personen, YearMonth von, YearMonth bis) throws SQLException {

        List<PersonWunschStatistik> result = new ArrayList<>();

        for (Person person : personen) {
            int gesamtFrei = 0;
            int erfuelltFrei = 0;
            int gesamtDienst = 0;
            int erfuelltDienst = 0;

            // Fairness-Historie für den Zeitraum laden
            List<WunschStatistik> historie = fairnessHistorieDAO.findByPersonId(person.getId());

            for (WunschStatistik ws : historie) {
                if (ws.getMonat() != null &&
                        !ws.getMonat().isBefore(von) &&
                        !ws.getMonat().isAfter(bis)) {

                    gesamtFrei += ws.getAnzahlFreiwuensche();
                    erfuelltFrei += ws.getErfuellteFreiwuensche();
                    gesamtDienst += ws.getAnzahlDienstwuensche();
                    erfuelltDienst += ws.getErfuellteDienstwuensche();
                }
            }

            double freiwunschQuote = gesamtFrei > 0 ? (double) erfuelltFrei / gesamtFrei * 100 : 100;
            double dienstwunschQuote = gesamtDienst > 0 ? (double) erfuelltDienst / gesamtDienst * 100 : 100;
            int gesamt = gesamtFrei + gesamtDienst;
            int erfuellt = erfuelltFrei + erfuelltDienst;
            double erfuellungsQuote = gesamt > 0 ? (double) erfuellt / gesamt * 100 : 100;

            result.add(new PersonWunschStatistik(
                    person.getId(),
                    person.getName(),
                    gesamtFrei,
                    erfuelltFrei,
                    freiwunschQuote,
                    gesamtDienst,
                    erfuelltDienst,
                    dienstwunschQuote,
                    erfuellungsQuote
            ));
        }

        // Sortieren nach Erfüllungsquote (aufsteigend, niedrigste zuerst)
        result.sort(Comparator.comparingDouble(PersonWunschStatistik::erfuellungsQuote));

        return result;
    }

    /**
     * Berechnet die Dienst-Statistiken pro Person (Soll vs. Ist).
     */
    private List<PersonDienstStatistik> berechnePersonDienstStatistiken(
            List<Person> personen, List<Dienst> alleDienste) {

        List<PersonDienstStatistik> result = new ArrayList<>();

        // Dienste pro Person zählen
        Map<Long, Integer> diensteProPerson = new HashMap<>();
        Map<Long, Map<DienstArt, Integer>> diensteProPersonNachArt = new HashMap<>();

        for (Dienst dienst : alleDienste) {
            if (dienst.getPersonId() != null) {
                diensteProPerson.merge(dienst.getPersonId(), 1, Integer::sum);

                diensteProPersonNachArt
                        .computeIfAbsent(dienst.getPersonId(), k -> new EnumMap<>(DienstArt.class))
                        .merge(dienst.getArt(), 1, Integer::sum);
            }
        }

        for (Person person : personen) {
            int sollDienste = person.getAnzahlDienste();
            int istDienste = diensteProPerson.getOrDefault(person.getId(), 0);

            Map<DienstArt, Integer> nachArt = diensteProPersonNachArt
                    .getOrDefault(person.getId(), new EnumMap<>(DienstArt.class));

            int dienste24h = nachArt.getOrDefault(DienstArt.DIENST_24H, 0);
            int diensteVisiten = nachArt.getOrDefault(DienstArt.VISTEN, 0);
            int diensteDavinci = nachArt.getOrDefault(DienstArt.DAVINCI, 0);

            result.add(new PersonDienstStatistik(
                    person.getId(),
                    person.getName(),
                    sollDienste,
                    istDienste,
                    dienste24h,
                    diensteVisiten,
                    diensteDavinci
            ));
        }

        // Sortieren nach Name
        result.sort(Comparator.comparing(PersonDienstStatistik::personName));

        return result;
    }

    /**
     * Berechnet die Verteilung nach Dienstarten.
     */
    private Map<DienstArt, Integer> berechneDienstArtenVerteilung(List<Dienst> alleDienste) {
        Map<DienstArt, Integer> verteilung = new EnumMap<>(DienstArt.class);

        for (Dienst dienst : alleDienste) {
            if (dienst.getArt() != null) {
                verteilung.merge(dienst.getArt(), 1, Integer::sum);
            }
        }

        return verteilung;
    }

    /**
     * Berechnet die Verteilung nach Wochentagen.
     */
    private Map<String, Integer> berechneWochentagVerteilung(List<Dienst> alleDienste) {
        Map<String, Integer> verteilung = new LinkedHashMap<>();
        // Initialisieren mit allen Wochentagen in richtiger Reihenfolge
        verteilung.put("Mo", 0);
        verteilung.put("Di", 0);
        verteilung.put("Mi", 0);
        verteilung.put("Do", 0);
        verteilung.put("Fr", 0);
        verteilung.put("Sa", 0);
        verteilung.put("So", 0);

        for (Dienst dienst : alleDienste) {
            if (dienst.getDatum() != null) {
                DayOfWeek dow = dienst.getDatum().getDayOfWeek();
                String kuerzel = dow.getDisplayName(TextStyle.SHORT, Locale.GERMAN);
                // Normalisieren (z.B. "Mo." -> "Mo")
                kuerzel = kuerzel.replace(".", "");
                verteilung.merge(kuerzel, 1, Integer::sum);
            }
        }

        return verteilung;
    }

    /**
     * Erstellt eine Liste der offenen (nicht zugewiesenen) Dienste.
     */
    private List<OffenerDienst> berechneOffeneDiensteListe(List<Dienstplan> dienstplaene) {
        List<OffenerDienst> result = new ArrayList<>();
        DateTimeFormatter wochentagFormat = DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN);

        for (Dienstplan dp : dienstplaene) {
            for (Dienst dienst : dp.getDienste()) {
                if (dienst.istOffen()) {
                    String wochentag = dienst.getDatum() != null ?
                            dienst.getDatum().format(wochentagFormat) : "";

                    result.add(new OffenerDienst(
                            dienst.getDatum(),
                            wochentag,
                            dienst.getArt() != null ? dienst.getArt().getVollName() : "",
                            dp.getName()
                    ));
                }
            }
        }

        // Sortieren nach Datum
        result.sort(Comparator.comparing(OffenerDienst::datum, Comparator.nullsLast(Comparator.naturalOrder())));

        return result;
    }

    /**
     * Erkennt Konflikte (Personen mit mehreren Diensten am selben Tag).
     */
    private List<Konflikt> berechneKonflikte(List<Dienst> alleDienste) {
        List<Konflikt> result = new ArrayList<>();

        // Gruppieren nach Person und Datum
        Map<String, List<Dienst>> gruppiert = new HashMap<>();

        for (Dienst dienst : alleDienste) {
            if (dienst.getPersonId() != null && dienst.getDatum() != null) {
                String key = dienst.getPersonId() + "_" + dienst.getDatum();
                gruppiert.computeIfAbsent(key, k -> new ArrayList<>()).add(dienst);
            }
        }

        // Konflikte finden (mehr als 1 Dienst pro Person pro Tag)
        for (List<Dienst> dienste : gruppiert.values()) {
            if (dienste.size() > 1) {
                Dienst erster = dienste.get(0);
                String details = dienste.stream()
                        .map(d -> d.getArt() != null ? d.getArt().getKurzName() : "?")
                        .collect(Collectors.joining(", "));

                result.add(new Konflikt(
                        erster.getDatum(),
                        erster.getPersonName(),
                        dienste.size(),
                        details
                ));
            }
        }

        // Sortieren nach Datum
        result.sort(Comparator.comparing(Konflikt::datum, Comparator.nullsLast(Comparator.naturalOrder())));

        return result;
    }

    /**
     * Berechnet den Fairness-Trend über mehrere Monate.
     */
    public Map<Long, List<MonatlicheErfuellung>> berechneFairnessTrend(
            YearMonth von, YearMonth bis) throws SQLException {

        Map<Long, List<MonatlicheErfuellung>> result = new HashMap<>();
        List<Person> personen = personDAO.findAll();

        for (Person person : personen) {
            List<MonatlicheErfuellung> trend = new ArrayList<>();
            List<WunschStatistik> historie = fairnessHistorieDAO.findByPersonId(person.getId());

            for (WunschStatistik ws : historie) {
                if (ws.getMonat() != null &&
                        !ws.getMonat().isBefore(von) &&
                        !ws.getMonat().isAfter(bis)) {

                    double quote = ws.getGesamtErfuellungsquote() * 100;
                    trend.add(new MonatlicheErfuellung(
                            person.getId(),
                            person.getName(),
                            ws.getMonat(),
                            quote
                    ));
                }
            }

            // Sortieren nach Monat
            trend.sort(Comparator.comparing(MonatlicheErfuellung::monat));

            if (!trend.isEmpty()) {
                result.put(person.getId(), trend);
            }
        }

        return result;
    }

    /**
     * Exportiert die Statistiken in eine Excel-Datei.
     */
    public void exportStatistiken(File file, YearMonth von, YearMonth bis) throws SQLException, IOException {
        logger.info("Exportiere Statistiken nach {}", file.getAbsolutePath());

        GesamtStatistik stats = berechneGesamtStatistik(von, bis);

        try (Workbook workbook = new XSSFWorkbook()) {
            // Übersicht Sheet
            createUebersichtSheet(workbook, stats, von, bis);

            // Wunscherfüllung Sheet
            createWunscherfuellungSheet(workbook, stats);

            // Dienst-Verteilung Sheet
            createDienstVerteilungSheet(workbook, stats);

            // Offene Dienste Sheet
            createOffeneDiensteSheet(workbook, stats);

            // Konflikte Sheet
            createKonflikteSheet(workbook, stats);

            // Datei speichern
            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
        }

        logger.info("Export erfolgreich abgeschlossen");
    }

    private void createUebersichtSheet(Workbook workbook, GesamtStatistik stats,
                                        YearMonth von, YearMonth bis) {
        Sheet sheet = workbook.createSheet("Übersicht");
        int rowNum = 0;

        // Titel
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Statistik-Übersicht");

        // Zeitraum
        Row zeitraumRow = sheet.createRow(rowNum++);
        zeitraumRow.createCell(0).setCellValue("Zeitraum:");
        zeitraumRow.createCell(1).setCellValue(von + " bis " + bis);

        rowNum++; // Leerzeile

        // Kennzahlen
        createKennzahlRow(sheet, rowNum++, "Gesamte Dienste:", stats.gesamtDienste());
        createKennzahlRow(sheet, rowNum++, "Zugewiesene Dienste:", stats.zugewieseneDienste());
        createKennzahlRow(sheet, rowNum++, "Offene Dienste:", stats.offeneDienste());
        createKennzahlRow(sheet, rowNum++, "Abdeckungsgrad:", String.format("%.1f%%", stats.abdeckungsgrad()));
        createKennzahlRow(sheet, rowNum++, "Durchschn. Wunscherfüllung:",
                String.format("%.1f%%", stats.durchschnittlicheWunscherfuellung()));
        createKennzahlRow(sheet, rowNum++, "Anzahl Konflikte:", stats.anzahlKonflikte());

        // Spaltenbreite anpassen
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void createKennzahlRow(Sheet sheet, int rowNum, String label, Object value) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        if (value instanceof Number) {
            row.createCell(1).setCellValue(((Number) value).doubleValue());
        } else {
            row.createCell(1).setCellValue(value.toString());
        }
    }

    private void createWunscherfuellungSheet(Workbook workbook, GesamtStatistik stats) {
        Sheet sheet = workbook.createSheet("Wunscherfüllung");
        int rowNum = 0;

        // Header
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.createCell(0).setCellValue("Person");
        headerRow.createCell(1).setCellValue("Freiwünsche (erfüllt/gesamt)");
        headerRow.createCell(2).setCellValue("Freiwunsch-Quote %");
        headerRow.createCell(3).setCellValue("Dienstwünsche (erfüllt/gesamt)");
        headerRow.createCell(4).setCellValue("Dienstwunsch-Quote %");
        headerRow.createCell(5).setCellValue("Gesamt-Quote %");

        // Daten
        for (PersonWunschStatistik pw : stats.personWunschStatistiken()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(pw.personName());
            row.createCell(1).setCellValue(pw.erfuellteFreiwuensche() + "/" + pw.gesamtFreiwuensche());
            row.createCell(2).setCellValue(pw.freiwunschQuote());
            row.createCell(3).setCellValue(pw.erfuellteDienstwuensche() + "/" + pw.gesamtDienstwuensche());
            row.createCell(4).setCellValue(pw.dienstwunschQuote());
            row.createCell(5).setCellValue(pw.erfuellungsQuote());
        }

        // Spaltenbreite anpassen
        for (int i = 0; i < 6; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createDienstVerteilungSheet(Workbook workbook, GesamtStatistik stats) {
        Sheet sheet = workbook.createSheet("Dienst-Verteilung");
        int rowNum = 0;

        // Header
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.createCell(0).setCellValue("Person");
        headerRow.createCell(1).setCellValue("Soll");
        headerRow.createCell(2).setCellValue("Ist");
        headerRow.createCell(3).setCellValue("Differenz");
        headerRow.createCell(4).setCellValue("24h-Dienste");
        headerRow.createCell(5).setCellValue("Visiten");
        headerRow.createCell(6).setCellValue("DaVinci");

        // Daten
        for (PersonDienstStatistik pd : stats.personDienstStatistiken()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(pd.personName());
            row.createCell(1).setCellValue(pd.sollDienste());
            row.createCell(2).setCellValue(pd.istDienste());
            row.createCell(3).setCellValue(pd.istDienste() - pd.sollDienste());
            row.createCell(4).setCellValue(pd.dienste24h());
            row.createCell(5).setCellValue(pd.diensteVisiten());
            row.createCell(6).setCellValue(pd.diensteDavinci());
        }

        // Spaltenbreite anpassen
        for (int i = 0; i < 7; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createOffeneDiensteSheet(Workbook workbook, GesamtStatistik stats) {
        Sheet sheet = workbook.createSheet("Offene Dienste");
        int rowNum = 0;

        // Header
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.createCell(0).setCellValue("Datum");
        headerRow.createCell(1).setCellValue("Wochentag");
        headerRow.createCell(2).setCellValue("Dienstart");
        headerRow.createCell(3).setCellValue("Dienstplan");

        DateTimeFormatter datumFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        // Daten
        for (OffenerDienst od : stats.offeneDiensteListe()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(od.datum() != null ? od.datum().format(datumFormat) : "");
            row.createCell(1).setCellValue(od.wochentag());
            row.createCell(2).setCellValue(od.dienstArt());
            row.createCell(3).setCellValue(od.dienstplanName());
        }

        // Spaltenbreite anpassen
        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createKonflikteSheet(Workbook workbook, GesamtStatistik stats) {
        Sheet sheet = workbook.createSheet("Konflikte");
        int rowNum = 0;

        // Header
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.createCell(0).setCellValue("Datum");
        headerRow.createCell(1).setCellValue("Person");
        headerRow.createCell(2).setCellValue("Anzahl Dienste");
        headerRow.createCell(3).setCellValue("Details");

        DateTimeFormatter datumFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        // Daten
        for (Konflikt k : stats.konflikte()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(k.datum() != null ? k.datum().format(datumFormat) : "");
            row.createCell(1).setCellValue(k.personName());
            row.createCell(2).setCellValue(k.anzahlDienste());
            row.createCell(3).setCellValue(k.details());
        }

        // Spaltenbreite anpassen
        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // Record-Klassen für Statistik-Daten

    public record GesamtStatistik(
            int gesamtDienste,
            int zugewieseneDienste,
            int offeneDienste,
            double abdeckungsgrad,
            double durchschnittlicheWunscherfuellung,
            int anzahlKonflikte,
            List<String> benachteiligtePersonen,
            List<PersonWunschStatistik> personWunschStatistiken,
            List<PersonDienstStatistik> personDienstStatistiken,
            Map<DienstArt, Integer> dienstArtenVerteilung,
            Map<String, Integer> wochentagVerteilung,
            List<OffenerDienst> offeneDiensteListe,
            List<Konflikt> konflikte
    ) {}

    public record PersonWunschStatistik(
            Long personId,
            String personName,
            int gesamtFreiwuensche,
            int erfuellteFreiwuensche,
            double freiwunschQuote,
            int gesamtDienstwuensche,
            int erfuellteDienstwuensche,
            double dienstwunschQuote,
            double erfuellungsQuote
    ) {}

    public record PersonDienstStatistik(
            Long personId,
            String personName,
            int sollDienste,
            int istDienste,
            int dienste24h,
            int diensteVisiten,
            int diensteDavinci
    ) {}

    public record OffenerDienst(
            LocalDate datum,
            String wochentag,
            String dienstArt,
            String dienstplanName
    ) {}

    public record Konflikt(
            LocalDate datum,
            String personName,
            int anzahlDienste,
            String details
    ) {}

    public record MonatlicheErfuellung(
            Long personId,
            String personName,
            YearMonth monat,
            double erfuellungsQuote
    ) {}
}
