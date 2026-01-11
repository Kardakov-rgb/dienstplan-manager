package de.dienstplan.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Repräsentiert eine Person/Mitarbeiter im Dienstplan-System
 */
public class Person {
    
    private Long id;
    private String name;
    private int anzahlDienste;
    private EnumSet<Wochentag> arbeitsTage;
    private List<Abwesenheit> abwesenheiten;
    private EnumSet<DienstArt> verfuegbareDienstArten;
    
    // Konstruktoren
    public Person() {
        this.abwesenheiten = new ArrayList<>();
        this.arbeitsTage = EnumSet.noneOf(Wochentag.class);
        this.verfuegbareDienstArten = EnumSet.noneOf(DienstArt.class);
    }
    
    public Person(String name) {
        this();
        this.name = name;
    }
    
    public Person(Long id, String name, int anzahlDienste, 
                  EnumSet<Wochentag> arbeitsTage, EnumSet<DienstArt> verfuegbareDienstArten) {
        this();
        this.id = id;
        this.name = name;
        this.anzahlDienste = anzahlDienste;
        this.arbeitsTage = arbeitsTage != null ? arbeitsTage.clone() : EnumSet.noneOf(Wochentag.class);
        this.verfuegbareDienstArten = verfuegbareDienstArten != null ? 
            verfuegbareDienstArten.clone() : EnumSet.noneOf(DienstArt.class);
    }
    
    // Getter und Setter
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public int getAnzahlDienste() {
        return anzahlDienste;
    }
    
    public void setAnzahlDienste(int anzahlDienste) {
        this.anzahlDienste = anzahlDienste;
    }
    
    public EnumSet<Wochentag> getArbeitsTage() {
        return arbeitsTage.clone();
    }
    
    public void setArbeitsTage(EnumSet<Wochentag> arbeitsTage) {
        this.arbeitsTage = arbeitsTage != null ? arbeitsTage.clone() : EnumSet.noneOf(Wochentag.class);
    }
    
    public List<Abwesenheit> getAbwesenheiten() {
        return new ArrayList<>(abwesenheiten);
    }
    
    public void setAbwesenheiten(List<Abwesenheit> abwesenheiten) {
        this.abwesenheiten = abwesenheiten != null ? new ArrayList<>(abwesenheiten) : new ArrayList<>();
    }
    
    public EnumSet<DienstArt> getVerfuegbareDienstArten() {
        return verfuegbareDienstArten.clone();
    }
    
    public void setVerfuegbareDienstArten(EnumSet<DienstArt> verfuegbareDienstArten) {
        this.verfuegbareDienstArten = verfuegbareDienstArten != null ? 
            verfuegbareDienstArten.clone() : EnumSet.noneOf(DienstArt.class);
    }
    
    // Helper Methoden
    public void addAbwesenheit(Abwesenheit abwesenheit) {
        if (abwesenheit != null) {
            this.abwesenheiten.add(abwesenheit);
        }
    }
    
    public void removeAbwesenheit(Abwesenheit abwesenheit) {
        this.abwesenheiten.remove(abwesenheit);
    }
    
    public void addArbeitstag(Wochentag wochentag) {
        if (wochentag != null) {
            this.arbeitsTage.add(wochentag);
        }
    }
    
    public void removeArbeitstag(Wochentag wochentag) {
        this.arbeitsTage.remove(wochentag);
    }
    
    public void addDienstArt(DienstArt dienstArt) {
        if (dienstArt != null) {
            this.verfuegbareDienstArten.add(dienstArt);
        }
    }
    
    public void removeDienstArt(DienstArt dienstArt) {
        this.verfuegbareDienstArten.remove(dienstArt);
    }
    
    /**
     * Prüft ob die Person an einem bestimmten Datum verfügbar ist
     * @param datum Das zu prüfende Datum
     * @return true wenn verfügbar, false wenn abwesend
     */
    public boolean istVerfuegbar(LocalDate datum) {
        return abwesenheiten.stream()
            .noneMatch(abwesenheit -> abwesenheit.istAbwesend(datum));
    }
    
    /**
     * Prüft ob die Person an einem bestimmten Wochentag grundsätzlich arbeitet
     * @param wochentag Der zu prüfende Wochentag
     * @return true wenn die Person an diesem Wochentag arbeitet
     */
    public boolean arbeitetAnWochentag(Wochentag wochentag) {
        return this.arbeitsTage.contains(wochentag);
    }
    
    /**
     * Prüft ob die Person für eine bestimmte Dienstart verfügbar ist
     * @param dienstArt Die zu prüfende Dienstart
     * @return true wenn die Person diese Dienstart machen kann
     */
    public boolean kannDienstArt(DienstArt dienstArt) {
        return this.verfuegbareDienstArten.contains(dienstArt);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Person person = (Person) o;
        return Objects.equals(id, person.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "Person{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", anzahlDienste=" + anzahlDienste +
                ", arbeitsTage=" + arbeitsTage +
                ", verfuegbareDienstArten=" + verfuegbareDienstArten +
                ", abwesenheiten=" + abwesenheiten.size() +
                '}';
    }
}