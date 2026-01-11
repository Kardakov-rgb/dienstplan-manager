package de.dienstplan.model;

/**
 * Enum für den Status eines Dienstplans
 */
public enum DienstplanStatus {
    ENTWURF("Entwurf", "E", "Dienstplan wird noch bearbeitet"),
    GEPRUEFT("Geprüft", "GP", "Dienstplan wurde geprüft und ist bereit zur Veröffentlichung"),
    VEROEFFENTLICHT("Veröffentlicht", "V", "Dienstplan wurde veröffentlicht und ist aktiv"),
    ARCHIVIERT("Archiviert", "A", "Dienstplan ist abgeschlossen und archiviert"),
    STORNIERT("Storniert", "S", "Dienstplan wurde storniert und ist ungültig");
    
    private final String vollName;
    private final String kurzName;
    private final String beschreibung;
    
    DienstplanStatus(String vollName, String kurzName, String beschreibung) {
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
     * Prüft ob der Dienstplan noch bearbeitet werden kann
     * @return true wenn der Plan bearbeitbar ist
     */
    public boolean istBearbeitbar() {
        return this == ENTWURF || this == GEPRUEFT;
    }
    
    /**
     * Prüft ob der Dienstplan aktiv ist (veröffentlicht)
     * @return true wenn der Plan aktiv ist
     */
    public boolean istAktiv() {
        return this == VEROEFFENTLICHT;
    }
    
    /**
     * Prüft ob der Dienstplan abgeschlossen ist
     * @return true wenn der Plan nicht mehr aktiv verwendet wird
     */
    public boolean istAbgeschlossen() {
        return this == ARCHIVIERT || this == STORNIERT;
    }
    
    /**
     * Prüft ob der Dienstplan gültig ist (nicht storniert)
     * @return true wenn der Plan gültig ist
     */
    public boolean istGueltig() {
        return this != STORNIERT;
    }
    
    /**
     * Gibt die möglichen Folgestatus zurück
     * @return Liste der möglichen nächsten Status
     */
    public DienstplanStatus[] getMoeglicheFolgestatus() {
        switch (this) {
            case ENTWURF:
                return new DienstplanStatus[]{GEPRUEFT, STORNIERT};
            case GEPRUEFT:
                return new DienstplanStatus[]{ENTWURF, VEROEFFENTLICHT, STORNIERT};
            case VEROEFFENTLICHT:
                return new DienstplanStatus[]{GEPRUEFT, ARCHIVIERT};
            case ARCHIVIERT:
                return new DienstplanStatus[]{}; // Keine Änderung möglich
            case STORNIERT:
                return new DienstplanStatus[]{ENTWURF}; // Nur Wiederherstellung möglich
            default:
                return new DienstplanStatus[]{};
        }
    }
    
    /**
     * Prüft ob ein Statuswechsel möglich ist
     * @param zielStatus Der gewünschte Zielstatus
     * @return true wenn der Wechsel erlaubt ist
     */
    public boolean kannWechselnZu(DienstplanStatus zielStatus) {
        DienstplanStatus[] erlaubte = getMoeglicheFolgestatus();
        for (DienstplanStatus status : erlaubte) {
            if (status == zielStatus) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Sucht einen DienstplanStatus anhand des Vollnamens
     * @param vollName Der vollständige Name
     * @return Der entsprechende DienstplanStatus oder null wenn nicht gefunden
     */
    public static DienstplanStatus findByVollName(String vollName) {
        for (DienstplanStatus status : values()) {
            if (status.vollName.equalsIgnoreCase(vollName)) {
                return status;
            }
        }
        return null;
    }
    
    /**
     * Sucht einen DienstplanStatus anhand des Kurznamens
     * @param kurzName Der Kurzname
     * @return Der entsprechende DienstplanStatus oder null wenn nicht gefunden
     */
    public static DienstplanStatus findByKurzName(String kurzName) {
        for (DienstplanStatus status : values()) {
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