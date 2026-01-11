package de.dienstplan.service;

import de.dienstplan.algorithm.DienstplanGenerator;
import de.dienstplan.database.DienstplanDAO;
import de.dienstplan.database.PersonDAO;
import de.dienstplan.model.Dienstplan;
import de.dienstplan.model.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Service-Klasse für Dienstplan-Operationen.
 * Kapselt die Business-Logik und koordiniert zwischen DAOs und dem Generator.
 */
public class DienstplanService {

    private static final Logger logger = LoggerFactory.getLogger(DienstplanService.class);

    private final PersonDAO personDAO;
    private final DienstplanDAO dienstplanDAO;

    /**
     * Konstruktor mit Dependency Injection
     */
    public DienstplanService(PersonDAO personDAO, DienstplanDAO dienstplanDAO) {
        this.personDAO = personDAO;
        this.dienstplanDAO = dienstplanDAO;
    }

    /**
     * Standard-Konstruktor (erstellt eigene DAOs)
     */
    public DienstplanService() {
        this(new PersonDAO(), new DienstplanDAO());
    }

    /**
     * Generiert einen neuen Dienstplan für den angegebenen Monat.
     * Thread-safe durch Erstellen einer defensiven Kopie der Personenliste.
     *
     * @param monat Der Zielmonat
     * @param progressCallback Optional: Callback für Fortschrittsmeldungen
     * @return Das Ergebnis der Generierung
     * @throws SQLException Bei Datenbankfehlern
     */
    public DienstplanGenerator.DienstplanGenerierungResult generiereDienstplan(
            YearMonth monat,
            Consumer<String> progressCallback) throws SQLException {

        logger.info("Starte Dienstplan-Generierung für {}", monat);

        if (progressCallback != null) {
            progressCallback.accept("Lade Personen...");
        }

        // Thread-safe: Defensive Kopie der Personenliste erstellen
        List<Person> personen = new ArrayList<>(personDAO.findAll());

        if (personen.isEmpty()) {
            logger.warn("Keine Personen für Dienstplan-Generierung vorhanden");
            throw new IllegalStateException("Keine Personen in der Datenbank vorhanden");
        }

        if (progressCallback != null) {
            progressCallback.accept("Generiere Dienstplan mit " + personen.size() + " Personen...");
        }

        // Generator mit defensiver Kopie aufrufen
        DienstplanGenerator generator = new DienstplanGenerator(personen, monat);
        DienstplanGenerator.DienstplanGenerierungResult result = generator.generiereDienstplan();

        if (progressCallback != null) {
            progressCallback.accept(result.getZusammenfassung());
        }

        logger.info("Dienstplan-Generierung abgeschlossen: {}", result.getZusammenfassung());
        return result;
    }

    /**
     * Generiert einen Dienstplan asynchron.
     * Ideal für UI-Operationen, um den Main-Thread nicht zu blockieren.
     *
     * @param monat Der Zielmonat
     * @param progressCallback Optional: Callback für Fortschrittsmeldungen
     * @return CompletableFuture mit dem Generierungsergebnis
     */
    public CompletableFuture<DienstplanGenerator.DienstplanGenerierungResult> generiereDienstplanAsync(
            YearMonth monat,
            Consumer<String> progressCallback) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                return generiereDienstplan(monat, progressCallback);
            } catch (SQLException e) {
                logger.error("Fehler bei asynchroner Dienstplan-Generierung", e);
                throw new RuntimeException("Datenbankfehler bei Generierung", e);
            }
        });
    }

    /**
     * Speichert einen Dienstplan in der Datenbank.
     * Prüft auf Duplikate und aktualisiert bei Bedarf.
     *
     * @param dienstplan Der zu speichernde Dienstplan
     * @param overwriteExisting Wenn true, wird ein bestehender Plan überschrieben
     * @return Der gespeicherte Dienstplan (mit ID)
     * @throws SQLException Bei Datenbankfehlern
     * @throws IllegalStateException Wenn ein Plan existiert und overwriteExisting false ist
     */
    public Dienstplan speichereDienstplan(Dienstplan dienstplan, boolean overwriteExisting) throws SQLException {
        if (dienstplan == null) {
            throw new IllegalArgumentException("Dienstplan darf nicht null sein");
        }

        String name = dienstplan.getName();
        YearMonth monat = dienstplan.getMonat();

        // Prüfen ob bereits existiert
        if (dienstplanDAO.existsByNameAndMonat(name, monat)) {
            if (!overwriteExisting) {
                throw new IllegalStateException(
                    "Ein Dienstplan mit dem Namen '" + name + "' für " + monat + " existiert bereits"
                );
            }

            // Bestehenden Plan finden und überschreiben
            Optional<Dienstplan> existing = dienstplanDAO.findByNameAndMonat(name, monat);
            if (existing.isPresent()) {
                dienstplan.setId(existing.get().getId());
                dienstplanDAO.update(dienstplan);
                logger.info("Dienstplan aktualisiert: {} ({})", name, monat);
                return dienstplan;
            }
        }

        // Neuen Plan erstellen
        Dienstplan created = dienstplanDAO.create(dienstplan);
        logger.info("Dienstplan erstellt: {} ({}) mit ID {}", name, monat, created.getId());
        return created;
    }

    /**
     * Lädt einen Dienstplan aus der Datenbank.
     *
     * @param name Der Name des Dienstplans
     * @param monat Der Monat des Dienstplans
     * @return Optional mit dem Dienstplan oder empty
     * @throws SQLException Bei Datenbankfehlern
     */
    public Optional<Dienstplan> ladeDienstplan(String name, YearMonth monat) throws SQLException {
        return dienstplanDAO.findByNameAndMonat(name, monat);
    }

    /**
     * Lädt alle Dienstpläne für einen bestimmten Monat.
     *
     * @param monat Der Monat
     * @return Liste der Dienstpläne
     * @throws SQLException Bei Datenbankfehlern
     */
    public List<Dienstplan> ladeDienstplaeneFuerMonat(YearMonth monat) throws SQLException {
        return dienstplanDAO.findByMonat(monat);
    }

    /**
     * Prüft ob ein Dienstplan Konflikte enthält.
     *
     * @param dienstplan Der zu prüfende Dienstplan
     * @return Liste der Konfliktbeschreibungen (leer wenn keine Konflikte)
     */
    public List<String> pruefeKonflikte(Dienstplan dienstplan) {
        if (dienstplan == null) {
            return List.of("Dienstplan ist null");
        }
        return dienstplan.getKonflikte();
    }

    /**
     * Lädt alle verfügbaren Personen.
     *
     * @return Liste aller Personen
     * @throws SQLException Bei Datenbankfehlern
     */
    public List<Person> ladeAllePersonen() throws SQLException {
        return personDAO.findAll();
    }
}
