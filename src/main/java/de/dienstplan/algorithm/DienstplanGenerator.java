package de.dienstplan.algorithm;

import de.dienstplan.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Optimierter Backtracking-Algorithmus für automatische Dienstplanerstellung.
 *
 * Prioritäten (in Reihenfolge):
 * 1. Harte Regeln (niemals brechen)
 * 2. Maximale Abstände zwischen Diensten pro Person
 * 3. Gleichmäßige Dienstanzahl pro Person
 * 4. Gleichmäßige Dienstarten-Verteilung
 *
 * Optimierungen:
 * - Constraint Propagation vor dem Backtracking
 * - Most Constrained Variable (MCV) Heuristik
 * - Berücksichtigung von anzahlDienste pro Person
 */
public class DienstplanGenerator {

    private static final Logger logger = LoggerFactory.getLogger(DienstplanGenerator.class);

    // Konstanten für Score-Berechnung
    private static final double SCORE_KEINE_BISHERIGEN_DIENSTE = 1000.0;
    private static final int MAX_ABSTAND_TAGE = 30;
    private static final double EPSILON = 0.1;

    // Konfiguration
    private final List<Person> verfuegbarePersonen;
    private final YearMonth zielmonat;
    private final Map<DienstArt, Set<Wochentag>> dienstartWochentage;

    // Arbeitsstrukturen
    private List<DienstSlot> dienstSlots;
    private Map<Long, List<LocalDate>> personDienste;
    private Set<String> warnungen;

    // Fortschritts-Callback
    private Consumer<Double> progressCallback;

    public DienstplanGenerator(List<Person> personen, YearMonth monat) {
        // Defensive Kopie für Thread-Safety
        this.verfuegbarePersonen = new ArrayList<>(personen);
        this.zielmonat = monat;
        this.dienstartWochentage = initializeDienstartWochentage();
        this.personDienste = new HashMap<>();
        this.warnungen = new LinkedHashSet<>();

        logger.info("DienstplanGenerator initialisiert für {} mit {} Personen",
                   monat, personen.size());
    }

    /**
     * Setzt den Fortschritts-Callback (0.0 bis 1.0).
     */
    public void setProgressCallback(Consumer<Double> callback) {
        this.progressCallback = callback;
    }

    /**
     * Hauptmethode: Generiert einen kompletten Dienstplan
     */
    public DienstplanGenerierungResult generiereDienstplan() {
        logger.info("=== Starte Dienstplan-Generierung für {} ===", zielmonat);

        try {
            // 1. Initialisierung
            initializeStructures();

            // 2. Dienstslots erstellen (alle Tage + Dienstarten für den Monat)
            createDienstSlots();

            // 3. Constraint Propagation - reduziert den Suchraum
            propagateConstraints();

            // 4. Sortiere Slots nach MCV-Heuristik (Most Constrained Variable first)
            sortSlotsByMCV();

            // 5. Backtracking-Algorithmus
            boolean erfolg = backtrackDienstplanErstellen(0);

            // 6. Ergebnisse zusammenstellen
            Dienstplan dienstplan = createDienstplanFromSlots();

            logger.info("Dienstplan-Generierung beendet. Erfolg: {}, Warnungen: {}",
                       erfolg, warnungen.size());

            return new DienstplanGenerierungResult(dienstplan, warnungen, erfolg);

        } catch (Exception e) {
            logger.error("Fehler bei der Dienstplan-Generierung", e);
            return new DienstplanGenerierungResult(null,
                Set.of("Kritischer Fehler: " + e.getMessage()), false);
        }
    }

    /**
     * Constraint Propagation: Berechnet vorab die möglichen Kandidaten für jeden Slot.
     * Wenn ein Slot nur einen Kandidaten hat, wird dieser direkt zugewiesen.
     */
    private void propagateConstraints() {
        logger.debug("Starte Constraint Propagation...");

        boolean changed;
        int iterations = 0;
        int maxIterations = 100; // Vermeidet Endlosschleifen

        do {
            changed = false;
            iterations++;

            for (DienstSlot slot : dienstSlots) {
                if (slot.zugewiesenePerson != null) {
                    continue; // Bereits zugewiesen
                }

                // Berechne mögliche Kandidaten
                List<Person> kandidaten = findKandidatenFuerSlot(slot);
                slot.moeglicheKandidaten = kandidaten;

                if (kandidaten.isEmpty()) {
                    // Kein Kandidat möglich - Warnung
                    warnungen.add(String.format("MANUELL ZUWEISEN: %s %s - Keine Person verfügbar",
                                 slot.datum, slot.dienstArt.getVollName()));
                } else if (kandidaten.size() == 1) {
                    // Nur ein Kandidat - direkt zuweisen (Naked Single)
                    Person einzigerKandidat = kandidaten.get(0);
                    if (kannPersonDienstUebernehmen(einzigerKandidat, slot)) {
                        slot.zugewiesenePerson = einzigerKandidat;
                        personDienste.get(einzigerKandidat.getId()).add(slot.datum);
                        changed = true;
                        logger.debug("Naked Single: {} -> {} {}",
                                    einzigerKandidat.getName(), slot.datum, slot.dienstArt.getKurzName());
                    }
                }
            }

        } while (changed && iterations < maxIterations);

        long zugewiesen = dienstSlots.stream().filter(s -> s.zugewiesenePerson != null).count();
        logger.debug("Constraint Propagation beendet nach {} Iterationen. Zugewiesen: {}/{}",
                    iterations, zugewiesen, dienstSlots.size());
    }

    /**
     * Sortiert die Slots nach der MCV-Heuristik (Most Constrained Variable).
     * Slots mit weniger möglichen Kandidaten werden zuerst bearbeitet.
     */
    private void sortSlotsByMCV() {
        // Nur nicht zugewiesene Slots neu sortieren, aber Reihenfolge stabil halten
        dienstSlots.sort((s1, s2) -> {
            // Bereits zugewiesene Slots ans Ende
            if (s1.zugewiesenePerson != null && s2.zugewiesenePerson == null) return 1;
            if (s1.zugewiesenePerson == null && s2.zugewiesenePerson != null) return -1;
            if (s1.zugewiesenePerson != null && s2.zugewiesenePerson != null) return 0;

            // MCV: Weniger Kandidaten = höhere Priorität
            int kandidaten1 = s1.moeglicheKandidaten != null ? s1.moeglicheKandidaten.size() : Integer.MAX_VALUE;
            int kandidaten2 = s2.moeglicheKandidaten != null ? s2.moeglicheKandidaten.size() : Integer.MAX_VALUE;

            if (kandidaten1 != kandidaten2) {
                return Integer.compare(kandidaten1, kandidaten2);
            }

            // Bei gleicher Anzahl: Nach Datum sortieren
            int datumCompare = s1.datum.compareTo(s2.datum);
            if (datumCompare != 0) return datumCompare;

            return Integer.compare(s1.dienstArt.ordinal(), s2.dienstArt.ordinal());
        });

        logger.debug("Slots nach MCV-Heuristik sortiert");
    }

    /**
     * Rekursiver Backtracking-Algorithmus
     */
    private boolean backtrackDienstplanErstellen(int slotIndex) {
        // Fortschritt melden
        if (progressCallback != null && dienstSlots.size() > 0) {
            progressCallback.accept((double) slotIndex / dienstSlots.size());
        }

        // Basis: Alle Slots verarbeitet
        if (slotIndex >= dienstSlots.size()) {
            return true;
        }

        DienstSlot slot = dienstSlots.get(slotIndex);

        // Bereits zugewiesen (durch Constraint Propagation) - überspringen
        if (slot.zugewiesenePerson != null) {
            return backtrackDienstplanErstellen(slotIndex + 1);
        }

        logger.debug("Verarbeite Slot {}: {} {}", slotIndex, slot.datum, slot.dienstArt);

        // Kandidaten für diesen Slot finden
        List<Person> kandidaten = findKandidatenFuerSlot(slot);

        // Kandidaten nach optimaler Reihenfolge sortieren
        kandidaten.sort((p1, p2) -> comparePersonenFuerSlot(p1, p2, slot));

        // Jeden Kandidaten probieren
        for (Person kandidat : kandidaten) {
            if (kannPersonDienstUebernehmen(kandidat, slot)) {

                // Temporäre Zuweisung
                slot.zugewiesenePerson = kandidat;
                personDienste.get(kandidat.getId()).add(slot.datum);

                // Rekursiver Aufruf für nächsten Slot
                if (backtrackDienstplanErstellen(slotIndex + 1)) {
                    return true; // Lösung gefunden
                }

                // Backtrack: Zuweisung rückgängig machen
                slot.zugewiesenePerson = null;
                personDienste.get(kandidat.getId()).remove(slot.datum);
            }
        }

        // Kein Kandidat gefunden - Slot leer lassen mit Warnung
        logger.warn("Kein Kandidat für Slot {}: {} {}", slotIndex, slot.datum, slot.dienstArt);
        warnungen.add(String.format("MANUELL ZUWEISEN: %s %s - Keine Person verfügbar",
                     slot.datum, slot.dienstArt.getVollName()));

        // Mit leerem Slot weitermachen
        return backtrackDienstplanErstellen(slotIndex + 1);
    }

    /**
     * Findet alle möglichen Kandidaten für einen Dienstslot
     */
    private List<Person> findKandidatenFuerSlot(DienstSlot slot) {
        return verfuegbarePersonen.stream()
                .filter(person -> grundlegendeVerfuegbarkeitPruefen(person, slot))
                .collect(Collectors.toList());
    }

    /**
     * Grundlegende Verfügbarkeitsprüfung (harte Regeln)
     */
    private boolean grundlegendeVerfuegbarkeitPruefen(Person person, DienstSlot slot) {
        // 1. Person muss diese Dienstart können
        if (!person.kannDienstArt(slot.dienstArt)) {
            return false;
        }

        // 2. Person muss an diesem Wochentag arbeiten
        Wochentag wochentag = Wochentag.fromLocalDate(slot.datum);
        if (!person.arbeitetAnWochentag(wochentag)) {
            return false;
        }

        // 3. Person darf nicht schon einen Dienst an diesem Tag haben
        // TODO: MonatsWunsch (Urlaub) Prüfung wird in Phase 3 implementiert
        if (personDienste.get(person.getId()).contains(slot.datum)) {
            return false;
        }

        return true;
    }

    /**
     * Detaillierte Prüfung ob Person Dienst übernehmen kann (inkl. Ruhezeiten und Limits)
     */
    private boolean kannPersonDienstUebernehmen(Person person, DienstSlot slot) {
        List<LocalDate> personsDienste = personDienste.get(person.getId());

        // Harte Regel: Nach Dienst → nächster Tag frei
        LocalDate vortag = slot.datum.minusDays(1);
        if (personsDienste.contains(vortag)) {
            return false;
        }

        // TODO: Nach Urlaub → erster Tag frei (wird in Phase 3 mit MonatsWunsch implementiert)

        // Maximale Dienstanzahl pro Person prüfen
        int maxDienste = person.getAnzahlDienste();
        if (maxDienste > 0 && personsDienste.size() >= maxDienste) {
            return false;
        }

        return true;
    }

    /**
     * Vergleichsfunktion für optimale Kandidatenreihenfolge
     */
    private int comparePersonenFuerSlot(Person p1, Person p2, DienstSlot slot) {
        // 1. Priorität: Personen die noch unter ihrem Soll sind
        int sollVergleich = compareSollErfuellung(p1, p2);
        if (sollVergleich != 0) {
            return sollVergleich;
        }

        // 2. Priorität: Abstand-Score (größere Abstände bevorzugen)
        double abstandScore1 = berechneAbstandScore(p1, slot.datum);
        double abstandScore2 = berechneAbstandScore(p2, slot.datum);
        if (Math.abs(abstandScore1 - abstandScore2) > EPSILON) {
            return Double.compare(abstandScore2, abstandScore1); // Höherer Score = besser
        }

        // 3. Priorität: Weniger Dienste bisher (gleichmäßige Verteilung)
        int anzahlDienste1 = personDienste.get(p1.getId()).size();
        int anzahlDienste2 = personDienste.get(p2.getId()).size();
        if (anzahlDienste1 != anzahlDienste2) {
            return Integer.compare(anzahlDienste1, anzahlDienste2);
        }

        // 4. Priorität: Dienstarten-Balance
        int dienstartBalance1 = berechneDienstartBalance(p1, slot.dienstArt);
        int dienstartBalance2 = berechneDienstartBalance(p2, slot.dienstArt);
        if (dienstartBalance1 != dienstartBalance2) {
            return Integer.compare(dienstartBalance1, dienstartBalance2);
        }

        // 5. Fallback: Alphabetisch nach Name (für Determinismus)
        return p1.getName().compareTo(p2.getName());
    }

    /**
     * Vergleicht zwei Personen nach ihrer Soll-Erfüllung.
     * Personen die noch unter ihrem Soll sind, haben Priorität.
     */
    private int compareSollErfuellung(Person p1, Person p2) {
        int soll1 = p1.getAnzahlDienste();
        int soll2 = p2.getAnzahlDienste();
        int ist1 = personDienste.get(p1.getId()).size();
        int ist2 = personDienste.get(p2.getId()).size();

        // Wenn eine Person kein Soll hat (0), wird sie anders behandelt
        boolean p1HatSoll = soll1 > 0;
        boolean p2HatSoll = soll2 > 0;

        if (p1HatSoll && !p2HatSoll) {
            // p1 hat Soll, p2 nicht → p1 bevorzugen wenn unter Soll
            return ist1 < soll1 ? -1 : 1;
        }
        if (!p1HatSoll && p2HatSoll) {
            // p2 hat Soll, p1 nicht → p2 bevorzugen wenn unter Soll
            return ist2 < soll2 ? 1 : -1;
        }
        if (!p1HatSoll && !p2HatSoll) {
            // Beide ohne Soll → kein Unterschied
            return 0;
        }

        // Beide haben Soll: Vergleiche Erfüllungsgrad
        double erfuellung1 = (double) ist1 / soll1;
        double erfuellung2 = (double) ist2 / soll2;

        // Niedrigerer Erfüllungsgrad = höhere Priorität
        return Double.compare(erfuellung1, erfuellung2);
    }

    /**
     * Berechnet Abstand-Score: Je größer die Abstände zu anderen Diensten, desto besser
     */
    private double berechneAbstandScore(Person person, LocalDate neuerDienst) {
        List<LocalDate> dienste = personDienste.get(person.getId());

        if (dienste.isEmpty()) {
            return SCORE_KEINE_BISHERIGEN_DIENSTE; // Sehr hoch wenn noch keine Dienste
        }

        // Berechne minimalen Abstand zu vorhandenen Diensten
        int minAbstand = dienste.stream()
                .mapToInt(datum -> Math.abs((int) datum.until(neuerDienst).getDays()))
                .min()
                .orElse(MAX_ABSTAND_TAGE);

        // Score: Je größer der minimale Abstand, desto besser
        return minAbstand;
    }

    /**
     * Berechnet Dienstarten-Balance: Wie viele von dieser Art hat die Person schon?
     */
    private int berechneDienstartBalance(Person person, DienstArt dienstArt) {
        // Zähle wie oft diese Person schon diese Dienstart hatte
        return (int) dienstSlots.stream()
                .filter(slot -> Objects.equals(slot.zugewiesenePerson, person))
                .filter(slot -> slot.dienstArt == dienstArt)
                .count();
    }

    /**
     * Initialisierung der Datenstrukturen
     */
    private void initializeStructures() {
        dienstSlots = new ArrayList<>();
        warnungen.clear();

        // PersonDienste-Map initialisieren
        for (Person person : verfuegbarePersonen) {
            personDienste.put(person.getId(), new ArrayList<>());
        }
    }

    /**
     * Erstellt alle DienstSlots für den Monat
     */
    private void createDienstSlots() {
        LocalDate startDatum = zielmonat.atDay(1);
        LocalDate endDatum = zielmonat.atEndOfMonth();

        for (LocalDate datum = startDatum; !datum.isAfter(endDatum); datum = datum.plusDays(1)) {
            Wochentag wochentag = Wochentag.fromLocalDate(datum);

            // Für jeden Tag die entsprechenden Dienstarten erstellen
            for (Map.Entry<DienstArt, Set<Wochentag>> entry : dienstartWochentage.entrySet()) {
                DienstArt dienstArt = entry.getKey();
                Set<Wochentag> erlaubteWochentage = entry.getValue();

                if (erlaubteWochentage.contains(wochentag)) {
                    dienstSlots.add(new DienstSlot(datum, dienstArt));
                }
            }
        }

        // Slots nach Datum sortieren für konsistente Verarbeitung
        dienstSlots.sort(Comparator.comparing((DienstSlot s) -> s.datum)
                                  .thenComparing(s -> s.dienstArt.ordinal()));

        logger.info("Erstellt {} Dienstslots für {}", dienstSlots.size(), zielmonat);
    }

    /**
     * Erstellt Dienstplan-Objekt aus den zugewiesenen Slots
     */
    private Dienstplan createDienstplanFromSlots() {
        Dienstplan dienstplan = new Dienstplan("Dienstplan " + zielmonat, zielmonat);

        for (DienstSlot slot : dienstSlots) {
            Dienst dienst = new Dienst(slot.datum, slot.dienstArt);

            if (slot.zugewiesenePerson != null) {
                dienst.zuweisen(slot.zugewiesenePerson);
                dienst.setStatus(DienstStatus.GEPLANT);
            } else {
                dienst.setStatus(DienstStatus.ABGESAGT); // Markierung für "manuell zuweisen"
                dienst.setBemerkung("MANUELL ZUWEISEN - Keine Person verfügbar");
            }

            dienstplan.addDienst(dienst);
        }

        return dienstplan;
    }

    /**
     * Konfiguriert welche Dienstarten an welchen Wochentagen stattfinden
     */
    private Map<DienstArt, Set<Wochentag>> initializeDienstartWochentage() {
        Map<DienstArt, Set<Wochentag>> mapping = new EnumMap<>(DienstArt.class);

        // 24h Dienste: Mo-So (alle Tage)
        mapping.put(DienstArt.DIENST_24H, EnumSet.allOf(Wochentag.class));

        // Visten: Nur Sa+So (Wochenende)
        mapping.put(DienstArt.VISTEN, EnumSet.of(Wochentag.SAMSTAG, Wochentag.SONNTAG));

        // Spätdienst: Mo-Fr (Werktage)
        mapping.put(DienstArt.SPAET, EnumSet.of(
            Wochentag.MONTAG, Wochentag.DIENSTAG, Wochentag.MITTWOCH,
            Wochentag.DONNERSTAG, Wochentag.FREITAG));

        return mapping;
    }

    // Hilfsdatenstrukturen

    /**
     * Repräsentiert einen zu besetzenden Dienstslot
     */
    private static class DienstSlot {
        final LocalDate datum;
        final DienstArt dienstArt;
        Person zugewiesenePerson;
        List<Person> moeglicheKandidaten; // Für MCV-Heuristik

        DienstSlot(LocalDate datum, DienstArt dienstArt) {
            this.datum = datum;
            this.dienstArt = dienstArt;
        }

        @Override
        public String toString() {
            return String.format("%s %s -> %s",
                datum, dienstArt.getKurzName(),
                zugewiesenePerson != null ? zugewiesenePerson.getName() : "LEER");
        }
    }

    /**
     * Ergebnis der Dienstplan-Generierung
     */
    public static class DienstplanGenerierungResult {
        private final Dienstplan dienstplan;
        private final Set<String> warnungen;
        private final boolean erfolgreich;

        public DienstplanGenerierungResult(Dienstplan dienstplan, Set<String> warnungen, boolean erfolgreich) {
            this.dienstplan = dienstplan;
            this.warnungen = warnungen != null ? warnungen : new HashSet<>();
            this.erfolgreich = erfolgreich;
        }

        public Dienstplan getDienstplan() { return dienstplan; }
        public Set<String> getWarnungen() { return warnungen; }
        public boolean istErfolgreich() { return erfolgreich; }
        public boolean hatWarnungen() { return !warnungen.isEmpty(); }

        public String getZusammenfassung() {
            StringBuilder sb = new StringBuilder();
            sb.append("Generierung: ").append(erfolgreich ? "Erfolgreich" : "Fehlgeschlagen");
            if (dienstplan != null) {
                sb.append(", Dienste: ").append(dienstplan.getDienste().size());
                sb.append(", Zuweisungsgrad: ").append(String.format("%.1f%%", dienstplan.getZuweisungsgrad()));
            }
            if (hatWarnungen()) {
                sb.append(", Warnungen: ").append(warnungen.size());
            }
            return sb.toString();
        }
    }
}
