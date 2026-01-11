package de.dienstplan.model;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Enum für Wochentage mit deutscher Beschriftung
 */
public enum Wochentag {
    MONTAG("Montag", "Mo", DayOfWeek.MONDAY),
    DIENSTAG("Dienstag", "Di", DayOfWeek.TUESDAY),
    MITTWOCH("Mittwoch", "Mi", DayOfWeek.WEDNESDAY),
    DONNERSTAG("Donnerstag", "Do", DayOfWeek.THURSDAY),
    FREITAG("Freitag", "Fr", DayOfWeek.FRIDAY),
    SAMSTAG("Samstag", "Sa", DayOfWeek.SATURDAY),
    SONNTAG("Sonntag", "So", DayOfWeek.SUNDAY);
    
    private final String vollName;
    private final String kurzName;
    private final DayOfWeek dayOfWeek;
    
    Wochentag(String vollName, String kurzName, DayOfWeek dayOfWeek) {
        this.vollName = vollName;
        this.kurzName = kurzName;
        this.dayOfWeek = dayOfWeek;
    }
    
    public String getVollName() {
        return vollName;
    }
    
    public String getKurzName() {
        return kurzName;
    }
    
    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }
    
    /**
     * Konvertiert ein LocalDate zu einem Wochentag
     * @param datum Das Datum
     * @return Der entsprechende Wochentag
     */
    public static Wochentag fromLocalDate(LocalDate datum) {
        DayOfWeek dayOfWeek = datum.getDayOfWeek();
        for (Wochentag wochentag : values()) {
            if (wochentag.dayOfWeek == dayOfWeek) {
                return wochentag;
            }
        }
        throw new IllegalArgumentException("Unbekannter Wochentag: " + dayOfWeek);
    }
    
    /**
     * Konvertiert einen DayOfWeek zu einem Wochentag
     * @param dayOfWeek Der DayOfWeek
     * @return Der entsprechende Wochentag
     */
    public static Wochentag fromDayOfWeek(DayOfWeek dayOfWeek) {
        for (Wochentag wochentag : values()) {
            if (wochentag.dayOfWeek == dayOfWeek) {
                return wochentag;
            }
        }
        throw new IllegalArgumentException("Unbekannter Wochentag: " + dayOfWeek);
    }
    
    @Override
    public String toString() {
        return vollName;
    }
}
