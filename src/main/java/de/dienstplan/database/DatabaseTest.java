package de.dienstplan.database;

import de.dienstplan.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;

/**
 * Testklasse für das Datenbank-Setup
 * Diese Klasse kannst du ausführen um zu testen ob alles funktioniert
 */
public class DatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseTest.class);

    public static void main(String[] args) {
        try {
            testDatabaseSetup();
        } catch (Exception e) {
            logger.error("Test fehlgeschlagen", e);
            System.exit(1);
        }
    }

    public static void testDatabaseSetup() throws SQLException {
        logger.info("=== Starte Datenbank-Test ===");

        // 1. Datenbank initialisieren

        // Verbindung testen
        if (!DatabaseManager.testConnection()) {
            throw new RuntimeException("Datenbankverbindung fehlgeschlagen");
        }
        logger.info("✓ Datenbankverbindung erfolgreich");

        // Datenbank initialisieren
        DatabaseManager.initializeDatabase();
        logger.info("✓ Datenbank initialisiert");

        // Datenbank-Info anzeigen
        String dbInfo = DatabaseManager.getDatabaseInfo();
        logger.info("Datenbank-Info:\n{}", dbInfo);

        // 2. Person-Tests
        testPersonOperations();

        // 3. MonatsWunsch-Tests (TODO: Phase 3)
        // testMonatsWunschOperations();

        logger.info("=== Alle Tests erfolgreich! ===");
    }

    private static void testPersonOperations() throws SQLException {
        logger.info("\n--- Person-Tests ---");

        PersonDAO personDAO = new PersonDAO();

        // Test-Person erstellen
        Person person = new Person("Max Mustermann");
        person.setAnzahlDienste(20);

        // Arbeitstage: Montag bis Freitag
        EnumSet<Wochentag> arbeitsTage = EnumSet.of(
            Wochentag.MONTAG,
            Wochentag.DIENSTAG,
            Wochentag.MITTWOCH,
            Wochentag.DONNERSTAG,
            Wochentag.FREITAG
        );
        person.setArbeitsTage(arbeitsTage);

        // Verfügbare Dienstarten: 24h und DaVinci
        EnumSet<DienstArt> dienstArten = EnumSet.of(
            DienstArt.DIENST_24H,
            DienstArt.DAVINCI
        );
        person.setVerfuegbareDienstArten(dienstArten);

        // Person speichern
        Person gespeichertePerson = personDAO.create(person);
        logger.info("✓ Person erstellt: ID {}, Name: {}",
                    gespeichertePerson.getId(), gespeichertePerson.getName());

        // Person laden
        var gefundenePerson = personDAO.findById(gespeichertePerson.getId());
        if (gefundenePerson.isPresent()) {
            Person geladenePerson = gefundenePerson.get();
            logger.info("✓ Person geladen: {}", geladenePerson.getName());
            logger.info("  - Arbeitstage: {}", geladenePerson.getArbeitsTage());
            logger.info("  - Dienstarten: {}", geladenePerson.getVerfuegbareDienstArten());
        } else {
            throw new RuntimeException("Person konnte nicht geladen werden");
        }

        // Weitere Test-Person erstellen
        Person person2 = new Person("Anna Schmidt");
        person2.setAnzahlDienste(15);
        person2.setArbeitsTage(EnumSet.of(
            Wochentag.MONTAG,
            Wochentag.MITTWOCH,
            Wochentag.FREITAG
        ));
        person2.setVerfuegbareDienstArten(EnumSet.of(
            DienstArt.VISTEN,
            DienstArt.DAVINCI
        ));

        personDAO.create(person2);
        logger.info("✓ Zweite Person erstellt: {}", person2.getName());

        // Alle Personen laden
        List<Person> allePersonen = personDAO.findAll();
        logger.info("✓ Anzahl Personen in Datenbank: {}", allePersonen.size());

        // Such-Test
        List<Person> gefundenePersonen = personDAO.search("Max", null, null);
        logger.info("✓ Suche nach 'Max': {} Treffer", gefundenePersonen.size());

        // Person nach Name finden
        var personByName = personDAO.findByName("Anna Schmidt");
        if (personByName.isPresent()) {
            logger.info("✓ Person nach Name gefunden: {}", personByName.get().getName());
        }

        // Anzahl Personen
        int personenCount = personDAO.count();
        logger.info("✓ Anzahl Personen: {}", personenCount);
    }

    /**
     * Erstellt Testdaten für die Anwendung - 15 realistische deutsche Personen
     */
    public static void createTestData() throws SQLException {
        logger.info("Erstelle Testdaten...");

        PersonDAO personDAO = new PersonDAO();

        // 15 realistische deutsche Personen mit verschiedenen Arbeitstagen und Dienstarten
        Person[] testPersonen = {
            createTestPerson("Dr. Sarah Weber", 22,
                           EnumSet.of(Wochentag.MONTAG, Wochentag.DIENSTAG, Wochentag.MITTWOCH, Wochentag.DONNERSTAG, Wochentag.FREITAG),
                           EnumSet.of(DienstArt.DIENST_24H, DienstArt.VISTEN)),

            createTestPerson("Michael Fischer", 20,
                           EnumSet.of(Wochentag.DIENSTAG, Wochentag.MITTWOCH, Wochentag.DONNERSTAG, Wochentag.FREITAG, Wochentag.SAMSTAG),
                           EnumSet.of(DienstArt.DAVINCI, DienstArt.VISTEN)),

            createTestPerson("Lisa Müller", 18,
                           EnumSet.of(Wochentag.MONTAG, Wochentag.MITTWOCH, Wochentag.FREITAG),
                           EnumSet.of(DienstArt.DIENST_24H, DienstArt.DAVINCI, DienstArt.VISTEN)),

            createTestPerson("Thomas Klein", 24,
                           EnumSet.of(Wochentag.MONTAG, Wochentag.DIENSTAG, Wochentag.DONNERSTAG, Wochentag.FREITAG),
                           EnumSet.of(DienstArt.DAVINCI)),

            createTestPerson("Anna Schmidt", 21,
                           EnumSet.of(Wochentag.MONTAG, Wochentag.DIENSTAG, Wochentag.MITTWOCH, Wochentag.DONNERSTAG),
                           EnumSet.of(DienstArt.DIENST_24H, DienstArt.VISTEN)),

            createTestPerson("Dr. Markus Wagner", 25,
                           EnumSet.of(Wochentag.MONTAG, Wochentag.DIENSTAG, Wochentag.MITTWOCH, Wochentag.DONNERSTAG, Wochentag.FREITAG, Wochentag.SAMSTAG),
                           EnumSet.of(DienstArt.DIENST_24H, DienstArt.DAVINCI, DienstArt.VISTEN)),

            createTestPerson("Julia Hoffmann", 19,
                           EnumSet.of(Wochentag.MITTWOCH, Wochentag.DONNERSTAG, Wochentag.FREITAG, Wochentag.SAMSTAG),
                           EnumSet.of(DienstArt.VISTEN)),

            createTestPerson("Stefan Becker", 20,
                           EnumSet.of(Wochentag.MONTAG, Wochentag.DIENSTAG, Wochentag.MITTWOCH),
                           EnumSet.of(DienstArt.DIENST_24H, DienstArt.DAVINCI)),

            createTestPerson("Claudia Richter", 23,
                           EnumSet.of(Wochentag.DIENSTAG, Wochentag.DONNERSTAG, Wochentag.SAMSTAG, Wochentag.SONNTAG),
                           EnumSet.of(DienstArt.DAVINCI, DienstArt.VISTEN)),

            createTestPerson("Dr. Hans Meyer", 22,
                           EnumSet.of(Wochentag.MONTAG, Wochentag.MITTWOCH, Wochentag.FREITAG, Wochentag.SONNTAG),
                           EnumSet.of(DienstArt.DIENST_24H)),

            createTestPerson("Sabine Wolf", 17,
                           EnumSet.of(Wochentag.DIENSTAG, Wochentag.MITTWOCH, Wochentag.DONNERSTAG),
                           EnumSet.of(DienstArt.VISTEN, DienstArt.DAVINCI)),

            createTestPerson("Peter Neumann", 21,
                           EnumSet.of(Wochentag.MONTAG, Wochentag.DIENSTAG, Wochentag.FREITAG, Wochentag.SAMSTAG),
                           EnumSet.of(DienstArt.DIENST_24H, DienstArt.VISTEN)),

            createTestPerson("Maria Schulz", 20,
                           EnumSet.of(Wochentag.MONTAG, Wochentag.MITTWOCH, Wochentag.DONNERSTAG, Wochentag.FREITAG),
                           EnumSet.of(DienstArt.DAVINCI)),

            createTestPerson("Dr. Frank Braun", 24,
                           EnumSet.of(Wochentag.DIENSTAG, Wochentag.MITTWOCH, Wochentag.DONNERSTAG, Wochentag.FREITAG, Wochentag.SAMSTAG),
                           EnumSet.of(DienstArt.DIENST_24H, DienstArt.DAVINCI, DienstArt.VISTEN)),

            createTestPerson("Katrin Zimmermann", 19,
                           EnumSet.of(Wochentag.MONTAG, Wochentag.DIENSTAG, Wochentag.DONNERSTAG, Wochentag.SONNTAG),
                           EnumSet.of(DienstArt.VISTEN))
        };

        for (Person person : testPersonen) {
            personDAO.create(person);
            logger.info("Test-Person erstellt: {}", person.getName());
        }

        logger.info("✓ 15 Testpersonen erstellt");
    }

    private static Person createTestPerson(String name, int anzahlDienste,
                                         EnumSet<Wochentag> arbeitsTage,
                                         EnumSet<DienstArt> dienstArten) {
        Person person = new Person(name);
        person.setAnzahlDienste(anzahlDienste);
        person.setArbeitsTage(arbeitsTage);
        person.setVerfuegbareDienstArten(dienstArten);
        return person;
    }
}
