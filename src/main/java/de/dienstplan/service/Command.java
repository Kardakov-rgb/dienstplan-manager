package de.dienstplan.service;

/**
 * Interface für das Command Pattern (Undo/Redo Funktionalität).
 * Jeder Command kapselt eine Aktion die rückgängig gemacht werden kann.
 */
public interface Command {

    /**
     * Führt den Command aus.
     */
    void execute();

    /**
     * Macht den Command rückgängig.
     */
    void undo();

    /**
     * Gibt eine Beschreibung des Commands zurück (für UI-Anzeige).
     *
     * @return Beschreibung des Commands
     */
    String getDescription();
}
