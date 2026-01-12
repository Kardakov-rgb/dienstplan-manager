package de.dienstplan.model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Repräsentiert einen monatlichen Wunsch einer Person.
 * Kann ein Urlaub, Freiwunsch oder Dienstwunsch sein.
 */
public class MonatsWunsch {

    private Long id;
    private Long personId;
    private String personName; // Denormalisiert für Anzeige
    private YearMonth monat;
    private LocalDate datum;
    private WunschTyp typ;
    private Boolean erfuellt; // null = noch nicht ausgewertet
    private String bemerkung;

    // Konstruktoren

    public MonatsWunsch() {
    }

    public MonatsWunsch(Long personId, LocalDate datum, WunschTyp typ) {
        this.personId = personId;
        this.datum = datum;
        this.typ = typ;
        this.monat = YearMonth.from(datum);
    }

    public MonatsWunsch(Long personId, String personName, LocalDate datum, WunschTyp typ) {
        this(personId, datum, typ);
        this.personName = personName;
    }

    public MonatsWunsch(Long id, Long personId, String personName, YearMonth monat,
                        LocalDate datum, WunschTyp typ, Boolean erfuellt, String bemerkung) {
        this.id = id;
        this.personId = personId;
        this.personName = personName;
        this.monat = monat;
        this.datum = datum;
        this.typ = typ;
        this.erfuellt = erfuellt;
        this.bemerkung = bemerkung;
    }

    // Getter und Setter

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public LocalDate getDatum() {
        return datum;
    }

    public void setDatum(LocalDate datum) {
        this.datum = datum;
        if (datum != null) {
            this.monat = YearMonth.from(datum);
        }
    }

    public WunschTyp getTyp() {
        return typ;
    }

    public void setTyp(WunschTyp typ) {
        this.typ = typ;
    }

    public Boolean getErfuellt() {
        return erfuellt;
    }

    public void setErfuellt(Boolean erfuellt) {
        this.erfuellt = erfuellt;
    }

    public String getBemerkung() {
        return bemerkung;
    }

    public void setBemerkung(String bemerkung) {
        this.bemerkung = bemerkung;
    }

    // Hilfsmethoden

    /**
     * Prüft ob dieser Wunsch ein hartes Constraint ist (muss erfüllt werden).
     */
    public boolean isHartesConstraint() {
        return typ != null && typ.isHartesConstraint();
    }

    /**
     * Prüft ob dieser Wunsch ein Urlaub ist.
     */
    public boolean isUrlaub() {
        return typ == WunschTyp.URLAUB;
    }

    /**
     * Prüft ob dieser Wunsch ein Freiwunsch ist.
     */
    public boolean isFreiwunsch() {
        return typ == WunschTyp.FREIWUNSCH;
    }

    /**
     * Prüft ob dieser Wunsch ein Dienstwunsch ist.
     */
    public boolean isDienstwunsch() {
        return typ == WunschTyp.DIENSTWUNSCH;
    }

    /**
     * Prüft ob dieser Wunsch bereits ausgewertet wurde.
     */
    public boolean isAusgewertet() {
        return erfuellt != null;
    }

    /**
     * Prüft ob dieser Wunsch erfüllt wurde.
     * Gibt false zurück wenn noch nicht ausgewertet.
     */
    public boolean wurdeErfuellt() {
        return Boolean.TRUE.equals(erfuellt);
    }

    /**
     * Markiert diesen Wunsch als erfüllt.
     */
    public void markiereAlsErfuellt() {
        this.erfuellt = true;
    }

    /**
     * Markiert diesen Wunsch als nicht erfüllt.
     */
    public void markiereAlsNichtErfuellt() {
        this.erfuellt = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MonatsWunsch that = (MonatsWunsch) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("MonatsWunsch{person=%s, datum=%s, typ=%s, erfuellt=%s}",
            personName != null ? personName : personId,
            datum,
            typ != null ? typ.getKuerzel() : "?",
            erfuellt != null ? erfuellt : "?");
    }
}
