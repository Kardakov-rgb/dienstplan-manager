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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service zum Exportieren von fertigen Dienstplänen als Excel-Datei.
 *
 * Erzeugt eine Excel-Datei mit:
 * - Übersicht: Personen x Tage mit Dienstkürzel
 * - Statistik: Dienstanzahl pro Person
 * - Wunscherfüllung: Statistik falls Wünsche vorhanden
 */
public class ExcelDienstplanExporter {

    private static final Logger logger = LoggerFactory.getLogger(ExcelDienstplanExporter.class);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter WOCHENTAG_FORMAT = DateTimeFormatter.ofPattern("EE");

    /**
     * Exportiert einen Dienstplan als Excel-Datei.
     *
     * @param dienstplan Der zu exportierende Dienstplan
     * @param personen Liste aller Personen (für vollständige Übersicht)
     * @param zielPfad Pfad für die Excel-Datei
     * @throws IOException bei Schreibfehlern
     */
    public void exportiereDienstplan(Dienstplan dienstplan, List<Person> personen,
                                      Path zielPfad) throws IOException {
        exportiereDienstplan(dienstplan, personen, null, zielPfad);
    }

    /**
     * Exportiert einen Dienstplan als Excel-Datei mit Wunschstatistik.
     *
     * @param dienstplan Der zu exportierende Dienstplan
     * @param personen Liste aller Personen
     * @param wunschStatistiken Statistiken zur Wunscherfüllung (optional)
     * @param zielPfad Pfad für die Excel-Datei
     * @throws IOException bei Schreibfehlern
     */
    public void exportiereDienstplan(Dienstplan dienstplan, List<Person> personen,
                                      Map<Long, WunschStatistik> wunschStatistiken,
                                      Path zielPfad) throws IOException {
        logger.info("Exportiere Dienstplan {} nach {}", dienstplan.getName(), zielPfad);

        YearMonth monat = dienstplan.getMonat();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // Dienstplan-Übersicht erstellen
            createDienstplanSheet(workbook, dienstplan, personen, monat);

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
     * Erstellt das Haupt-Sheet mit der Dienstplan-Übersicht.
     */
    private void createDienstplanSheet(XSSFWorkbook workbook, Dienstplan dienstplan,
                                        List<Person> personen, YearMonth monat) {
        XSSFSheet sheet = workbook.createSheet("Dienstplan");

        // Styles erstellen
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle personStyle = createPersonStyle(workbook);
        CellStyle wochenendStyle = createWochenendStyle(workbook);
        CellStyle dienstStyle = createDienstStyle(workbook);
        CellStyle leerStyle = createLeerStyle(workbook);

        // Dienste nach Person und Datum gruppieren
        Map<Long, Map<LocalDate, Dienst>> diensteNachPersonUndDatum = new HashMap<>();
        for (Dienst dienst : dienstplan.getDienste()) {
            if (dienst.getPersonId() != null) {
                diensteNachPersonUndDatum
                    .computeIfAbsent(dienst.getPersonId(), k -> new HashMap<>())
                    .put(dienst.getDatum(), dienst);
            }
        }

        int daysInMonth = monat.lengthOfMonth();

        // Header-Zeile 1: Wochentage
        Row wochentagRow = sheet.createRow(0);
        Cell eckeCell = wochentagRow.createCell(0);
        eckeCell.setCellStyle(headerStyle);
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate datum = monat.atDay(day);
            Cell cell = wochentagRow.createCell(day);
            cell.setCellValue(datum.format(WOCHENTAG_FORMAT));
            cell.setCellStyle(isWochenende(datum) ? wochenendStyle : headerStyle);
        }

        // Header-Zeile 2: Datum
        Row datumRow = sheet.createRow(1);
        Cell personHeaderCell = datumRow.createCell(0);
        personHeaderCell.setCellValue("Person");
        personHeaderCell.setCellStyle(headerStyle);
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate datum = monat.atDay(day);
            Cell cell = datumRow.createCell(day);
            cell.setCellValue(day);
            cell.setCellStyle(isWochenende(datum) ? wochenendStyle : headerStyle);
        }

        // Datenzeilen für jede Person
        int rowNum = 2;
        for (Person person : personen) {
            Row row = sheet.createRow(rowNum);

            // Personenname
            Cell nameCell = row.createCell(0);
            nameCell.setCellValue(person.getName());
            nameCell.setCellStyle(personStyle);

            // Dienste für jeden Tag
            Map<LocalDate, Dienst> personDienste = diensteNachPersonUndDatum.getOrDefault(person.getId(), new HashMap<>());

            for (int day = 1; day <= daysInMonth; day++) {
                LocalDate datum = monat.atDay(day);
                Cell cell = row.createCell(day);

                Dienst dienst = personDienste.get(datum);
                if (dienst != null) {
                    cell.setCellValue(dienst.getArt().getKurzName());
                    cell.setCellStyle(dienstStyle);
                } else if (isWochenende(datum)) {
                    cell.setCellStyle(wochenendStyle);
                } else {
                    cell.setCellStyle(leerStyle);
                }
            }

            rowNum++;
        }

        // Spaltenbreiten anpassen
        sheet.setColumnWidth(0, 20 * 256);
        for (int col = 1; col <= daysInMonth; col++) {
            sheet.setColumnWidth(col, 5 * 256);
        }

        // Zeilen fixieren (Header)
        sheet.createFreezePane(1, 2);
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
        String[] headers = {"Person", "24h", "Spät", "Visten", "Gesamt"};
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
            long spaet = counts.getOrDefault(DienstArt.SPAET, 0L);
            long visten = counts.getOrDefault(DienstArt.VISTEN, 0L);
            long gesamt = dienst24h + spaet + visten;

            row.createCell(1).setCellValue(dienst24h);
            row.createCell(2).setCellValue(spaet);
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
            sheet.setColumnWidth(i, 10 * 256);
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

    // Style-Erstellung

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

    private CellStyle createPersonStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createWochenendStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createDienstStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createLeerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
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
        return datum.getDayOfWeek().getValue() >= 6;
    }

    /**
     * Generiert einen Standard-Dateinamen für den Export.
     */
    public static String generateDefaultFileName(Dienstplan dienstplan) {
        return String.format("Dienstplan_%s.xlsx",
            dienstplan.getMonat().format(DateTimeFormatter.ofPattern("yyyy-MM")));
    }
}
