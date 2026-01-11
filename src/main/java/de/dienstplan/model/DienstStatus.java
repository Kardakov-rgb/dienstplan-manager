package de.dienstplan.model;

/**
 * Enum für den Status eines Dienstes
 */
public enum DienstStatus {
    GEPLANT("Geplant", "G", "Dienst ist eingeplant aber noch nicht bestätigt"),
    BESTAETIGT("Bestätigt", "B", "Dienst wurde von der Person bestätigt"),
    ABGESAGT("Abgesagt", "A", "Dienst wurde abgesagt oder Person ist ausgefallen"),
    ERSETZT("Ersetzt", "E", "Dienst wurde durch eine andere Person ersetzt"),
    ABGESCHLOSSEN("Abgeschlossen", "X", "Dienst wurde erfolgreich durchgeführt");
    
    private final String vollName;
    private final String kurzName;
    private final String beschreibung;
    
    DienstStatus(String vollName, String kurzName, String beschreibung) {
        this.vollName = vollName;
        this.kurzName = kurzName;
        this.beschreibung = beschreibung;
    }
    
    public String getVollName() {
        return vollName;
    }
    
    public String getKurzName() {
        return kurzName;
    }
    
    public String getBeschreibung() {
        return beschreibung;
    }
    
    /**
     * Prüft ob der Status bedeutet, dass der Dienst noch aktiv geplant ist
     * @return true wenn der Dienst aktiv ist (geplant oder bestätigt)
     */
    public boolean istAktiv() {
        return this == GEPLANT || this == BESTAETIGT;
    }
    
    /**
     * Prüft ob der Status bedeutet, dass der Dienst abgeschlossen ist
     * @return true wenn der Dienst nicht mehr verändert werden kann
     */
    public boolean istAbgeschlossen() {
        return this == ABGESCHLOSSEN || this == ABGESAGT;
    }
    
    /**
     * Prüft ob der Status bedeutet, dass eine Ersatzperson benötigt wird
     * @return true wenn Ersatz benötigt wird
     */
    public boolean brauchtErsatz() {
        return this == ABGESAGT;
    }
    
    /**
     * Sucht einen DienstStatus anhand des Vollnamens
     * @param vollName Der vollständige Name
     * @return Der entsprechende DienstStatus oder null wenn nicht gefunden
     */
    public static DienstStatus findByVollName(String vollName) {
        for (DienstStatus status : values()) {
            if (status.vollName.equalsIgnoreCase(vollName)) {
                return status;
            }
        }
        return null;
    }
    
    /**
     * Sucht einen DienstStatus anhand des Kurznamens
     * @param kurzName Der Kurzname
     * @return Der entsprechende DienstStatus oder null wenn nicht gefunden
     */
    public static DienstStatus findByKurzName(String kurzName) {
        for (DienstStatus status : values()) {
            if (status.kurzName.equalsIgnoreCase(kurzName)) {
                return status;
            }
        }
        return null;
    }
    
    @Override
    public String toString() {
        return vollName;
    }
}