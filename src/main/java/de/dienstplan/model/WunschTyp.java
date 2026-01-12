package de.dienstplan.model;

/**
 * Typ eines monatlichen Wunsches.
 */
public enum WunschTyp {

    /**
     * Urlaub - HARTER Constraint, wird garantiert berücksichtigt.
     * Person arbeitet an diesem Tag definitiv nicht.
     */
    URLAUB("U", "Urlaub", true),

    /**
     * Freiwunsch - WEICHER Constraint, wird wenn möglich berücksichtigt.
     * Person möchte an diesem Tag nicht arbeiten.
     */
    FREIWUNSCH("F", "Freiwunsch", false),

    /**
     * Dienstwunsch - WEICHER Constraint, wird wenn möglich berücksichtigt.
     * Person möchte an diesem Tag einen 24h-Dienst haben.
     */
    DIENSTWUNSCH("D", "Dienstwunsch (24h)", false);

    private final String kuerzel;
    private final String bezeichnung;
    private final boolean hartesConstraint;

    WunschTyp(String kuerzel, String bezeichnung, boolean hartesConstraint) {
        this.kuerzel = kuerzel;
        this.bezeichnung = bezeichnung;
        this.hartesConstraint = hartesConstraint;
    }

    public String getKuerzel() {
        return kuerzel;
    }

    public String getBezeichnung() {
        return bezeichnung;
    }

    /**
     * Gibt an, ob dieser Wunschtyp ein hartes Constraint ist (muss erfüllt werden)
     * oder ein weiches (sollte wenn möglich erfüllt werden).
     */
    public boolean isHartesConstraint() {
        return hartesConstraint;
    }

    /**
     * Findet den WunschTyp anhand des Kürzels (U, F, D).
     * Groß-/Kleinschreibung wird ignoriert.
     *
     * @param kuerzel Das Kürzel (U, F, D)
     * @return Der entsprechende WunschTyp oder null wenn nicht gefunden
     */
    public static WunschTyp fromKuerzel(String kuerzel) {
        if (kuerzel == null || kuerzel.trim().isEmpty()) {
            return null;
        }

        String normalized = kuerzel.trim().toUpperCase();

        for (WunschTyp typ : values()) {
            if (typ.kuerzel.equals(normalized)) {
                return typ;
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return bezeichnung;
    }
}
