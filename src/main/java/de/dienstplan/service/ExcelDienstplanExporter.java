package de.dienstplan.service;

import de.dienstplan.model.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service zum Exportieren von fertigen Dienstplänen als Excel-Datei.
 *
 * Erzeugt eine Excel-Datei mit:
 * - Übersicht: Tage x Dienstarten Matrix mit Personennamen
 * - Spalten: Datum, Vordergrund (24h), Visitendienst, DaVinci, Hintergrund, Urlaub, Kann nicht
 */
public class ExcelDienstplanExporter {

    private static final Logger logger = LoggerFactory.getLogger(ExcelDienstplanExporter.class);

    // Deutsche Datumsformatierung: "Sonntag, 1. Februar 2026"
    private static final DateTimeFormatter DATUM_FORMAT =
        DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN);

    // Spalten-Indizes
    private static final int COL_DATUM = 0;
    private static final int COL_VORDERGRUND = 1;
    private static final int COL_VISITENDIENST = 2;
    private static final int COL_DAVINCI = 3;
    private static final int COL_HINTERGRUND = 4;
    private static final int COL_URLAUB = 5;
    private static final int COL_KANN_NICHT = 6;

    // Spalten-Header
    private static final String[] HEADERS = {
        "Datum", "Vordergrund", "Visitendienst", "DaVinci", "Hintergrund", "Urlaub", "Kann nicht"
    };

    /**
     * Exportiert einen Dienstplan als Excel-Datei (ohne Wünsche).
     */
    public void exportiereDienstplan(Dienstplan dienstplan, List<Person> personen,
                                      Path zielPfad) throws IOException {
        exportiereDienstplan(dienstplan, personen, null, null, zielPfad);
    }

    /**
     * Exportiert einen Dienstplan als Excel-Datei mit Wunschstatistik (Kompatibilität).
     */
    public void exportiereDienstplan(Dienstplan dienstplan, List<Person> personen,
                                      Map<Long, WunschStatistik> wunschStatistiken,
                                      Path zielPfad) throws IOException {
        exportiereDienstplan(dienstplan, personen, null, wunschStatistiken, zielPfad);
    }

    /**
     * Exportiert einen Dienstplan als Excel-Datei mit Wünschen.
     *
     * @param dienstplan Der zu exportierende Dienstplan
     * @param personen Liste aller Personen
     * @param wuensche Liste der Monatswünsche (für Urlaub und Kann nicht)
     * @param wunschStatistiken Statistiken zur Wunscherfüllung (optional)
     * @param zielPfad Pfad für die Excel-Datei
     * @throws IOException bei Schreibfehlern
     */
    public void exportiereDienstplan(Dienstplan dienstplan, List<Person> personen,
                                      List<MonatsWunsch> wuensche,
                                      Map<Long, WunschStatistik> wunschStatistiken,
                                      Path zielPfad) throws IOException {
        logger.info("Exportiere Dienstplan {} nach {}", dienstplan.getName(), zielPfad);

        YearMonth monat = dienstplan.getMonat();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // Dienstplan-Übersicht erstellen (neues Format)
            createDienstplanSheet(workbook, dienstplan, wuensche, monat);

            // Statistik-Sheet erstellen
            createStatistikSheet(workbook, dienstplan, personen);

            // Wunscherfüllungs-Sheet erstellen (falls Statistiken vorhanden)
            if (wunschStatistiken != null && !wunschStatistiken.isEmpty()) {
                createWunscherfuellungSheet(workbook, wunschStatistiken, personen);
            }

            // Datei speichern
            try (OutputStream outputStream = new FileOutputStream(zielPfad.toFile())) {
                workbook.write(outputStream);
            }

            logger.info("Dienstplan erfolgreich exportiert: {}", zielPfad);
        }
    }

    /**
     * Erstellt das Haupt-Sheet mit der Dienstplan-Übersicht im neuen Format.
     * Zeilen = Tage, Spalten = Dienstarten
     */
    private void createDienstplanSheet(XSSFWorkbook workbook, Dienstplan dienstplan,
                                        List<MonatsWunsch> wuensche, YearMonth monat) {
        XSSFSheet sheet = workbook.createSheet("Dienstplan");

        // Styles erstellen
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle normalStyle = createNormalStyle(workbook);
        CellStyle weekendStyle = createWeekendStyle(workbook);
        CellStyle yellowStyle = createYellowStyle(workbook);
        CellStyle yellowWeekendStyle = createYellowWeekendStyle(workbook);

        // Dienste nach Datum gruppieren
        Map<LocalDate, Map<DienstArt, String>> diensteNachDatum = new HashMap<>();
        for (Dienst dienst : dienstplan.getDienste()) {
            if (dienst.getPersonId() != null && dienst.getPersonName() != null) {
                diensteNachDatum
                    .computeIfAbsent(dienst.getDatum(), k -> new EnumMap<>(DienstArt.class))
                    .put(dienst.getArt(), dienst.getPersonName());
            }
        }

        // Wünsche nach Datum und Typ gruppieren
        Map<LocalDate, Map<WunschTyp, List<String>>> wuenscheNachDatum = new HashMap<>();
        if (wuensche != null) {
            for (MonatsWunsch wunsch : wuensche) {
                if (wunsch.getPersonName() != null) {
                    wuenscheNachDatum
                        .computeIfAbsent(wunsch.getDatum(), k -> new EnumMap<>(WunschTyp.class))
                        .computeIfAbsent(wunsch.getTyp(), k -> new ArrayList<>())
                        .add(wunsch.getPersonName());
                }
            }
        }

        int daysInMonth = monat.lengthOfMonth();

        // Header-Zeile erstellen
        Row headerRow = sheet.createRow(0);
        for (int col = 0; col < HEADERS.length; col++) {
            Cell cell = headerRow.createCell(col);
            cell.setCellValue(HEADERS[col]);
            cell.setCellStyle(headerStyle);
        }

        // Datenzeilen für jeden Tag
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate datum = monat.atDay(day);
            Row row = sheet.createRow(day);
            boolean isWeekend = isWochenende(datum);

            // Datum-Spalte
            Cell datumCell = row.createCell(COL_DATUM);
            datumCell.setCellValue(datum.format(DATUM_FORMAT));
            datumCell.setCellStyle(isWeekend ? weekendStyle : normalStyle);

            // Dienste für diesen Tag
            Map<DienstArt, String> tagesDienste = diensteNachDatum.getOrDefault(datum, Collections.emptyMap());
            Map<WunschTyp, List<String>> tagesWuensche = wuenscheNachDatum.getOrDefault(datum, Collections.emptyMap());

            // Vordergrund (24h Dienst)
            createDienstCell(row, COL_VORDERGRUND, tagesDienste.get(DienstArt.DIENST_24H),
                isWeekend, normalStyle, weekendStyle, yellowStyle, yellowWeekendStyle);

            // Visitendienst
            createDienstCell(row, COL_VISITENDIENST, tagesDienste.get(DienstArt.VISTEN),
                isWeekend, normalStyle, weekendStyle, yellowStyle, yellowWeekendStyle);

            // DaVinci
            createDienstCell(row, COL_DAVINCI, tagesDienste.get(DienstArt.DAVINCI),
                isWeekend, normalStyle, weekendStyle, yellowStyle, yellowWeekendStyle);

            // Hintergrund (leer)
            Cell hintergrundCell = row.createCell(COL_HINTERGRUND);
            hintergrundCell.setCellStyle(isWeekend ? weekendStyle : normalStyle);

            // Urlaub
            List<String> urlaubPersonen = tagesWuensche.getOrDefault(WunschTyp.URLAUB, Collections.emptyList());
            createWunschCell(row, COL_URLAUB, urlaubPersonen,
                isWeekend, normalStyle, weekendStyle, yellowStyle, yellowWeekendStyle);

            // Kann nicht (Freiwunsch)
            List<String> kannNichtPersonen = tagesWuensche.getOrDefault(WunschTyp.FREIWUNSCH, Collections.emptyList());
            createWunschCell(row, COL_KANN_NICHT, kannNichtPersonen,
                isWeekend, normalStyle, weekendStyle, yellowStyle, yellowWeekendStyle);
        }

        // Spaltenbreiten anpassen
        sheet.setColumnWidth(COL_DATUM, 35 * 256);           // Breite für volles Datum
        sheet.setColumnWidth(COL_VORDERGRUND, 15 * 256);
        sheet.setColumnWidth(COL_VISITENDIENST, 15 * 256);
        sheet.setColumnWidth(COL_DAVINCI, 15 * 256);
        sheet.setColumnWidth(COL_HINTERGRUND, 15 * 256);
        sheet.setColumnWidth(COL_URLAUB, 20 * 256);
        sheet.setColumnWidth(COL_KANN_NICHT, 20 * 256);

        // Header-Zeile fixieren
        sheet.createFreezePane(0, 1);
    }

    /**
     * Erstellt eine Zelle für einen Dienst.
     */
    private void createDienstCell(Row row, int column, String personName, boolean isWeekend,
                                   CellStyle normalStyle, CellStyle weekendStyle,
                                   CellStyle yellowStyle, CellStyle yellowWeekendStyle) {
        Cell cell = row.createCell(column);
        if (personName != null && !personName.isEmpty()) {
            cell.setCellValue(personName);
            cell.setCellStyle(isWeekend ? yellowWeekendStyle : yellowStyle);
        } else {
            cell.setCellStyle(isWeekend ? weekendStyle : normalStyle);
        }
    }

    /**
     * Erstellt eine Zelle für Wünsche (kann mehrere Personen enthalten).
     */
    private void createWunschCell(Row row, int column, List<String> personen, boolean isWeekend,
                                   CellStyle normalStyle, CellStyle weekendStyle,
                                   CellStyle yellowStyle, CellStyle yellowWeekendStyle) {
        Cell cell = row.createCell(column);
        if (personen != null && !personen.isEmpty()) {
            cell.setCellValue(String.join(", ", personen));
            cell.setCellStyle(isWeekend ? yellowWeekendStyle : yellowStyle);
        } else {
            cell.setCellStyle(isWeekend ? weekendStyle : normalStyle);
        }
    }

    /**
     * Erstellt das Statistik-Sheet.
     */
    private void createStatistikSheet(XSSFWorkbook workbook, Dienstplan dienstplan, List<Person> personen) {
        XSSFSheet sheet = workbook.createSheet("Statistik");

        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle textStyle = createTextStyle(workbook);
        CellStyle zahlStyle = createZahlStyle(workbook);

        // Dienste pro Person und DienstArt zählen
        Map<Long, Map<DienstArt, Long>> diensteCounts = dienstplan.getDienste().stream()
            .filter(d -> d.getPersonId() != null)
            .collect(Collectors.groupingBy(
                Dienst::getPersonId,
                Collectors.groupingBy(Dienst::getArt, Collectors.counting())
            ));

        // Header
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Person", "Vordergrund", "DaVinci", "Visten", "Gesamt"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Datenzeilen
        int rowNum = 1;
        for (Person person : personen) {
            Row row = sheet.createRow(rowNum);

            Cell nameCell = row.createCell(0);
            nameCell.setCellValue(person.getName());
            nameCell.setCellStyle(textStyle);

            Map<DienstArt, Long> counts = diensteCounts.getOrDefault(person.getId(), new EnumMap<>(DienstArt.class));

            long dienst24h = counts.getOrDefault(DienstArt.DIENST_24H, 0L);
            long davinci = counts.getOrDefault(DienstArt.DAVINCI, 0L);
            long visten = counts.getOrDefault(DienstArt.VISTEN, 0L);
            long gesamt = dienst24h + davinci + visten;

            row.createCell(1).setCellValue(dienst24h);
            row.createCell(2).setCellValue(davinci);
            row.createCell(3).setCellValue(visten);
            row.createCell(4).setCellValue(gesamt);

            for (int i = 1; i <= 4; i++) {
                row.getCell(i).setCellStyle(zahlStyle);
            }

            rowNum++;
        }

        // Spaltenbreiten
        sheet.setColumnWidth(0, 20 * 256);
        for (int i = 1; i <= 4; i++) {
            sheet.setColumnWidth(i, 12 * 256);
        }
    }

    /**
     * Erstellt das Wunscherfüllungs-Sheet.
     */
    private void createWunscherfuellungSheet(XSSFWorkbook workbook,
                                              Map<Long, WunschStatistik> statistiken,
                                              List<Person> personen) {
        XSSFSheet sheet = workbook.createSheet("Wunscherfüllung");

        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle textStyle = createTextStyle(workbook);
        CellStyle zahlStyle = createZahlStyle(workbook);
        CellStyle prozentStyle = createProzentStyle(workbook);

        // Header
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Person", "Freiwünsche", "Erfüllt", "Quote", "Dienstwünsche", "Erfüllt", "Quote"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Datenzeilen
        int rowNum = 1;
        for (Person person : personen) {
            WunschStatistik stat = statistiken.get(person.getId());
            if (stat == null) continue;

            Row row = sheet.createRow(rowNum);

            Cell nameCell = row.createCell(0);
            nameCell.setCellValue(person.getName());
            nameCell.setCellStyle(textStyle);

            // Freiwünsche
            row.createCell(1).setCellValue(stat.getAnzahlFreiwuensche());
            row.createCell(2).setCellValue(stat.getErfuellteFreiwuensche());
            row.createCell(3).setCellValue(stat.getFreiwunschErfuellungsquote());

            // Dienstwünsche
            row.createCell(4).setCellValue(stat.getAnzahlDienstwuensche());
            row.createCell(5).setCellValue(stat.getErfuellteDienstwuensche());
            row.createCell(6).setCellValue(stat.getDienstwunschErfuellungsquote());

            for (int i = 1; i <= 2; i++) {
                row.getCell(i).setCellStyle(zahlStyle);
            }
            row.getCell(3).setCellStyle(prozentStyle);
            for (int i = 4; i <= 5; i++) {
                row.getCell(i).setCellStyle(zahlStyle);
            }
            row.getCell(6).setCellStyle(prozentStyle);

            rowNum++;
        }

        // Spaltenbreiten
        sheet.setColumnWidth(0, 20 * 256);
        for (int i = 1; i <= 6; i++) {
            sheet.setColumnWidth(i, 12 * 256);
        }
    }

    // ===== Style-Erstellung =====

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createNormalStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createWeekendStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createYellowStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createYellowWeekendStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createTextStyle(XSSFWorkbook workbook) {
        return workbook.createCellStyle();
    }

    private CellStyle createZahlStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createProzentStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0%"));
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private boolean isWochenende(LocalDate datum) {
        DayOfWeek dayOfWeek = datum.getDayOfWeek();
        // Freitag, Samstag und Sonntag als Wochenende (fett)
        return dayOfWeek == DayOfWeek.FRIDAY ||
               dayOfWeek == DayOfWeek.SATURDAY ||
               dayOfWeek == DayOfWeek.SUNDAY;
    }

    /**
     * Generiert einen Standard-Dateinamen für den Export.
     */
    public static String generateDefaultFileName(Dienstplan dienstplan) {
        return String.format("Dienstplan_%s.xlsx",
            dienstplan.getMonat().format(DateTimeFormatter.ofPattern("yyyy-MM")));
    }
}
