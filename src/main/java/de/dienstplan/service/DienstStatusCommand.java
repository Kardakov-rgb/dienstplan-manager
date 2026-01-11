package de.dienstplan.service;

import de.dienstplan.model.Dienst;
import de.dienstplan.model.DienstStatus;

/**
 * Command für das Ändern des Status eines Dienstes.
 * Ermöglicht Undo/Redo von Status-Änderungen.
 */
public class DienstStatusCommand implements Command {

    private final Dienst dienst;
    private final DienstStatus neuerStatus;
    private final DienstStatus vorherigerStatus;

    /**
     * Erstellt einen neuen Status-Command.
     *
     * @param dienst Der zu ändernde Dienst
     * @param neuerStatus Der neue Status
     */
    public DienstStatusCommand(Dienst dienst, DienstStatus neuerStatus) {
        this.dienst = dienst;
        this.neuerStatus = neuerStatus;
        // Vorherigen Zustand speichern
        this.vorherigerStatus = dienst.getStatus();
    }

    @Override
    public void execute() {
        dienst.setStatus(neuerStatus);
    }

    @Override
    public void undo() {
        dienst.setStatus(vorherigerStatus);
    }

    @Override
    public String getDescription() {
        String datumStr = dienst.getDatum() != null ? dienst.getDatum().toString() : "?";
        String artStr = dienst.getArt() != null ? dienst.getArt().getKurzName() : "?";

        return String.format("Status geändert: %s %s (%s → %s)",
            datumStr, artStr,
            vorherigerStatus != null ? vorherigerStatus.name() : "?",
            neuerStatus != null ? neuerStatus.name() : "?");
    }
}
