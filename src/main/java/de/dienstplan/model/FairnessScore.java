package de.dienstplan.model;

/**
 * Fairness-Score einer Person basierend auf der historischen Wunscherfüllung.
 * Personen mit niedrigerem Score haben höhere Priorität bei der nächsten Planung.
 */
public class FairnessScore {

    private Long personId;
    private String personName;

    // Historische Daten (über alle erfassten Monate)
    private int anzahlMonate;
    private int gesamtWuensche;
    private int erfuellteWuensche;

    // Berechneter Score
    private double durchschnittlicheErfuellung;
    private int prioritaet; // 1 = höchste Priorität (niedrigste Erfüllung)

    // Konstruktoren

    public FairnessScore() {
    }

    public FairnessScore(Long personId, String personName) {
        this.personId = personId;
        this.personName = personName;
    }

    // Getter und Setter

    public Long getPersonId() {
        return personId;
    }

    public void setPersonId(Long personId) {
        this.personId = personId;
    }

    public String getPersonName() {
        return personName;
    }

    public void setPersonName(String personName) {
        this.personName = personName;
    }

    public int getAnzahlMonate() {
        return anzahlMonate;
    }

    public void setAnzahlMonate(int anzahlMonate) {
        this.anzahlMonate = anzahlMonate;
    }

    public int getGesamtWuensche() {
        return gesamtWuensche;
    }

    public void setGesamtWuensche(int gesamtWuensche) {
        this.gesamtWuensche = gesamtWuensche;
    }

    public int getErfuellteWuensche() {
        return erfuellteWuensche;
    }

    public void setErfuellteWuensche(int erfuellteWuensche) {
        this.erfuellteWuensche = erfuellteWuensche;
    }

    public double getDurchschnittlicheErfuellung() {
        return durchschnittlicheErfuellung;
    }

    public void setDurchschnittlicheErfuellung(double durchschnittlicheErfuellung) {
        this.durchschnittlicheErfuellung = durchschnittlicheErfuellung;
    }

    public int getPrioritaet() {
        return prioritaet;
    }

    public void setPrioritaet(int prioritaet) {
        this.prioritaet = prioritaet;
    }

    // Berechnungen

    /**
     * Berechnet die durchschnittliche Erfüllungsquote.
     */
    public void berechneErfuellung() {
        if (gesamtWuensche == 0) {
            this.durchschnittlicheErfuellung = 1.0; // Keine Wünsche = "voll erfüllt"
        } else {
            this.durchschnittlicheErfuellung = (double) erfuellteWuensche / gesamtWuensche;
        }
    }

    /**
     * Fügt Daten eines Monats hinzu.
     *
     * @param wuensche Anzahl der Wünsche im Monat
     * @param erfuellt Anzahl der erfüllten Wünsche im Monat
     */
    public void addMonatsDaten(int wuensche, int erfuellt) {
        this.anzahlMonate++;
        this.gesamtWuensche += wuensche;
        this.erfuellteWuensche += erfuellt;
        berechneErfuellung();
    }

    /**
     * Gibt die Erfüllung als Prozent-String zurück.
     */
    public String getErfuellungAlsProzent() {
        return String.format("%.0f%%", durchschnittlicheErfuellung * 100);
    }

    /**
     * Gibt an, ob diese Person bei der nächsten Planung bevorzugt werden sollte.
     * Personen mit Erfüllung unter 70% werden als "benachteiligt" betrachtet.
     */
    public boolean istBenachteiligt() {
        return durchschnittlicheErfuellung < 0.7 && gesamtWuensche > 0;
    }

    /**
     * Berechnet einen Score-Wert für die Sortierung.
     * Niedrigere Erfüllung = höherer Score = höhere Priorität.
     *
     * @return Score zwischen 0 und 100
     */
    public double getScoreFuerSortierung() {
        if (gesamtWuensche == 0) {
            return 50.0; // Neutral wenn keine Daten
        }
        // Invertieren: 0% Erfüllung = 100 Punkte, 100% Erfüllung = 0 Punkte
        return (1.0 - durchschnittlicheErfuellung) * 100;
    }

    @Override
    public String toString() {
        return String.format("FairnessScore{person=%s, erfuellung=%s, monate=%d, prioritaet=%d}",
            personName != null ? personName : personId,
            getErfuellungAlsProzent(),
            anzahlMonate,
            prioritaet);
    }
}
