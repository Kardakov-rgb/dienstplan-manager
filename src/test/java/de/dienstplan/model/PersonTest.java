package de.dienstplan.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests für die Person-Klasse
 */
@DisplayName("Person Tests")
class PersonTest {

    private Person person;

    @BeforeEach
    void setUp() {
        person = new Person("Max Mustermann");
        person.setId(1L);
        person.setAnzahlDienste(10);
        person.setArbeitsTage(EnumSet.of(
            Wochentag.MONTAG, Wochentag.DIENSTAG, Wochentag.MITTWOCH,
            Wochentag.DONNERSTAG, Wochentag.FREITAG
        ));
        person.setVerfuegbareDienstArten(EnumSet.of(
            DienstArt.DIENST_24H, DienstArt.DAVINCI
        ));
    }

    @Nested
    @DisplayName("Konstruktor und Validierung")
    class KonstruktorTests {

        @Test
        @DisplayName("Erstellt Person mit gültigem Namen")
        void erstelltPersonMitGueltigemNamen() {
            Person p = new Person("Test Person");
            assertEquals("Test Person", p.getName());
        }

        @Test
        @DisplayName("Trimmt Namen automatisch")
        void trimmtNamen() {
            Person p = new Person("  Test Person  ");
            assertEquals("Test Person", p.getName());
        }

        @Test
        @DisplayName("Wirft Exception bei null Namen")
        void wirftExceptionBeiNullName() {
            assertThrows(IllegalArgumentException.class, () -> new Person(null));
        }

        @Test
        @DisplayName("Wirft Exception bei leerem Namen")
        void wirftExceptionBeiLeeremName() {
            assertThrows(IllegalArgumentException.class, () -> new Person(""));
            assertThrows(IllegalArgumentException.class, () -> new Person("   "));
        }

        @Test
        @DisplayName("Wirft Exception bei negativer Dienstanzahl")
        void wirftExceptionBeiNegativerDienstanzahl() {
            assertThrows(IllegalArgumentException.class, () -> person.setAnzahlDienste(-1));
        }

        @Test
        @DisplayName("Akzeptiert Dienstanzahl von 0")
        void akzeptiertDienstanzahlNull() {
            assertDoesNotThrow(() -> person.setAnzahlDienste(0));
            assertEquals(0, person.getAnzahlDienste());
        }
    }

    // TODO: Verfügbarkeitstests mit MonatsWunsch in Phase 3 implementieren
    // Abwesenheiten wurden durch MonatsWunsch (Urlaub) ersetzt

    @Nested
    @DisplayName("Arbeits- und Dienstarten-Prüfung")
    class ArbeitstagsTests {

        @Test
        @DisplayName("Arbeitet an konfigurierten Wochentagen")
        void arbeitetAnKonfiguriertenWochentagen() {
            assertTrue(person.arbeitetAnWochentag(Wochentag.MONTAG));
            assertTrue(person.arbeitetAnWochentag(Wochentag.FREITAG));
            assertFalse(person.arbeitetAnWochentag(Wochentag.SAMSTAG));
            assertFalse(person.arbeitetAnWochentag(Wochentag.SONNTAG));
        }

        @Test
        @DisplayName("Kann konfigurierte Dienstarten")
        void kannKonfigurierteDienstarten() {
            assertTrue(person.kannDienstArt(DienstArt.DIENST_24H));
            assertTrue(person.kannDienstArt(DienstArt.DAVINCI));
            assertFalse(person.kannDienstArt(DienstArt.VISTEN));
        }
    }

    @Nested
    @DisplayName("Equals und HashCode")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("Gleichheit basiert auf ID")
        void gleichheitBasiertAufId() {
            Person p1 = new Person("Person 1");
            p1.setId(1L);

            Person p2 = new Person("Person 2");
            p2.setId(1L);

            Person p3 = new Person("Person 1");
            p3.setId(2L);

            assertEquals(p1, p2);
            assertNotEquals(p1, p3);
            assertEquals(p1.hashCode(), p2.hashCode());
        }

        @Test
        @DisplayName("Null und andere Klassen sind nicht gleich")
        void nullUndAndereKlassenNichtGleich() {
            assertNotEquals(person, null);
            assertNotEquals(person, "String");
        }
    }
}
