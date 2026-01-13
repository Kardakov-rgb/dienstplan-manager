package de.dienstplan.model;

/**
 * Enum für verschiedene Dienstarten
 */
public enum DienstArt {
    DIENST_24H("24h Dienst", "24h", "Ganztägiger 24-Stunden-Dienst"),
    VISTEN("Visten", "V", "Visite und Patientenbetreuung"),
    DAVINCI("DaVinci", "daVinci", "DaVinci-Dienst (nur freitags)");
    
    private final String vollName;
    private final String kurzName;
    private final String beschreibung;
    
    DienstArt(String vollName, String kurzName, String beschreibung) {
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
     * Sucht eine DienstArt anhand des Vollnamens
     * @param vollName Der vollständige Name
     * @return Die entsprechende DienstArt oder null wenn nicht gefunden
     */
    public static DienstArt findByVollName(String vollName) {
        for (DienstArt dienstArt : values()) {
            if (dienstArt.vollName.equalsIgnoreCase(vollName)) {
                return dienstArt;
            }
        }
        return null;
    }
    
    /**
     * Sucht eine DienstArt anhand des Kurznamens
     * @param kurzName Der Kurzname
     * @return Die entsprechende DienstArt oder null wenn nicht gefunden
     */
    public static DienstArt findByKurzName(String kurzName) {
        for (DienstArt dienstArt : values()) {
            if (dienstArt.kurzName.equalsIgnoreCase(kurzName)) {
                return dienstArt;
            }
        }
        return null;
    }
    
    @Override
    public String toString() {
        return vollName;
    }
}
