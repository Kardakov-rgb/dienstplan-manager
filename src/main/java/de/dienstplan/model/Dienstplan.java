package de.dienstplan.model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Repräsentiert einen kompletten Dienstplan für einen bestimmten Zeitraum
 */
public class Dienstplan {
    
    private Long id;
    private String name;
    private YearMonth monat;
    private LocalDate erstelltAm;
    private LocalDate letztesUpdate;
    private DienstplanStatus status;
    private List<Dienst> dienste;
    private String bemerkung;
    
    // Konstruktoren
    public Dienstplan() {
        this.dienste = new ArrayList<>();
        this.status = DienstplanStatus.ENTWURF;
        this.erstelltAm = LocalDate.now();
        this.letztesUpdate = LocalDate.now();
    }
    
    public Dienstplan(YearMonth monat) {
        this();
        this.monat = monat;
        this.name = "Dienstplan " + monat.toString();
    }
    
    public Dienstplan(String name, YearMonth monat) {
        this();
        this.name = name;
        this.monat = monat;
    }
    
    public Dienstplan(Long id, String name, YearMonth monat, LocalDate erstelltAm, 
                     LocalDate letztesUpdate, DienstplanStatus status, String bemerkung) {
        this();
        this.id = id;
        this.name = name;
        this.monat = monat;
        this.erstelltAm = erstelltAm;
        this.letztesUpdate = letztesUpdate;
        this.status = status != null ? status : DienstplanStatus.ENTWURF;
        this.bemerkung = bemerkung;
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
    
    public YearMonth getMonat() {
        return monat;
    }
    
    public void setMonat(YearMonth monat) {
        this.monat = monat;
    }
    
    public LocalDate getErstelltAm() {
        return erstelltAm;
    }
    
    public void setErstelltAm(LocalDate erstelltAm) {
        this.erstelltAm = erstelltAm;
    }
    
    public LocalDate getLetztesUpdate() {
        return letztesUpdate;
    }
    
    public void setLetztesUpdate(LocalDate letztesUpdate) {
        this.letztesUpdate = letztesUpdate;
    }
    
    public DienstplanStatus getStatus() {
        return status;
    }
    
    public void setStatus(DienstplanStatus status) {
        this.status = status != null ? status : DienstplanStatus.ENTWURF;
        this.letztesUpdate = LocalDate.now();
    }
    
    public List<Dienst> getDienste() {
        return new ArrayList<>(dienste);
    }
    
    public void setDienste(List<Dienst> dienste) {
        this.dienste = dienste != null ? new ArrayList<>(dienste) : new ArrayList<>();
        this.letztesUpdate = LocalDate.now();
    }
    
    public String getBemerkung() {
        return bemerkung;
    }
    
    public void setBemerkung(String bemerkung) {
        this.bemerkung = bemerkung;
    }
    
    // Dienst-Management Methoden
    
    public void addDienst(Dienst dienst) {
        if (dienst != null) {
            this.dienste.add(dienst);
            this.letztesUpdate = LocalDate.now();
        }
    }
    
    public void removeDienst(Dienst dienst) {
        if (this.dienste.remove(dienst)) {
            this.letztesUpdate = LocalDate.now();
        }
    }
    
    public void addAllDienste(Collection<Dienst> dienste) {
        if (dienste != null && !dienste.isEmpty()) {
            this.dienste.addAll(dienste);
            this.letztesUpdate = LocalDate.now();
        }
    }
    
    // Such- und Filter-Methoden
    
    /**
     * Findet alle Dienste für ein bestimmtes Datum
     */
    public List<Dienst> getDiensteAmDatum(LocalDate datum) {
        return dienste.stream()
                .filter(dienst -> Objects.equals(dienst.getDatum(), datum))
                .collect(Collectors.toList());
    }
    
    /**
     * Findet alle Dienste einer bestimmten Person
     */
    public List<Dienst> getDiensteVonPerson(Long personId) {
        return dienste.stream()
                .filter(dienst -> Objects.equals(dienst.getPersonId(), personId))
                .collect(Collectors.toList());
    }
    
    /**
     * Findet alle Dienste einer bestimmten Art
     */
    public List<Dienst> getDiensteVonArt(DienstArt art) {
        return dienste.stream()
                .filter(dienst -> dienst.getArt() == art)
                .collect(Collectors.toList());
    }
    
    /**
     * Findet alle offenen (nicht zugewiesenen) Dienste
     */
    public List<Dienst> getOffeneDienste() {
        return dienste.stream()
                .filter(Dienst::istOffen)
                .collect(Collectors.toList());
    }
    
    /**
     * Findet alle Dienste mit einem bestimmten Status
     */
    public List<Dienst> getDiensteNachStatus(DienstStatus status) {
        return dienste.stream()
                .filter(dienst -> dienst.getStatus() == status)
                .collect(Collectors.toList());
    }
    
    // Statistik-Methoden
    
    /**
     * Zählt die Anzahl der Dienste pro Person
     */
    public Map<Long, Integer> getDiensteProPerson() {
        return dienste.stream()
                .filter(Dienst::istZugewiesen)
                .collect(Collectors.groupingBy(
                    Dienst::getPersonId,
                    Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                ));
    }
    
    /**
     * Zählt die Anzahl der Dienste pro Art
     */
    public Map<DienstArt, Integer> getDiensteProArt() {
        return dienste.stream()
                .collect(Collectors.groupingBy(
                    Dienst::getArt,
                    Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                ));
    }
    
    /**
     * Berechnet die Anzahl der offenen Dienste
     */
    public int getAnzahlOffeneDienste() {
        return (int) dienste.stream().filter(Dienst::istOffen).count();
    }
    
    /**
     * Berechnet den Prozentsatz der zugewiesenen Dienste
     */
    public double getZuweisungsgrad() {
        if (dienste.isEmpty()) {
            return 0.0;
        }
        long zugewiesene = dienste.stream().filter(Dienst::istZugewiesen).count();
        return (double) zugewiesene / dienste.size() * 100.0;
    }
    
    // Validierungs-Methoden
    
    /**
     * Prüft ob der Dienstplan vollständig zugewiesen ist
     */
    public boolean istVollstaendigZugewiesen() {
        return dienste.stream().allMatch(Dienst::istZugewiesen);
    }
    
    /**
     * Prüft ob der Dienstplan Konflikte enthält
     */
    public boolean hatKonflikte() {
        // Prüfe auf doppelte Zuweisung einer Person am gleichen Tag
        Set<String> personDatumKombinationen = new HashSet<>();
        for (Dienst dienst : dienste) {
            if (dienst.istZugewiesen()) {
                String kombination = dienst.getPersonId() + "_" + dienst.getDatum();
                if (!personDatumKombinationen.add(kombination)) {
                    return true; // Doppelte Zuweisung gefunden
                }
            }
        }
        return false;
    }
    
    /**
     * Findet alle Konflikte im Dienstplan
     */
    public List<String> getKonflikte() {
        List<String> konflikte = new ArrayList<>();
        
        // Gruppiere Dienste nach Person und Datum
        Map<String, List<Dienst>> personDatumDienste = dienste.stream()
                .filter(Dienst::istZugewiesen)
                .collect(Collectors.groupingBy(dienst -> 
                    dienst.getPersonId() + "_" + dienst.getDatum()));
        
        // Finde Mehrfachzuweisungen
        for (Map.Entry<String, List<Dienst>> entry : personDatumDienste.entrySet()) {
            if (entry.getValue().size() > 1) {
                String[] parts = entry.getKey().split("_");
                konflikte.add("Person ID " + parts[0] + " hat mehrere Dienste am " + parts[1]);
            }
        }
        
        return konflikte;
    }
    
    /**
     * Aktualisiert das letzte Update-Datum
     */
    public void touch() {
        this.letztesUpdate = LocalDate.now();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Dienstplan that = (Dienstplan) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "Dienstplan{" +
                "name='" + name + '\'' +
                ", monat=" + monat +
                ", status=" + status +
                ", dienste=" + dienste.size() +
                ", zuweisungsgrad=" + String.format("%.1f", getZuweisungsgrad()) + "%" +
                '}';
    }
}