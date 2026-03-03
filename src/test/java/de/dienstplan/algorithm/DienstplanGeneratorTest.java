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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            EnumSet.of(DienstArt.DIENST_24H, DienstArt.DAVINCI)));

        personen.add(createPerson(2L, "Bernd", 5,
            EnumSet.of(Wochentag.MONTAG, Wochentag.DIENSTAG, Wochentag.MITTWOCH, Wochentag.DONNERSTAG, Wochentag.FREITAG),
            EnumSet.of(DienstArt.DIENST_24H, DienstArt.DAVINCI)));

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
            // 30 x DIENST_24H + 8 x VISTEN (4 Wochenenden) + 4 x DAVINCI (Freitage)
            // = 30 + 8 + 4 = 42 Dienste (ungefähr, je nach Kalender)
            assertTrue(dienstplan.getDienste().size() > 40);
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

        @Test
        @DisplayName("Weist keine Person am Urlaubstag zu")
        void weistNichtZuBeiUrlaub() {
            // Anna (id=1) hat am 10. Juni 2024 (Montag) Urlaub
            LocalDate urlaubsDatum = LocalDate.of(2024, 6, 10);
            List<MonatsWunsch> wuensche = new ArrayList<>();
            wuensche.add(new MonatsWunsch(1L, urlaubsDatum, WunschTyp.URLAUB));

            DienstplanGenerator generator = new DienstplanGenerator(
                personen, testMonat, wuensche, new HashMap<>()
            );
            DienstplanGenerator.DienstplanGenerierungResult result = generator.generiereDienstplan();
            Dienstplan dienstplan = result.getDienstplan();

            boolean annaHatDienstAmUrlaubstag = dienstplan.getDienste().stream()
                .anyMatch(d -> d.getDatum().equals(urlaubsDatum)
                    && Long.valueOf(1L).equals(d.getPersonId()));

            assertFalse(annaHatDienstAmUrlaubstag,
                "Anna sollte am Urlaubstag " + urlaubsDatum + " keinen Dienst haben");
        }

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
        @DisplayName("Keine zwei Dienste an aufeinanderfolgenden Tagen (außer Visitendienst Wochenende)")
        void keineAufeinanderfolgendenDienste() {
            DienstplanGenerator generator = new DienstplanGenerator(personen, testMonat);
            DienstplanGenerator.DienstplanGenerierungResult result = generator.generiereDienstplan();

            Dienstplan dienstplan = result.getDienstplan();

            // Für jede Person prüfen
            for (Person person : personen) {
                List<LocalDate> dienstDaten = dienstplan.getDienste().stream()
                    .filter(d -> person.getId().equals(d.getPersonId()))
                    .map(Dienst::getDatum)
                    .distinct()
                    .sorted()
                    .toList();

                for (int i = 0; i < dienstDaten.size() - 1; i++) {
                    LocalDate current = dienstDaten.get(i);
                    LocalDate next = dienstDaten.get(i + 1);

                    if (current.plusDays(1).equals(next)) {
                        // Aufeinanderfolgende Tage sind nur beim Visitendienst-Wochenendpaket (Sa+So) erlaubt
                        assertTrue(
                            istVistenWochenenddienst(dienstplan, person.getId(), current, next),
                            person.getName() + " hat unerlaubte aufeinanderfolgende Dienste am "
                                + current + " (" + Wochentag.fromLocalDate(current) + ")"
                                + " und " + next + " (" + Wochentag.fromLocalDate(next) + ")"
                        );
                    }
                }
            }
        }

        @Test
        @DisplayName("Erster Tag nach Urlaub ist frei")
        void ersterTagNachUrlaubIstFrei() {
            // Anna (id=1) hat am 10. Juni 2024 (Montag) Urlaub → 11. Juni (Dienstag) muss frei sein
            LocalDate urlaubsDatum = LocalDate.of(2024, 6, 10);
            LocalDate tagNachUrlaub = urlaubsDatum.plusDays(1);

            List<MonatsWunsch> wuensche = new ArrayList<>();
            wuensche.add(new MonatsWunsch(1L, urlaubsDatum, WunschTyp.URLAUB));

            DienstplanGenerator generator = new DienstplanGenerator(
                personen, testMonat, wuensche, new HashMap<>()
            );
            DienstplanGenerator.DienstplanGenerierungResult result = generator.generiereDienstplan();
            Dienstplan dienstplan = result.getDienstplan();

            boolean annaHatDienstNachUrlaub = dienstplan.getDienste().stream()
                .anyMatch(d -> d.getDatum().equals(tagNachUrlaub)
                    && Long.valueOf(1L).equals(d.getPersonId()));

            assertFalse(annaHatDienstNachUrlaub,
                "Anna sollte am Tag nach dem Urlaub (" + tagNachUrlaub + ") keinen Dienst haben");
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

    @Nested
    @DisplayName("Weiche Regeln und Wünsche")
    class WeicheRegelnTests {

        @Test
        @DisplayName("Dienstwunsch wird in Wunschstatistik erfasst")
        void dienstwunschWirdInStatistikErfasst() {
            // Anna (id=1) hat am 10. Juni 2024 (Montag) einen Dienstwunsch
            LocalDate dienstwunschDatum = LocalDate.of(2024, 6, 10);
            List<MonatsWunsch> wuensche = new ArrayList<>();
            wuensche.add(new MonatsWunsch(1L, dienstwunschDatum, WunschTyp.DIENSTWUNSCH));

            DienstplanGenerator generator = new DienstplanGenerator(
                personen, testMonat, wuensche, new HashMap<>()
            );
            DienstplanGenerator.DienstplanGenerierungResult result = generator.generiereDienstplan();

            assertNotNull(result.getDienstplan());
            assertNotNull(result.getWunschStatistiken());

            // Wunschstatistik für Anna sollte genau 1 Dienstwunsch erfassen
            WunschStatistik stat = result.getWunschStatistiken().get(1L);
            assertNotNull(stat, "Wunschstatistik für Anna (id=1) sollte vorhanden sein");
            assertEquals(1, stat.getAnzahlDienstwuensche(),
                "Anna sollte genau 1 Dienstwunsch in der Statistik haben");
        }

        @Test
        @DisplayName("Freiwunsch wird in Wunschstatistik erfasst")
        void freiwunschWirdInStatistikErfasst() {
            // Bernd (id=2) hat am 3. Juni 2024 (Montag) einen Freiwunsch
            LocalDate freiwunschDatum = LocalDate.of(2024, 6, 3);
            List<MonatsWunsch> wuensche = new ArrayList<>();
            wuensche.add(new MonatsWunsch(2L, freiwunschDatum, WunschTyp.FREIWUNSCH));

            DienstplanGenerator generator = new DienstplanGenerator(
                personen, testMonat, wuensche, new HashMap<>()
            );
            DienstplanGenerator.DienstplanGenerierungResult result = generator.generiereDienstplan();

            assertNotNull(result.getWunschStatistiken());

            // Wunschstatistik für Bernd sollte genau 1 Freiwunsch erfassen
            WunschStatistik stat = result.getWunschStatistiken().get(2L);
            assertNotNull(stat, "Wunschstatistik für Bernd (id=2) sollte vorhanden sein");
            assertEquals(1, stat.getAnzahlFreiwuensche(),
                "Bernd sollte genau 1 Freiwunsch in der Statistik haben");
        }

        @Test
        @DisplayName("Fairness-Scores beeinflussen die Generierung nicht negativ")
        void fairnessScoresBeeinflusstenGenerierungNichtNegativ() {
            // Anna erscheint als "benachteiligt" durch historisch niedrige Erfüllung
            Map<Long, FairnessScore> fairnessScores = new HashMap<>();
            FairnessScore annaScore = new FairnessScore(1L, "Anna");
            annaScore.addMonatsDaten(10, 2); // 20% Erfüllungsquote = benachteiligt
            fairnessScores.put(1L, annaScore);

            DienstplanGenerator generator = new DienstplanGenerator(
                personen, testMonat, new ArrayList<>(), fairnessScores
            );
            DienstplanGenerator.DienstplanGenerierungResult result = generator.generiereDienstplan();

            // Plan muss trotz Fairness-Scores vollständig und erfolgreich generiert werden
            assertNotNull(result.getDienstplan());
            assertTrue(result.istErfolgreich());
            assertTrue(result.getDienstplan().getDienste().size() > 40);
        }

        @Test
        @DisplayName("Wunschstatistiken decken alle Personen mit Wünschen ab")
        void wunschstatistikenDeckenAllePersonenAb() {
            List<MonatsWunsch> wuensche = new ArrayList<>();
            wuensche.add(new MonatsWunsch(1L, LocalDate.of(2024, 6, 10), WunschTyp.URLAUB));
            wuensche.add(new MonatsWunsch(2L, LocalDate.of(2024, 6, 3), WunschTyp.FREIWUNSCH));
            wuensche.add(new MonatsWunsch(3L, LocalDate.of(2024, 6, 17), WunschTyp.DIENSTWUNSCH));

            DienstplanGenerator generator = new DienstplanGenerator(
                personen, testMonat, wuensche, new HashMap<>()
            );
            DienstplanGenerator.DienstplanGenerierungResult result = generator.generiereDienstplan();

            Map<Long, WunschStatistik> statistiken = result.getWunschStatistiken();
            assertNotNull(statistiken);

            // Alle drei Personen mit Wünschen müssen in der Statistik erscheinen
            assertTrue(statistiken.containsKey(1L), "Statistik für Anna (id=1) fehlt");
            assertTrue(statistiken.containsKey(2L), "Statistik für Bernd (id=2) fehlt");
            assertTrue(statistiken.containsKey(3L), "Statistik für Clara (id=3) fehlt");

            // Urlaub wird korrekt gezählt
            assertEquals(1, statistiken.get(1L).getAnzahlUrlaub(),
                "Anna sollte 1 Urlaubstag in der Statistik haben");
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

    /**
     * Prüft ob zwei aufeinanderfolgende Dienste das erlaubte Visitendienst-Wochenendpaket (Sa+So) sind.
     */
    private boolean istVistenWochenenddienst(Dienstplan dienstplan, Long personId,
                                              LocalDate ersterTag, LocalDate zweiterTag) {
        if (Wochentag.fromLocalDate(ersterTag) != Wochentag.SAMSTAG) return false;
        if (Wochentag.fromLocalDate(zweiterTag) != Wochentag.SONNTAG) return false;

        boolean hatSamstagVisten = dienstplan.getDienste().stream()
            .anyMatch(d -> d.getDatum().equals(ersterTag)
                && d.getArt() == DienstArt.VISTEN
                && personId.equals(d.getPersonId()));

        boolean hatSonntagVisten = dienstplan.getDienste().stream()
            .anyMatch(d -> d.getDatum().equals(zweiterTag)
                && d.getArt() == DienstArt.VISTEN
                && personId.equals(d.getPersonId()));

        return hatSamstagVisten && hatSonntagVisten;
    }
}
