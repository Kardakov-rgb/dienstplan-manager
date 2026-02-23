package de.dienstplan.service;

import de.dienstplan.model.Person;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
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
import java.util.List;

/**
 * Service zum Generieren von Excel-Vorlagen für den Wunsch-Import.
 *
 * Erzeugt eine Excel-Datei mit:
 * - Header-Zeile mit allen Tagen des Monats (Format: dd.MM.yyyy)
 * - Eine Zeile pro Person mit vorausgefülltem Namen
 * - Dropdown-Validierung für Wunschtypen (U/F/D)
 * - Formatierung und Anleitung
 */
public class ExcelTemplateGenerator {

    private static final Logger logger = LoggerFactory.getLogger(ExcelTemplateGenerator.class);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter MONAT_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy");

    private static final String SHEET_NAME = "Wünsche";
    private static final String ANLEITUNG_SHEET = "Anleitung";

    // Wunschtyp-Optionen für Dropdown
    private static final String[] WUNSCH_OPTIONEN = {"", "U", "F", "D"};

    /**
     * Generiert eine Excel-Vorlage für den angegebenen Monat.
     *
     * @param personen Liste der Personen (Namen werden in Spalte A eingetragen)
     * @param monat Der Monat für die Vorlage
     * @param zielPfad Pfad für die generierte Excel-Datei
     * @throws IOException bei Schreibfehlern
     */
    public void generiereVorlage(List<Person> personen, YearMonth monat, Path zielPfad) throws IOException {
        logger.info("Generiere Excel-Vorlage für {} mit {} Personen", monat, personen.size());

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // Haupt-Sheet erstellen
            XSSFSheet sheet = workbook.createSheet(SHEET_NAME);

            // Styles erstellen
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle personStyle = createPersonStyle(workbook);
            CellStyle wochenendStyle = createWochenendStyle(workbook);
            CellStyle inputStyle = createInputStyle(workbook);

            // Header-Zeile mit Datums-Spalten erstellen
            createHeaderRow(sheet, monat, headerStyle, wochenendStyle);

            // Personen-Zeilen erstellen
            createPersonRows(sheet, personen, monat, personStyle, inputStyle, wochenendStyle);

            // Dropdown-Validierung für Wunschtypen hinzufügen
            addDataValidation(sheet, personen.size(), monat.lengthOfMonth());

            // Spaltenbreiten anpassen
            adjustColumnWidths(sheet, monat.lengthOfMonth());

            // Anleitung-Sheet erstellen
            createAnleitungSheet(workbook, monat);

            // Datei speichern
            try (OutputStream outputStream = new FileOutputStream(zielPfad.toFile())) {
                workbook.write(outputStream);
            }

            logger.info("Excel-Vorlage erfolgreich erstellt: {}", zielPfad);
        }
    }

    /**
     * Erstellt die Header-Zeile mit Datums-Spalten.
     */
    private void createHeaderRow(XSSFSheet sheet, YearMonth monat, CellStyle headerStyle, CellStyle wochenendStyle) {
        Row headerRow = sheet.createRow(0);

        // Spalte A: "Person"
        Cell personHeaderCell = headerRow.createCell(0);
        personHeaderCell.setCellValue("Person");
        personHeaderCell.setCellStyle(headerStyle);

        // Spalten für jeden Tag des Monats
        int daysInMonth = monat.lengthOfMonth();
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate datum = monat.atDay(day);
            Cell cell = headerRow.createCell(day);
            cell.setCellValue(datum.format(DATE_FORMAT));

            // Wochenende anders formatieren
            if (isWochenende(datum)) {
                cell.setCellStyle(wochenendStyle);
            } else {
                cell.setCellStyle(headerStyle);
            }
        }
    }

    /**
     * Erstellt die Datenzeilen für jede Person.
     */
    private void createPersonRows(XSSFSheet sheet, List<Person> personen, YearMonth monat,
                                   CellStyle personStyle, CellStyle inputStyle, CellStyle wochenendStyle) {
        int rowNum = 1;
        for (Person person : personen) {
            Row row = sheet.createRow(rowNum);

            // Spalte A: Personenname
            Cell nameCell = row.createCell(0);
            nameCell.setCellValue(person.getName());
            nameCell.setCellStyle(personStyle);

            // Leere Zellen für jeden Tag (zum Ausfüllen)
            int daysInMonth = monat.lengthOfMonth();
            for (int day = 1; day <= daysInMonth; day++) {
                Cell cell = row.createCell(day);
                LocalDate datum = monat.atDay(day);

                if (isWochenende(datum)) {
                    cell.setCellStyle(wochenendStyle);
                } else {
                    cell.setCellStyle(inputStyle);
                }
            }

            rowNum++;
        }
    }

    /**
     * Fügt Dropdown-Validierung für die Wunschtypen hinzu.
     */
    private void addDataValidation(XSSFSheet sheet, int personenAnzahl, int tageImMonat) {
        if (personenAnzahl == 0 || tageImMonat == 0) return;

        XSSFDataValidationHelper validationHelper = new XSSFDataValidationHelper(sheet);

        // Bereich: Alle Datenzellen (ab Zeile 2, ab Spalte B)
        CellRangeAddressList addressList = new CellRangeAddressList(
            1, personenAnzahl, // Zeilen (1-basiert, ohne Header)
            1, tageImMonat     // Spalten (1-basiert, ohne Namen-Spalte)
        );

        // Dropdown mit U, F, D, oder leer
        DataValidationConstraint constraint = validationHelper.createExplicitListConstraint(WUNSCH_OPTIONEN);

        DataValidation validation = validationHelper.createValidation(constraint, addressList);
        validation.setShowErrorBox(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.WARNING);
        validation.createErrorBox("Ungültiger Wert",
            "Bitte verwenden Sie:\nU = Urlaub\nF = Freiwunsch\nD = Dienstwunsch\noder leer lassen");

        sheet.addValidationData(validation);
    }

    /**
     * Passt die Spaltenbreiten an.
     */
    private void adjustColumnWidths(XSSFSheet sheet, int tageImMonat) {
        // Spalte A (Personennamen) breiter
        sheet.setColumnWidth(0, 20 * 256);

        // Datum-Spalten schmaler
        for (int col = 1; col <= tageImMonat; col++) {
            sheet.setColumnWidth(col, 11 * 256);
        }
    }

    /**
     * Erstellt das Anleitung-Sheet.
     */
    private void createAnleitungSheet(XSSFWorkbook workbook, YearMonth monat) {
        XSSFSheet sheet = workbook.createSheet(ANLEITUNG_SHEET);

        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle textStyle = createTextStyle(workbook);

        int rowNum = 0;

        // Titel
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Anleitung: Wunsch-Vorlage für " + monat.format(MONAT_FORMAT));
        titleCell.setCellStyle(titleStyle);

        rowNum++; // Leerzeile

        // Anleitungstext
        String[] anleitungsTexte = {
            "So füllen Sie die Vorlage aus:",
            "",
            "1. Wechseln Sie zum Sheet 'Wünsche'",
            "2. Tragen Sie für jeden Tag Ihren Wunsch ein:",
            "",
            "   U = Urlaub (MUSS berücksichtigt werden)",
            "   F = Freiwunsch (wird möglichst berücksichtigt)",
            "   D = Dienstwunsch für Vordergrund (wird möglichst berücksichtigt)",
            "",
            "3. Lassen Sie Zellen leer, wenn kein Wunsch besteht",
            "4. Speichern Sie die Datei und senden Sie sie zurück",
            "",
            "Hinweise:",
            "- Urlaubstage werden garantiert eingehalten",
            "- Freiwünsche und Dienstwünsche werden nach Möglichkeit erfüllt",
            "- Bei Konflikten wird fair nach historischer Erfüllung priorisiert",
            "- Wochenend-Spalten sind grau hinterlegt"
        };

        for (String text : anleitungsTexte) {
            Row row = sheet.createRow(rowNum++);
            Cell cell = row.createCell(0);
            cell.setCellValue(text);
            cell.setCellStyle(textStyle);
        }

        // Spaltenbreite anpassen
        sheet.setColumnWidth(0, 60 * 256);
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
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createWochenendStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createPersonStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createInputStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createTitleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    private CellStyle createTextStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        return style;
    }

    /**
     * Prüft ob ein Datum auf ein Wochenende fällt.
     */
    private boolean isWochenende(LocalDate datum) {
        return datum.getDayOfWeek().getValue() >= 6;
    }

    /**
     * Generiert einen Standard-Dateinamen für die Vorlage.
     */
    public static String generateDefaultFileName(YearMonth monat) {
        return String.format("Wunsch-Vorlage_%s.xlsx", monat.format(DateTimeFormatter.ofPattern("yyyy-MM")));
    }
}
