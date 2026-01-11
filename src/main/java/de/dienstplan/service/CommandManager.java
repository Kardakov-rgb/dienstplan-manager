package de.dienstplan.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Verwaltet die Undo/Redo-Stacks für Commands.
 * Implementiert das Command Pattern für rückgängig machbare Aktionen.
 */
public class CommandManager {

    private static final Logger logger = LoggerFactory.getLogger(CommandManager.class);

    private static final int DEFAULT_MAX_HISTORY = 50;

    private final Deque<Command> undoStack;
    private final Deque<Command> redoStack;
    private final int maxHistory;

    // Callbacks für UI-Updates
    private Consumer<Boolean> onUndoAvailableChanged;
    private Consumer<Boolean> onRedoAvailableChanged;

    /**
     * Konstruktor mit Standard-Historiengröße.
     */
    public CommandManager() {
        this(DEFAULT_MAX_HISTORY);
    }

    /**
     * Konstruktor mit angegebener Historiengröße.
     *
     * @param maxHistory Maximale Anzahl der gespeicherten Commands
     */
    public CommandManager(int maxHistory) {
        this.maxHistory = maxHistory;
        this.undoStack = new ArrayDeque<>();
        this.redoStack = new ArrayDeque<>();
    }

    /**
     * Führt einen Command aus und fügt ihn zum Undo-Stack hinzu.
     *
     * @param command Der auszuführende Command
     */
    public void executeCommand(Command command) {
        if (command == null) {
            return;
        }

        try {
            command.execute();

            // Zur Undo-Historie hinzufügen
            undoStack.push(command);

            // Redo-Stack leeren (neue Aktion macht alte Redos ungültig)
            redoStack.clear();

            // Historie begrenzen
            while (undoStack.size() > maxHistory) {
                undoStack.removeLast();
            }

            logger.debug("Command ausgeführt: {}", command.getDescription());
            notifyUndoRedoChanged();

        } catch (Exception e) {
            logger.error("Fehler beim Ausführen des Commands: {}", command.getDescription(), e);
            throw e;
        }
    }

    /**
     * Macht den letzten Command rückgängig.
     *
     * @return true wenn Undo erfolgreich, false wenn kein Undo verfügbar
     */
    public boolean undo() {
        if (undoStack.isEmpty()) {
            logger.debug("Kein Undo verfügbar");
            return false;
        }

        Command command = undoStack.pop();

        try {
            command.undo();
            redoStack.push(command);

            logger.debug("Undo ausgeführt: {}", command.getDescription());
            notifyUndoRedoChanged();
            return true;

        } catch (Exception e) {
            // Bei Fehler den Command zurück auf den Undo-Stack
            undoStack.push(command);
            logger.error("Fehler beim Undo: {}", command.getDescription(), e);
            return false;
        }
    }

    /**
     * Stellt den letzten rückgängig gemachten Command wieder her.
     *
     * @return true wenn Redo erfolgreich, false wenn kein Redo verfügbar
     */
    public boolean redo() {
        if (redoStack.isEmpty()) {
            logger.debug("Kein Redo verfügbar");
            return false;
        }

        Command command = redoStack.pop();

        try {
            command.execute();
            undoStack.push(command);

            logger.debug("Redo ausgeführt: {}", command.getDescription());
            notifyUndoRedoChanged();
            return true;

        } catch (Exception e) {
            // Bei Fehler den Command zurück auf den Redo-Stack
            redoStack.push(command);
            logger.error("Fehler beim Redo: {}", command.getDescription(), e);
            return false;
        }
    }

    /**
     * Prüft ob Undo verfügbar ist.
     *
     * @return true wenn mindestens ein Command rückgängig gemacht werden kann
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * Prüft ob Redo verfügbar ist.
     *
     * @return true wenn mindestens ein Command wiederhergestellt werden kann
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Gibt die Beschreibung des nächsten Undo-Commands zurück.
     *
     * @return Beschreibung oder null wenn kein Undo verfügbar
     */
    public String getUndoDescription() {
        return undoStack.isEmpty() ? null : undoStack.peek().getDescription();
    }

    /**
     * Gibt die Beschreibung des nächsten Redo-Commands zurück.
     *
     * @return Beschreibung oder null wenn kein Redo verfügbar
     */
    public String getRedoDescription() {
        return redoStack.isEmpty() ? null : redoStack.peek().getDescription();
    }

    /**
     * Gibt die Anzahl der verfügbaren Undo-Schritte zurück.
     *
     * @return Anzahl der Undo-Schritte
     */
    public int getUndoCount() {
        return undoStack.size();
    }

    /**
     * Gibt die Anzahl der verfügbaren Redo-Schritte zurück.
     *
     * @return Anzahl der Redo-Schritte
     */
    public int getRedoCount() {
        return redoStack.size();
    }

    /**
     * Leert beide Stacks (z.B. bei neuem Dokument).
     */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
        logger.debug("Command-Historie geleert");
        notifyUndoRedoChanged();
    }

    /**
     * Setzt den Callback für Undo-Verfügbarkeitsänderungen.
     *
     * @param callback Consumer der mit true/false aufgerufen wird
     */
    public void setOnUndoAvailableChanged(Consumer<Boolean> callback) {
        this.onUndoAvailableChanged = callback;
    }

    /**
     * Setzt den Callback für Redo-Verfügbarkeitsänderungen.
     *
     * @param callback Consumer der mit true/false aufgerufen wird
     */
    public void setOnRedoAvailableChanged(Consumer<Boolean> callback) {
        this.onRedoAvailableChanged = callback;
    }

    /**
     * Benachrichtigt die Callbacks über Änderungen.
     */
    private void notifyUndoRedoChanged() {
        if (onUndoAvailableChanged != null) {
            onUndoAvailableChanged.accept(canUndo());
        }
        if (onRedoAvailableChanged != null) {
            onRedoAvailableChanged.accept(canRedo());
        }
    }
}
