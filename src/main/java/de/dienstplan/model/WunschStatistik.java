package de.dienstplan.model;

import java.time.YearMonth;

/**
 * Statistik über die Wunscherfüllung einer Person für einen Monat.
 */
public class WunschStatistik {

    private Long personId;
    private String personName;
    private YearMonth monat;

    // Urlaub (wird immer erfüllt)
    private int anzahlUrlaub;

    // Freiwünsche
    private int anzahlFreiwuensche;
    private int erfuellteFreiwuensche;

    // Dienstwünsche
    private int anzahlDienstwuensche;
    private int erfuellteDienstwuensche;

    // Konstruktoren

    public WunschStatistik() {
    }

    public WunschStatistik(Long personId, String personName, YearMonth monat) {
        this.personId = personId;
        this.personName = personName;
        this.monat = monat;
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

    public YearMonth getMonat() {
        return monat;
    }

    public void setMonat(YearMonth monat) {
        this.monat = monat;
    }

    public int getAnzahlUrlaub() {
        return anzahlUrlaub;
    }

    public void setAnzahlUrlaub(int anzahlUrlaub) {
        this.anzahlUrlaub = anzahlUrlaub;
    }

    public int getAnzahlFreiwuensche() {
        return anzahlFreiwuensche;
    }

    public void setAnzahlFreiwuensche(int anzahlFreiwuensche) {
        this.anzahlFreiwuensche = anzahlFreiwuensche;
    }

    public int getErfuellteFreiwuensche() {
        return erfuellteFreiwuensche;
    }

    public void setErfuellteFreiwuensche(int erfuellteFreiwuensche) {
        this.erfuellteFreiwuensche = erfuellteFreiwuensche;
    }

    public int getAnzahlDienstwuensche() {
        return anzahlDienstwuensche;
    }

    public void setAnzahlDienstwuensche(int anzahlDienstwuensche) {
        this.anzahlDienstwuensche = anzahlDienstwuensche;
    }

    public int getErfuellteDienstwuensche() {
        return erfuellteDienstwuensche;
    }

    public void setErfuellteDienstwuensche(int erfuellteDienstwuensche) {
        this.erfuellteDienstwuensche = erfuellteDienstwuensche;
    }

    // Berechnete Werte

    /**
     * Gesamtanzahl der weichen Wünsche (Freiwünsche + Dienstwünsche).
     */
    public int getAnzahlWeicheWuensche() {
        return anzahlFreiwuensche + anzahlDienstwuensche;
    }

    /**
     * Anzahl erfüllter weicher Wünsche.
     */
    public int getErfuellteWeicheWuensche() {
        return erfuellteFreiwuensche + erfuellteDienstwuensche;
    }

    /**
     * Erfüllungsquote für Freiwünsche (0.0 - 1.0).
     * Gibt 1.0 zurück wenn keine Freiwünsche vorhanden.
     */
    public double getFreiwunschErfuellungsquote() {
        if (anzahlFreiwuensche == 0) {
            return 1.0;
        }
        return (double) erfuellteFreiwuensche / anzahlFreiwuensche;
    }

    /**
     * Erfüllungsquote für Dienstwünsche (0.0 - 1.0).
     * Gibt 1.0 zurück wenn keine Dienstwünsche vorhanden.
     */
    public double getDienstwunschErfuellungsquote() {
        if (anzahlDienstwuensche == 0) {
            return 1.0;
        }
        return (double) erfuellteDienstwuensche / anzahlDienstwuensche;
    }

    /**
     * Gesamte Erfüllungsquote für alle weichen Wünsche (0.0 - 1.0).
     * Gibt 1.0 zurück wenn keine weichen Wünsche vorhanden.
     */
    public double getGesamtErfuellungsquote() {
        int gesamt = getAnzahlWeicheWuensche();
        if (gesamt == 0) {
            return 1.0;
        }
        return (double) getErfuellteWeicheWuensche() / gesamt;
    }

    /**
     * Gibt die Erfüllungsquote als Prozent-String zurück (z.B. "75%").
     */
    public String getErfuellungsquoteAlsProzent() {
        return String.format("%.0f%%", getGesamtErfuellungsquote() * 100);
    }

    /**
     * Gibt einen zusammenfassenden String für Freiwünsche zurück (z.B. "2/3").
     */
    public String getFreiwuenscheZusammenfassung() {
        return erfuellteFreiwuensche + "/" + anzahlFreiwuensche;
    }

    /**
     * Gibt einen zusammenfassenden String für Dienstwünsche zurück (z.B. "1/2").
     */
    public String getDienstwuenscheZusammenfassung() {
        return erfuellteDienstwuensche + "/" + anzahlDienstwuensche;
    }

    // Inkrementier-Methoden für einfache Statistik-Erstellung

    public void incrementUrlaub() {
        this.anzahlUrlaub++;
    }

    public void incrementFreiwunsch(boolean erfuellt) {
        this.anzahlFreiwuensche++;
        if (erfuellt) {
            this.erfuellteFreiwuensche++;
        }
    }

    public void incrementDienstwunsch(boolean erfuellt) {
        this.anzahlDienstwuensche++;
        if (erfuellt) {
            this.erfuellteDienstwuensche++;
        }
    }

    @Override
    public String toString() {
        return String.format("WunschStatistik{person=%s, monat=%s, erfuellung=%s}",
            personName != null ? personName : personId,
            monat,
            getErfuellungsquoteAlsProzent());
    }
}
