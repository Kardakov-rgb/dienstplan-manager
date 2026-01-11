package de.dienstplan.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Repräsentiert einen einzelnen Dienst im Dienstplan
 */
public class Dienst {
    
    private Long id;
    private LocalDate datum;
    private DienstArt art;
    private Long personId;
    private String personName; // Denormalisiert für einfachere Anzeige
    private DienstStatus status;
    private String bemerkung;
    
    // Konstruktoren
    public Dienst() {
        this.status = DienstStatus.GEPLANT;
    }
    
    public Dienst(LocalDate datum, DienstArt art) {
        this();
        this.datum = datum;
        this.art = art;
    }
    
    public Dienst(LocalDate datum, DienstArt art, Long personId) {
        this(datum, art);
        this.personId = personId;
    }
    
    public Dienst(Long id, LocalDate datum, DienstArt art, Long personId, 
                  String personName, DienstStatus status, String bemerkung) {
        this.id = id;
        this.datum = datum;
        this.art = art;
        this.personId = personId;
        this.personName = personName;
        this.status = status != null ? status : DienstStatus.GEPLANT;
        this.bemerkung = bemerkung;
    }
    
    // Getter und Setter
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public LocalDate getDatum() {
        return datum;
    }
    
    public void setDatum(LocalDate datum) {
        this.datum = datum;
    }
    
    public DienstArt getArt() {
        return art;
    }
    
    public void setArt(DienstArt art) {
        this.art = art;
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
    
    public DienstStatus getStatus() {
        return status;
    }
    
    public void setStatus(DienstStatus status) {
        this.status = status != null ? status : DienstStatus.GEPLANT;
    }
    
    public String getBemerkung() {
        return bemerkung;
    }
    
    public void setBemerkung(String bemerkung) {
        this.bemerkung = bemerkung;
    }
    
    // Helper Methoden
    
    /**
     * Prüft ob der Dienst einer Person zugewiesen ist
     * @return true wenn eine Person zugewiesen ist
     */
    public boolean istZugewiesen() {
        return personId != null;
    }
    
    /**
     * Prüft ob der Dienst noch offen (nicht zugewiesen) ist
     * @return true wenn kein Person zugewiesen ist
     */
    public boolean istOffen() {
        return personId == null;
    }
    
    /**
     * Prüft ob der Dienst bestätigt ist
     * @return true wenn der Status BESTAETIGT ist
     */
    public boolean istBestaetigt() {
        return status == DienstStatus.BESTAETIGT;
    }
    
    /**
     * Prüft ob der Dienst abgesagt wurde
     * @return true wenn der Status ABGESAGT ist
     */
    public boolean istAbgesagt() {
        return status == DienstStatus.ABGESAGT;
    }
    
    /**
     * Ermittelt den Wochentag des Dienstes
     * @return Der Wochentag oder null wenn kein Datum gesetzt
     */
    public Wochentag getWochentag() {
        return datum != null ? Wochentag.fromLocalDate(datum) : null;
    }
    
    /**
     * Erstellt eine Kopie des Dienstes
     * @return Eine neue Instanz mit den gleichen Werten
     */
    public Dienst copy() {
        return new Dienst(this.id, this.datum, this.art, this.personId, 
                         this.personName, this.status, this.bemerkung);
    }
    
    /**
     * Erstellt eine Kopie ohne ID (für neue Dienste)
     * @return Eine neue Instanz ohne ID
     */
    public Dienst copyWithoutId() {
        return new Dienst(null, this.datum, this.art, this.personId, 
                         this.personName, this.status, this.bemerkung);
    }
    
    /**
     * Weist den Dienst einer Person zu
     * @param person Die Person
     */
    public void zuweisen(Person person) {
        if (person != null) {
            this.personId = person.getId();
            this.personName = person.getName();
        }
    }
    
    /**
     * Entfernt die Zuweisung des Dienstes
     */
    public void zuweisingEntfernen() {
        this.personId = null;
        this.personName = null;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Dienst dienst = (Dienst) o;
        return Objects.equals(id, dienst.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        String personInfo = personName != null ? personName : "Nicht zugewiesen";
        String datumStr = datum != null ? datum.toString() : "Kein Datum";
        String artStr = art != null ? art.getKurzName() : "Unbekannt";
        
        return "Dienst{" +
                "datum=" + datumStr +
                ", art=" + artStr +
                ", person='" + personInfo + '\'' +
                ", status=" + status +
                (bemerkung != null && !bemerkung.trim().isEmpty() ? ", bemerkung='" + bemerkung + '\'' : "") +
                '}';
    }
}