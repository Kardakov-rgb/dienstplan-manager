package de.dienstplan.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests für die Dienstplan-Klasse
 */
@DisplayName("Dienstplan Tests")
class DienstplanTest {

    private Dienstplan dienstplan;
    private Person person1;
    private Person person2;

    @BeforeEach
    void setUp() {
        dienstplan = new Dienstplan("Test Dienstplan", YearMonth.of(2024, 6));

        person1 = new Person("Person 1");
        person1.setId(1L);

        person2 = new Person("Person 2");
        person2.setId(2L);
    }

    @Nested
    @DisplayName("Dienst-Management")
    class DienstManagementTests {

        @Test
        @DisplayName("Fügt Dienste hinzu")
        void fuegtDiensteHinzu() {
            Dienst dienst = new Dienst(LocalDate.of(2024, 6, 15), DienstArt.DIENST_24H);
            dienstplan.addDienst(dienst);

            assertEquals(1, dienstplan.getDienste().size());
        }

        @Test
        @DisplayName("Entfernt Dienste")
        void entferntDienste() {
            Dienst dienst = new Dienst(LocalDate.of(2024, 6, 15), DienstArt.DIENST_24H);
            dienstplan.addDienst(dienst);
            dienstplan.removeDienst(dienst);

            assertEquals(0, dienstplan.getDienste().size());
        }

        @Test
        @DisplayName("Gibt defensive Kopie der Dienste zurück")
        void gibtDefensiveKopieZurueck() {
            Dienst dienst = new Dienst(LocalDate.of(2024, 6, 15), DienstArt.DIENST_24H);
            dienstplan.addDienst(dienst);

            List<Dienst> dienste = dienstplan.getDienste();
            dienste.clear();

            assertEquals(1, dienstplan.getDienste().size());
        }
    }

    @Nested
    @DisplayName("Konflikt-Erkennung")
    class KonfliktTests {

        @Test
        @DisplayName("Erkennt keine Konflikte bei korrektem Plan")
        void erkenntKeineKonflikte() {
            Dienst dienst1 = new Dienst(LocalDate.of(2024, 6, 15), DienstArt.DIENST_24H);
            dienst1.zuweisen(person1);
            dienstplan.addDienst(dienst1);

            Dienst dienst2 = new Dienst(LocalDate.of(2024, 6, 16), DienstArt.DIENST_24H);
            dienst2.zuweisen(person1);
            dienstplan.addDienst(dienst2);

            assertFalse(dienstplan.hatKonflikte());
            assertTrue(dienstplan.getKonflikte().isEmpty());
        }

        @Test
        @DisplayName("Erkennt Doppelzuweisung am gleichen Tag")
        void erkenntDoppelzuweisung() {
            // Person 1 hat zwei Dienste am gleichen Tag
            Dienst dienst1 = new Dienst(LocalDate.of(2024, 6, 15), DienstArt.DIENST_24H);
            dienst1.zuweisen(person1);
            dienstplan.addDienst(dienst1);

            Dienst dienst2 = new Dienst(LocalDate.of(2024, 6, 15), DienstArt.DAVINCI);
            dienst2.zuweisen(person1);
            dienstplan.addDienst(dienst2);

            assertTrue(dienstplan.hatKonflikte());
            assertEquals(1, dienstplan.getKonflikte().size());
        }

        @Test
        @DisplayName("Verschiedene Personen am gleichen Tag sind kein Konflikt")
        void verschiedenePersonenKeinKonflikt() {
            Dienst dienst1 = new Dienst(LocalDate.of(2024, 6, 15), DienstArt.DIENST_24H);
            dienst1.zuweisen(person1);
            dienstplan.addDienst(dienst1);

            Dienst dienst2 = new Dienst(LocalDate.of(2024, 6, 15), DienstArt.DAVINCI);
            dienst2.zuweisen(person2);
            dienstplan.addDienst(dienst2);

            assertFalse(dienstplan.hatKonflikte());
        }
    }

    @Nested
    @DisplayName("Statistiken")
    class StatistikTests {

        @Test
        @DisplayName("Berechnet Zuweisungsgrad korrekt")
        void berechnetZuweisungsgradKorrekt() {
            // 2 von 4 Diensten zugewiesen = 50%
            dienstplan.addDienst(createZugewiesenenDienst(LocalDate.of(2024, 6, 15), person1));
            dienstplan.addDienst(createZugewiesenenDienst(LocalDate.of(2024, 6, 16), person1));
            dienstplan.addDienst(createOffenenDienst(LocalDate.of(2024, 6, 17)));
            dienstplan.addDienst(createOffenenDienst(LocalDate.of(2024, 6, 18)));

            assertEquals(50.0, dienstplan.getZuweisungsgrad(), 0.01);
        }

        @Test
        @DisplayName("Zählt offene Dienste korrekt")
        void zaehltOffeneDiensteKorrekt() {
            dienstplan.addDienst(createZugewiesenenDienst(LocalDate.of(2024, 6, 15), person1));
            dienstplan.addDienst(createOffenenDienst(LocalDate.of(2024, 6, 16)));
            dienstplan.addDienst(createOffenenDienst(LocalDate.of(2024, 6, 17)));

            assertEquals(2, dienstplan.getAnzahlOffeneDienste());
            assertEquals(2, dienstplan.getOffeneDienste().size());
        }

        @Test
        @DisplayName("Gruppiert Dienste nach Person")
        void gruppiertDiensteNachPerson() {
            dienstplan.addDienst(createZugewiesenenDienst(LocalDate.of(2024, 6, 15), person1));
            dienstplan.addDienst(createZugewiesenenDienst(LocalDate.of(2024, 6, 16), person1));
            dienstplan.addDienst(createZugewiesenenDienst(LocalDate.of(2024, 6, 17), person2));

            Map<Long, Integer> diensteProPerson = dienstplan.getDiensteProPerson();

            assertEquals(2, diensteProPerson.get(1L));
            assertEquals(1, diensteProPerson.get(2L));
        }

        @Test
        @DisplayName("Prüft vollständige Zuweisung")
        void prueftVollstaendigeZuweisung() {
            dienstplan.addDienst(createZugewiesenenDienst(LocalDate.of(2024, 6, 15), person1));
            assertTrue(dienstplan.istVollstaendigZugewiesen());

            dienstplan.addDienst(createOffenenDienst(LocalDate.of(2024, 6, 16)));
            assertFalse(dienstplan.istVollstaendigZugewiesen());
        }
    }

    @Nested
    @DisplayName("Such-Methoden")
    class SuchMethodenTests {

        @Test
        @DisplayName("Findet Dienste am Datum")
        void findetDiensteAmDatum() {
            LocalDate datum = LocalDate.of(2024, 6, 15);
            dienstplan.addDienst(new Dienst(datum, DienstArt.DIENST_24H));
            dienstplan.addDienst(new Dienst(datum, DienstArt.DAVINCI));
            dienstplan.addDienst(new Dienst(LocalDate.of(2024, 6, 16), DienstArt.DIENST_24H));

            List<Dienst> diensteAmDatum = dienstplan.getDiensteAmDatum(datum);
            assertEquals(2, diensteAmDatum.size());
        }

        @Test
        @DisplayName("Findet Dienste von Person")
        void findetDiensteVonPerson() {
            dienstplan.addDienst(createZugewiesenenDienst(LocalDate.of(2024, 6, 15), person1));
            dienstplan.addDienst(createZugewiesenenDienst(LocalDate.of(2024, 6, 16), person1));
            dienstplan.addDienst(createZugewiesenenDienst(LocalDate.of(2024, 6, 17), person2));

            List<Dienst> diensteVonPerson1 = dienstplan.getDiensteVonPerson(1L);
            assertEquals(2, diensteVonPerson1.size());
        }

        @Test
        @DisplayName("Findet Dienste nach Art")
        void findetDiensteNachArt() {
            dienstplan.addDienst(new Dienst(LocalDate.of(2024, 6, 15), DienstArt.DIENST_24H));
            dienstplan.addDienst(new Dienst(LocalDate.of(2024, 6, 16), DienstArt.DIENST_24H));
            dienstplan.addDienst(new Dienst(LocalDate.of(2024, 6, 17), DienstArt.DAVINCI));

            List<Dienst> dienste24h = dienstplan.getDiensteVonArt(DienstArt.DIENST_24H);
            assertEquals(2, dienste24h.size());
        }
    }

    // Helper-Methoden

    private Dienst createZugewiesenenDienst(LocalDate datum, Person person) {
        Dienst dienst = new Dienst(datum, DienstArt.DIENST_24H);
        dienst.zuweisen(person);
        return dienst;
    }

    private Dienst createOffenenDienst(LocalDate datum) {
        return new Dienst(datum, DienstArt.DIENST_24H);
    }
}
