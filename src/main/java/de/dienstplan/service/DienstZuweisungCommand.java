package de.dienstplan.service;

import de.dienstplan.model.Dienst;
import de.dienstplan.model.Person;

/**
 * Command für das Zuweisen/Entfernen einer Person zu/von einem Dienst.
 * Ermöglicht Undo/Redo von Zuweisungsänderungen.
 */
public class DienstZuweisungCommand implements Command {

    private final Dienst dienst;
    private final Person neuePerson;
    private final Long vorherigePersonId;
    private final String vorherigerPersonName;

    /**
     * Erstellt einen neuen Zuweisungs-Command.
     *
     * @param dienst Der zu ändernde Dienst
     * @param neuePerson Die neue Person (null zum Entfernen der Zuweisung)
     */
    public DienstZuweisungCommand(Dienst dienst, Person neuePerson) {
        this.dienst = dienst;
        this.neuePerson = neuePerson;
        // Vorherigen Zustand speichern
        this.vorherigePersonId = dienst.getPersonId();
        this.vorherigerPersonName = dienst.getPersonName();
    }

    @Override
    public void execute() {
        if (neuePerson != null) {
            dienst.zuweisen(neuePerson);
        } else {
            dienst.zuweisingEntfernen();
        }
    }

    @Override
    public void undo() {
        if (vorherigePersonId != null) {
            dienst.setPersonId(vorherigePersonId);
            dienst.setPersonName(vorherigerPersonName);
        } else {
            dienst.zuweisingEntfernen();
        }
    }

    @Override
    public String getDescription() {
        String datumStr = dienst.getDatum() != null ? dienst.getDatum().toString() : "?";
        String artStr = dienst.getArt() != null ? dienst.getArt().getKurzName() : "?";

        if (neuePerson != null) {
            if (vorherigePersonId != null) {
                return String.format("Zuweisung geändert: %s %s (%s → %s)",
                    datumStr, artStr, vorherigerPersonName, neuePerson.getName());
            } else {
                return String.format("Zugewiesen: %s %s → %s",
                    datumStr, artStr, neuePerson.getName());
            }
        } else {
            return String.format("Zuweisung entfernt: %s %s (war: %s)",
                datumStr, artStr, vorherigerPersonName);
        }
    }
}
