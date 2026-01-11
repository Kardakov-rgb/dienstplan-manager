package de.dienstplan.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Repräsentiert eine Abwesenheit einer Person (Urlaub, Krankheit, etc.)
 */
public class Abwesenheit {
    
    private Long id;
    private Long personId;
    private LocalDate startDatum;
    private LocalDate endDatum;
    private AbwesenheitsArt art;
    private String bemerkung;
    
    // Konstruktoren
    public Abwesenheit() {
    }
    
    public Abwesenheit(LocalDate startDatum, LocalDate endDatum, AbwesenheitsArt art) {
        this.startDatum = startDatum;
        this.endDatum = endDatum;
        this.art = art;
        
        // Sicherstellen, dass startDatum <= endDatum
        if (startDatum != null && endDatum != null && startDatum.isAfter(endDatum)) {
            throw new IllegalArgumentException("Startdatum darf nicht nach Enddatum liegen");
        }
    }
    
    public Abwesenheit(LocalDate datum, AbwesenheitsArt art) {
        this(datum, datum, art);
    }
    
    public Abwesenheit(Long id, Long personId, LocalDate startDatum, 
                      LocalDate endDatum, AbwesenheitsArt art, String bemerkung) {
        this(startDatum, endDatum, art);
        this.id = id;
        this.personId = personId;
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
    
    public LocalDate getStartDatum() {
        return startDatum;
    }
    
    public void setStartDatum(LocalDate startDatum) {
        if (startDatum != null && this.endDatum != null && startDatum.isAfter(this.endDatum)) {
            throw new IllegalArgumentException("Startdatum darf nicht nach Enddatum liegen");
        }
        this.startDatum = startDatum;
    }
    
    public LocalDate getEndDatum() {
        return endDatum;
    }
    
    public void setEndDatum(LocalDate endDatum) {
        if (endDatum != null && this.startDatum != null && endDatum.isBefore(this.startDatum)) {
            throw new IllegalArgumentException("Enddatum darf nicht vor Startdatum liegen");
        }
        this.endDatum = endDatum;
    }
    
    public AbwesenheitsArt getArt() {
        return art;
    }
    
    public void setArt(AbwesenheitsArt art) {
        this.art = art;
    }
    
    public String getBemerkung() {
        return bemerkung;
    }
    
    public void setBemerkung(String bemerkung) {
        this.bemerkung = bemerkung;
    }
    
    // Helper Methoden
    
    /**
     * Prüft ob ein bestimmtes Datum in der Abwesenheit liegt
     * @param datum Das zu prüfende Datum
     * @return true wenn das Datum in der Abwesenheit liegt
     */
    public boolean istAbwesend(LocalDate datum) {
        if (datum == null || startDatum == null || endDatum == null) {
            return false;
        }
        return !datum.isBefore(startDatum) && !datum.isAfter(endDatum);
    }
    
    /**
     * Berechnet die Anzahl der Tage der Abwesenheit (inklusive Start- und Enddatum)
     * @return Anzahl der Tage
     */
    public long getAnzahlTage() {
        if (startDatum == null || endDatum == null) {
            return 0;
        }
        return startDatum.until(endDatum).getDays() + 1; // +1 weil beide Tage inklusive
    }
    
    /**
     * Prüft ob diese Abwesenheit mit einer anderen überlappt
     * @param andere Die andere Abwesenheit
     * @return true wenn sie sich überlappen
     */
    public boolean ueberlapptMit(Abwesenheit andere) {
        if (andere == null || startDatum == null || endDatum == null || 
            andere.startDatum == null || andere.endDatum == null) {
            return false;
        }
        
        return !(endDatum.isBefore(andere.startDatum) || startDatum.isAfter(andere.endDatum));
    }
    
    /**
     * Erstellt eine Kopie der Abwesenheit
     * @return Eine neue Instanz mit den gleichen Werten
     */
    public Abwesenheit copy() {
        return new Abwesenheit(this.id, this.personId, this.startDatum, 
                              this.endDatum, this.art, this.bemerkung);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Abwesenheit that = (Abwesenheit) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        String zeitraum = startDatum != null && endDatum != null 
            ? (startDatum.equals(endDatum) 
                ? startDatum.toString() 
                : startDatum + " bis " + endDatum)
            : "Kein Datum";
            
        return "Abwesenheit{" +
                "art=" + (art != null ? art.getVollName() : "Unbekannt") +
                ", zeitraum=" + zeitraum +
                (bemerkung != null && !bemerkung.trim().isEmpty() ? ", bemerkung='" + bemerkung + '\'' : "") +
                '}';
    }
}