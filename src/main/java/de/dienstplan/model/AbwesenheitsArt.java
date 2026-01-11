package de.dienstplan.model;

/**
 * Enum für verschiedene Arten von Abwesenheiten
 */
public enum AbwesenheitsArt {
    URLAUB("Urlaub", "U", "Geplanter Jahresurlaub"),
    KRANKHEIT("Krankheit", "K", "Krankheitsbedingte Abwesenheit"),
    FORTBILDUNG("Fortbildung", "F", "Fortbildung oder Schulung"),
    SONDERURLAUB("Sonderurlaub", "S", "Sonderurlaub (Hochzeit, Geburt, etc.)"),
    FEIERTAG("Feiertag", "Fei", "Gesetzlicher Feiertag"),
    DIENSTREISE("Dienstreise", "DR", "Dienstreise oder Außentermin"),
    PERSOENLICH("Persönlich", "P", "Persönliche Gründe");
    
    private final String vollName;
    private final String kurzName;
    private final String beschreibung;
    
    AbwesenheitsArt(String vollName, String kurzName, String beschreibung) {
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
     * Prüft ob diese Art von Abwesenheit planbar ist (im Gegensatz zu spontanen wie Krankheit)
     * @return true wenn die Abwesenheit planbar ist
     */
    public boolean istPlanbar() {
        return this != KRANKHEIT;
    }
    
    /**
     * Prüft ob diese Abwesenheit eine hohe Priorität hat (nicht verschiebbar)
     * @return true wenn hohe Priorität
     */
    public boolean hatHohePrioritaet() {
        return this == KRANKHEIT || this == FEIERTAG || this == SONDERURLAUB;
    }
    
    /**
     * Sucht eine AbwesenheitsArt anhand des Vollnamens
     * @param vollName Der vollständige Name
     * @return Die entsprechende AbwesenheitsArt oder null wenn nicht gefunden
     */
    public static AbwesenheitsArt findByVollName(String vollName) {
        for (AbwesenheitsArt art : values()) {
            if (art.vollName.equalsIgnoreCase(vollName)) {
                return art;
            }
        }
        return null;
    }
    
    /**
     * Sucht eine AbwesenheitsArt anhand des Kurznamens
     * @param kurzName Der Kurzname
     * @return Die entsprechende AbwesenheitsArt oder null wenn nicht gefunden
     */
    public static AbwesenheitsArt findByKurzName(String kurzName) {
        for (AbwesenheitsArt art : values()) {
            if (art.kurzName.equalsIgnoreCase(kurzName)) {
                return art;
            }
        }
        return null;
    }
    
    @Override
    public String toString() {
        return vollName;
    }
}
