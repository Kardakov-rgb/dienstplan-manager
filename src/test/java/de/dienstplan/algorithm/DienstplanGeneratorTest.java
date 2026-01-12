package de.dienstplan.algorithm;

import de.dienstplan.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests für den DienstplanGenerator
 */
@DisplayName("DienstplanGenerator Tests")
class DienstplanGeneratorTest {

    private List<Person> personen;
    private YearMonth testMonat;

    @BeforeEach
    void setUp() {
        personen = new ArrayList<>();
        testMonat = YearMonth.of(2024, 6); // Juni 2024

        // Erstelle Test-Personen mit verschiedenen Konfigurationen
        personen.add(createPerson(1L, "Anna", 5,
            EnumSet.allOf(Wochentag.class),
            EnumSet.of(DienstArt.DIENST_24H, DienstArt.SPAET)));

        personen.add(createPerson(2L, "Bernd", 5,
            EnumSet.of(Wochentag.MONTAG, Wochentag.DIENSTAG, Wochentag.MITTWOCH, Wochentag.DONNERSTAG, Wochentag.FREITAG),
            EnumSet.of(DienstArt.DIENST_24H, DienstArt.SPAET)));

        personen.add(createPerson(3L, "Clara", 5,
            EnumSet.allOf(Wochentag.class),
            EnumSet.of(DienstArt.DIENST_24H, DienstArt.VISTEN)));

        personen.add(createPerson(4L, "David", 5,
            EnumSet.allOf(Wochentag.class),
            EnumSet.allOf(DienstArt.class)));
    }

    @Nested
    @DisplayName("Grundlegende Generierung")
    class GrundlegendeGenerierungTests {

        @Test
        @DisplayName("Generiert Dienstplan erfolgreich")
        void generiertDienstplanErfolgreich() {
            DienstplanGenerator generator = new DienstplanGenerator(personen, testMonat);
            DienstplanGenerator.DienstplanGenerierungResult result = generator.generiereDienstplan();

            assertNotNull(result);
            assertNotNull(result.getDienstplan());
            assertTrue(result.istErfolgreich());
        }

        @Test
        @DisplayName("Erstellt korrekte Anzahl von Dienstslots")
        void erstelltKorrekteAnzahlSlots() {
            DienstplanGenerator generator = new DienstplanGenerator(personen, testMonat);
            DienstplanGenerator.DienstplanGenerierungResult result = generator.generiereDienstplan();

            Dienstplan dienstplan = result.getDienstplan();
            assertNotNull(dienstplan);

            // Juni 2024: 30 Tage
            // 30 x DIENST_24H + 8 x VISTEN (4 Wochenenden) + 20 x SPAET (Werktage)
            // = 30 + 8 + 20 = 58 Dienste (ungefähr, je nach Kalender)
            assertTrue(dienstplan.getDienste().size() > 50);
        }

        @Test
        @DisplayName("Generiert Warnung bei leerer Personenliste")
        void generiertWarnungBeiLeererListe() {
            DienstplanGenerator generator = new DienstplanGenerator(new ArrayList<>(), testMonat);
            DienstplanGenerator.DienstplanGenerierungResult result = generator.generiereDienstplan();

            assertTrue(result.hatWarnungen());
        }
    }

    @Nested
    @DisplayName("Constraint-Prüfung")
    class ConstraintTests {

        // TODO: Test für Urlaub mit MonatsWunsch in Phase 3 implementieren
        // @Test
        // @DisplayName("Weist keine Person am Urlaubstag zu")
        // void weistNichtZuBeiUrlaub() { ... }

        @Test
        @DisplayName("Weist keine Person am falschen Wochentag zu")
        void weistNichtAmFalschenWochentagZu() {
            // Bernd arbeitet nur Mo-Fr
            DienstplanGenerator generator = new DienstplanGenerator(personen, testMonat);
            DienstplanGenerator.DienstplanGenerierungResult result = generator.generiereDienstplan();

            Dienstplan dienstplan = result.getDienstplan();

            // Prüfe, dass Bernd keine Wochenend-Dienste hat
            for (Dienst dienst : dienstplan.getDienste()) {
                if (dienst.getPersonId() != null && dienst.getPersonId().equals(2L)) {
                    Wochentag wochentag = Wochentag.fromLocalDate(dienst.getDatum());
                    assertFalse(
                        wochentag == Wochentag.SAMSTAG || wochentag == Wochentag.SONNTAG,
                        "Bernd sollte am " + wochentag + " keinen Dienst haben"
                    );
                }
            }
        }

        @Test
        @DisplayName("Respektiert maximale Dienstanzahl")
        void respektiertMaximaleDienstanzahl() {
            // Erstelle eine Person mit sehr geringer maximaler Dienstanzahl
            Person limitedPerson = createPerson(10L, "Limited", 2,
                EnumSet.allOf(Wochentag.class),
                EnumSet.allOf(DienstArt.class));

            List<Person> testPersonen = new ArrayList<>();
            testPersonen.add(limitedPerson);
            // Füge weitere Personen hinzu, um den Rest abzudecken
            testPersonen.addAll(personen);

            DienstplanGenerator generator = new DienstplanGenerator(testPersonen, testMonat);
            DienstplanGenerator.DienstplanGenerierungResult result = generator.generiereDienstplan();

            Dienstplan dienstplan = result.getDienstplan();

            // Zähle Dienste der Limited-Person
            long diensteCount = dienstplan.getDienste().stream()
                .filter(d -> d.getPersonId() != null && d.getPersonId().equals(10L))
                .count();

            assertTrue(diensteCount <= 2,
                "Limited Person sollte maximal 2 Dienste haben, hat aber " + diensteCount);
        }

        @Test
        @DisplayName("Keine zwei Dienste an aufeinanderfolgenden Tagen")
        void keineAufeinanderfolgendenDienste() {
            DienstplanGenerator generator = new DienstplanGenerator(personen, testMonat);
            DienstplanGenerator.DienstplanGenerierungResult result = generator.generiereDienstplan();

            Dienstplan dienstplan = result.getDienstplan();

            // Für jede Person prüfen
            for (Person person : personen) {
                List<LocalDate> dienstDaten = dienstplan.getDienste().stream()
                    .filter(d -> person.getId().equals(d.getPersonId()))
                    .map(Dienst::getDatum)
                    .sorted()
                    .toList();

                for (int i = 0; i < dienstDaten.size() - 1; i++) {
                    LocalDate current = dienstDaten.get(i);
                    LocalDate next = dienstDaten.get(i + 1);

                    assertFalse(current.plusDays(1).equals(next),
                        person.getName() + " hat aufeinanderfolgende Dienste am "
                        + current + " und " + next);
                }
            }
        }
    }

    @Nested
    @DisplayName("Ergebnis-Zusammenfassung")
    class ErgebnisTests {

        @Test
        @DisplayName("Zusammenfassung enthält alle Informationen")
        void zusammenfassungEnthaeltInfos() {
            DienstplanGenerator generator = new DienstplanGenerator(personen, testMonat);
            DienstplanGenerator.DienstplanGenerierungResult result = generator.generiereDienstplan();

            String zusammenfassung = result.getZusammenfassung();

            assertNotNull(zusammenfassung);
            assertTrue(zusammenfassung.contains("Generierung"));
            assertTrue(zusammenfassung.contains("Dienste"));
            assertTrue(zusammenfassung.contains("Zuweisungsgrad"));
        }

        @Test
        @DisplayName("Dienstplan hat korrekten Monat")
        void dienstplanHatKorrektenMonat() {
            DienstplanGenerator generator = new DienstplanGenerator(personen, testMonat);
            DienstplanGenerator.DienstplanGenerierungResult result = generator.generiereDienstplan();

            assertEquals(testMonat, result.getDienstplan().getMonat());
        }
    }

    @Nested
    @DisplayName("Fortschritts-Callback")
    class FortschrittsTests {

        @Test
        @DisplayName("Ruft Fortschritts-Callback auf")
        void ruftFortschrittsCallbackAuf() {
            List<Double> fortschritte = new ArrayList<>();

            DienstplanGenerator generator = new DienstplanGenerator(personen, testMonat);
            generator.setProgressCallback(fortschritte::add);

            generator.generiereDienstplan();

            // Mindestens einige Fortschritts-Updates sollten kommen
            assertFalse(fortschritte.isEmpty());

            // Fortschritte sollten zwischen 0 und 1 liegen
            for (Double fortschritt : fortschritte) {
                assertTrue(fortschritt >= 0.0 && fortschritt <= 1.0,
                    "Fortschritt sollte zwischen 0 und 1 liegen: " + fortschritt);
            }
        }
    }

    // Helper-Methoden

    private Person createPerson(Long id, String name, int anzahlDienste,
                                EnumSet<Wochentag> arbeitsTage, EnumSet<DienstArt> dienstArten) {
        Person person = new Person(name);
        person.setId(id);
        person.setAnzahlDienste(anzahlDienste);
        person.setArbeitsTage(arbeitsTage);
        person.setVerfuegbareDienstArten(dienstArten);
        return person;
    }
}
