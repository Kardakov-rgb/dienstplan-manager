package de.dienstplan.service;

import de.dienstplan.model.MonatsWunsch;
import de.dienstplan.model.Person;
import de.dienstplan.model.WunschTyp;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Service zum Importieren von MonatsWünschen aus Excel-Dateien.
 *
 * Excel-Format:
 * - Zeile 1: Header mit Datum (dd.MM.yyyy) in jeder Spalte
 * - Spalte A: Personennamen
 * - Zellen: U (Urlaub), F (Freiwunsch), D (Dienstwunsch) oder leer
 */
public class ExcelWunschImporter {

    private static final Logger logger = LoggerFactory.getLogger(ExcelWunschImporter.class);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter ALT_DATE_FORMAT = DateTimeFormatter.ofPattern("d.M.yyyy");

    // Mapping von Person-Namen zu Person-Objekten
    private final Map<String, Person> personenNachName;

    public ExcelWunschImporter(List<Person> personen) {
        this.personenNachName = new HashMap<>();
        for (Person person : personen) {
            // Normalisiere Namen für Matching (Groß-/Kleinschreibung ignorieren, trimmen)
            personenNachName.put(normalizePersonName(person.getName()), person);
        }
    }

    /**
     * Importiert Wünsche aus einer Excel-Datei und gibt ein Vorschau-Ergebnis zurück.
     * Die Wünsche werden noch NICHT in der Datenbank gespeichert.
     *
     * @param excelDatei Pfad zur Excel-Datei (.xlsx)
     * @return ImportResult mit den geparsten Wünschen und eventuellen Fehlern/Warnungen
     */
    public ImportResult importiereVorschau(Path excelDatei) {
        logger.info("Starte Excel-Import von: {}", excelDatei);

        ImportResult result = new ImportResult();

        try (InputStream inputStream = new FileInputStream(excelDatei.toFile());
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);

            if (sheet == null || sheet.getPhysicalNumberOfRows() < 2) {
                result.addFehler("Excel-Datei ist leer oder hat keine Datenzeilen");
                return result;
            }

            // Header-Zeile lesen (Datum-Spalten)
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                result.addFehler("Header-Zeile fehlt");
                return result;
            }

            // Datum-Spalten parsen (ab Spalte 1, Spalte 0 = Personenname)
            Map<Integer, LocalDate> spaltenDatum = parseHeaderRow(headerRow, result);

            if (spaltenDatum.isEmpty()) {
                result.addFehler("Keine gültigen Datum-Spalten im Header gefunden");
                return result;
            }

            // Monat aus dem ersten Datum ermitteln
            LocalDate erstesDatum = spaltenDatum.values().iterator().next();
            YearMonth monat = YearMonth.from(erstesDatum);
            result.setMonat(monat);

            logger.debug("Erkannter Monat: {}, {} Datum-Spalten", monat, spaltenDatum.size());

            // Datenzeilen verarbeiten (ab Zeile 1)
            for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;

                parseDataRow(row, rowNum, spaltenDatum, monat, result);
            }

            logger.info("Import abgeschlossen: {} Wünsche, {} Warnungen, {} Fehler",
                       result.getWuensche().size(), result.getWarnungen().size(), result.getFehler().size());

        } catch (IOException e) {
            logger.error("Fehler beim Lesen der Excel-Datei", e);
            result.addFehler("Fehler beim Lesen der Datei: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unerwarteter Fehler beim Import", e);
            result.addFehler("Unerwarteter Fehler: " + e.getMessage());
        }

        return result;
    }

    /**
     * Parst die Header-Zeile und extrahiert die Datum-Spalten.
     */
    private Map<Integer, LocalDate> parseHeaderRow(Row headerRow, ImportResult result) {
        Map<Integer, LocalDate> spaltenDatum = new LinkedHashMap<>();

        for (int col = 1; col <= headerRow.getLastCellNum(); col++) {
            Cell cell = headerRow.getCell(col);
            if (cell == null) continue;

            LocalDate datum = parseDatumFromCell(cell);
            if (datum != null) {
                spaltenDatum.put(col, datum);
            } else {
                String cellValue = getCellValueAsString(cell);
                if (!cellValue.isEmpty()) {
                    result.addWarnung(String.format("Spalte %d: '%s' konnte nicht als Datum geparst werden",
                                     col + 1, cellValue));
                }
            }
        }

        return spaltenDatum;
    }

    /**
     * Parst eine Datenzeile (Person + Wünsche).
     */
    private void parseDataRow(Row row, int rowNum, Map<Integer, LocalDate> spaltenDatum,
                               YearMonth monat, ImportResult result) {
        // Personenname aus Spalte 0
        Cell nameCell = row.getCell(0);
        if (nameCell == null) return;

        String personName = getCellValueAsString(nameCell).trim();
        if (personName.isEmpty()) return;

        // Person-Matching
        String normalizedName = normalizePersonName(personName);
        Person person = personenNachName.get(normalizedName);

        if (person == null) {
            // Versuche Teil-Matching
            person = findPersonByPartialMatch(personName);
            if (person == null) {
                result.addWarnung(String.format("Zeile %d: Person '%s' nicht gefunden", rowNum + 1, personName));
                return;
            }
            result.addWarnung(String.format("Zeile %d: '%s' zugeordnet zu '%s'",
                             rowNum + 1, personName, person.getName()));
        }

        // Wunsch-Zellen verarbeiten
        for (Map.Entry<Integer, LocalDate> entry : spaltenDatum.entrySet()) {
            int col = entry.getKey();
            LocalDate datum = entry.getValue();

            // Prüfen ob das Datum im richtigen Monat liegt
            if (!YearMonth.from(datum).equals(monat)) {
                continue; // Überspringen wenn anderer Monat
            }

            Cell cell = row.getCell(col);
            if (cell == null) continue;

            String wert = getCellValueAsString(cell).trim().toUpperCase();
            if (wert.isEmpty()) continue;

            WunschTyp typ = WunschTyp.fromKuerzel(wert);
            if (typ != null) {
                MonatsWunsch wunsch = new MonatsWunsch(person.getId(), person.getName(), datum, typ);
                wunsch.setMonat(monat);
                result.addWunsch(wunsch);
            } else {
                result.addWarnung(String.format("Zeile %d, Spalte %d: Unbekannter Wunschtyp '%s'",
                                 rowNum + 1, col + 1, wert));
            }
        }
    }

    /**
     * Parst ein Datum aus einer Zelle (unterstützt String und numerische Datumswerte).
     */
    private LocalDate parseDatumFromCell(Cell cell) {
        if (cell == null) return null;

        try {
            // Numerisches Datum (Excel-Datumswert)
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }

            // String-Datum
            String cellValue = getCellValueAsString(cell).trim();
            if (cellValue.isEmpty()) return null;

            // Versuche verschiedene Formate
            try {
                return LocalDate.parse(cellValue, DATE_FORMAT);
            } catch (DateTimeParseException e1) {
                try {
                    return LocalDate.parse(cellValue, ALT_DATE_FORMAT);
                } catch (DateTimeParseException e2) {
                    return null;
                }
            }

        } catch (Exception e) {
            logger.debug("Fehler beim Parsen von Datum: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Gibt den Zellwert als String zurück.
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDate date = cell.getLocalDateTimeCellValue().toLocalDate();
                    return date.format(DATE_FORMAT);
                }
                // Numerischen Wert ohne Dezimalstellen
                double value = cell.getNumericCellValue();
                if (value == Math.floor(value)) {
                    return String.valueOf((long) value);
                }
                return String.valueOf(value);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        return "";
                    }
                }
            default:
                return "";
        }
    }

    /**
     * Normalisiert einen Personennamen für das Matching.
     */
    private String normalizePersonName(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase()
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
            .replace("ß", "ss");
    }

    /**
     * Sucht eine Person durch Teil-Matching (wenn exakte Übereinstimmung fehlschlägt).
     */
    private Person findPersonByPartialMatch(String searchName) {
        String normalized = normalizePersonName(searchName);

        for (Map.Entry<String, Person> entry : personenNachName.entrySet()) {
            // Prüfe ob einer der Namen im anderen enthalten ist
            if (entry.getKey().contains(normalized) || normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Ergebnis eines Excel-Imports.
     */
    public static class ImportResult {
        private YearMonth monat;
        private final List<MonatsWunsch> wuensche = new ArrayList<>();
        private final List<String> warnungen = new ArrayList<>();
        private final List<String> fehler = new ArrayList<>();

        public YearMonth getMonat() {
            return monat;
        }

        public void setMonat(YearMonth monat) {
            this.monat = monat;
        }

        public List<MonatsWunsch> getWuensche() {
            return wuensche;
        }

        public void addWunsch(MonatsWunsch wunsch) {
            this.wuensche.add(wunsch);
        }

        public List<String> getWarnungen() {
            return warnungen;
        }

        public void addWarnung(String warnung) {
            this.warnungen.add(warnung);
        }

        public List<String> getFehler() {
            return fehler;
        }

        public void addFehler(String fehler) {
            this.fehler.add(fehler);
        }

        public boolean hatFehler() {
            return !fehler.isEmpty();
        }

        public boolean hatWarnungen() {
            return !warnungen.isEmpty();
        }

        public boolean istErfolgreich() {
            return fehler.isEmpty() && !wuensche.isEmpty();
        }

        /**
         * Gruppiert die Wünsche nach Person.
         */
        public Map<String, List<MonatsWunsch>> getWuenscheNachPerson() {
            Map<String, List<MonatsWunsch>> grouped = new LinkedHashMap<>();
            for (MonatsWunsch wunsch : wuensche) {
                grouped.computeIfAbsent(wunsch.getPersonName(), k -> new ArrayList<>())
                       .add(wunsch);
            }
            return grouped;
        }

        /**
         * Zählt die Wünsche nach Typ.
         */
        public Map<WunschTyp, Integer> getAnzahlNachTyp() {
            Map<WunschTyp, Integer> counts = new EnumMap<>(WunschTyp.class);
            for (WunschTyp typ : WunschTyp.values()) {
                counts.put(typ, 0);
            }
            for (MonatsWunsch wunsch : wuensche) {
                counts.merge(wunsch.getTyp(), 1, Integer::sum);
            }
            return counts;
        }

        /**
         * Gibt eine Zusammenfassung des Imports zurück.
         */
        public String getZusammenfassung() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Import für %s:\n", monat));
            sb.append(String.format("- %d Wünsche importiert\n", wuensche.size()));

            Map<WunschTyp, Integer> counts = getAnzahlNachTyp();
            sb.append(String.format("  - Urlaub: %d\n", counts.get(WunschTyp.URLAUB)));
            sb.append(String.format("  - Freiwünsche: %d\n", counts.get(WunschTyp.FREIWUNSCH)));
            sb.append(String.format("  - Dienstwünsche: %d\n", counts.get(WunschTyp.DIENSTWUNSCH)));

            sb.append(String.format("- %d Personen betroffen\n", getWuenscheNachPerson().size()));

            if (!warnungen.isEmpty()) {
                sb.append(String.format("- %d Warnungen\n", warnungen.size()));
            }
            if (!fehler.isEmpty()) {
                sb.append(String.format("- %d Fehler\n", fehler.size()));
            }

            return sb.toString();
        }
    }
}
