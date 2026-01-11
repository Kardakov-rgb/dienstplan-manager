package de.dienstplan.service;

import de.dienstplan.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests für den CommandManager (Undo/Redo Funktionalität)
 */
@DisplayName("CommandManager Tests")
class CommandManagerTest {

    private CommandManager commandManager;

    @BeforeEach
    void setUp() {
        commandManager = new CommandManager();
    }

    @Nested
    @DisplayName("Grundlegende Operationen")
    class GrundlegendeOperationen {

        @Test
        @DisplayName("Initialer Zustand: Kein Undo/Redo verfügbar")
        void initialerZustand() {
            assertFalse(commandManager.canUndo());
            assertFalse(commandManager.canRedo());
            assertEquals(0, commandManager.getUndoCount());
            assertEquals(0, commandManager.getRedoCount());
        }

        @Test
        @DisplayName("Führt Command aus")
        void fuehrtCommandAus() {
            AtomicBoolean executed = new AtomicBoolean(false);
            Command command = new TestCommand(() -> executed.set(true), () -> executed.set(false));

            commandManager.executeCommand(command);

            assertTrue(executed.get());
            assertTrue(commandManager.canUndo());
            assertEquals(1, commandManager.getUndoCount());
        }

        @Test
        @DisplayName("Undo macht Command rückgängig")
        void undoMachtRueckgaengig() {
            AtomicBoolean executed = new AtomicBoolean(false);
            Command command = new TestCommand(() -> executed.set(true), () -> executed.set(false));

            commandManager.executeCommand(command);
            assertTrue(executed.get());

            boolean undoSuccess = commandManager.undo();
            assertTrue(undoSuccess);
            assertFalse(executed.get());
            assertFalse(commandManager.canUndo());
            assertTrue(commandManager.canRedo());
        }

        @Test
        @DisplayName("Redo stellt Command wieder her")
        void redoStelltWiederHer() {
            AtomicBoolean executed = new AtomicBoolean(false);
            Command command = new TestCommand(() -> executed.set(true), () -> executed.set(false));

            commandManager.executeCommand(command);
            commandManager.undo();
            assertFalse(executed.get());

            boolean redoSuccess = commandManager.redo();
            assertTrue(redoSuccess);
            assertTrue(executed.get());
            assertTrue(commandManager.canUndo());
            assertFalse(commandManager.canRedo());
        }
    }

    @Nested
    @DisplayName("Mehrere Commands")
    class MehrereCommands {

        @Test
        @DisplayName("Mehrfaches Undo in korrekter Reihenfolge")
        void mehrfachesUndo() {
            int[] value = {0};
            Command cmd1 = new TestCommand(() -> value[0] = 1, () -> value[0] = 0);
            Command cmd2 = new TestCommand(() -> value[0] = 2, () -> value[0] = 1);
            Command cmd3 = new TestCommand(() -> value[0] = 3, () -> value[0] = 2);

            commandManager.executeCommand(cmd1);
            commandManager.executeCommand(cmd2);
            commandManager.executeCommand(cmd3);

            assertEquals(3, value[0]);
            assertEquals(3, commandManager.getUndoCount());

            commandManager.undo();
            assertEquals(2, value[0]);

            commandManager.undo();
            assertEquals(1, value[0]);

            commandManager.undo();
            assertEquals(0, value[0]);
            assertEquals(0, commandManager.getUndoCount());
            assertEquals(3, commandManager.getRedoCount());
        }

        @Test
        @DisplayName("Neuer Command löscht Redo-Stack")
        void neuerCommandLoeschtRedoStack() {
            int[] value = {0};
            Command cmd1 = new TestCommand(() -> value[0] = 1, () -> value[0] = 0);
            Command cmd2 = new TestCommand(() -> value[0] = 2, () -> value[0] = 1);

            commandManager.executeCommand(cmd1);
            commandManager.executeCommand(cmd2);
            commandManager.undo();

            assertTrue(commandManager.canRedo());

            // Neuer Command
            Command cmd3 = new TestCommand(() -> value[0] = 99, () -> value[0] = 1);
            commandManager.executeCommand(cmd3);

            assertFalse(commandManager.canRedo());
            assertEquals(99, value[0]);
        }
    }

    @Nested
    @DisplayName("History-Limit")
    class HistoryLimit {

        @Test
        @DisplayName("Begrenzt History auf maximale Größe")
        void begrenztHistory() {
            CommandManager limitedManager = new CommandManager(3);

            int[] value = {0};
            for (int i = 1; i <= 5; i++) {
                final int newValue = i;
                final int oldValue = i - 1;
                Command cmd = new TestCommand(() -> value[0] = newValue, () -> value[0] = oldValue);
                limitedManager.executeCommand(cmd);
            }

            assertEquals(3, limitedManager.getUndoCount());
        }
    }

    @Nested
    @DisplayName("Callbacks")
    class Callbacks {

        @Test
        @DisplayName("Ruft Undo-Callback auf")
        void ruftUndoCallbackAuf() {
            AtomicBoolean callbackCalled = new AtomicBoolean(false);
            commandManager.setOnUndoAvailableChanged(available -> callbackCalled.set(true));

            Command command = new TestCommand(() -> {}, () -> {});
            commandManager.executeCommand(command);

            assertTrue(callbackCalled.get());
        }

        @Test
        @DisplayName("Ruft Redo-Callback auf")
        void ruftRedoCallbackAuf() {
            AtomicBoolean callbackCalled = new AtomicBoolean(false);
            commandManager.setOnRedoAvailableChanged(available -> callbackCalled.set(true));

            Command command = new TestCommand(() -> {}, () -> {});
            commandManager.executeCommand(command);
            commandManager.undo();

            assertTrue(callbackCalled.get());
        }
    }

    @Nested
    @DisplayName("Clear")
    class Clear {

        @Test
        @DisplayName("Clear löscht beide Stacks")
        void clearLoeschtBeideStacks() {
            Command cmd1 = new TestCommand(() -> {}, () -> {});
            Command cmd2 = new TestCommand(() -> {}, () -> {});

            commandManager.executeCommand(cmd1);
            commandManager.executeCommand(cmd2);
            commandManager.undo();

            assertTrue(commandManager.canUndo());
            assertTrue(commandManager.canRedo());

            commandManager.clear();

            assertFalse(commandManager.canUndo());
            assertFalse(commandManager.canRedo());
        }
    }

    @Nested
    @DisplayName("DienstZuweisungCommand Integration")
    class DienstZuweisungIntegration {

        @Test
        @DisplayName("Undo einer Dienst-Zuweisung")
        void undoDienstZuweisung() {
            Dienst dienst = new Dienst(LocalDate.of(2024, 6, 15), DienstArt.DIENST_24H);
            Person person = new Person("Test Person");
            person.setId(1L);

            assertNull(dienst.getPersonId());

            Command command = new DienstZuweisungCommand(dienst, person);
            commandManager.executeCommand(command);

            assertEquals(1L, dienst.getPersonId());
            assertEquals("Test Person", dienst.getPersonName());

            commandManager.undo();

            assertNull(dienst.getPersonId());
            assertNull(dienst.getPersonName());
        }

        @Test
        @DisplayName("Undo einer Zuweisung-Entfernung")
        void undoZuweisungEntfernung() {
            Dienst dienst = new Dienst(LocalDate.of(2024, 6, 15), DienstArt.DIENST_24H);
            Person person = new Person("Test Person");
            person.setId(1L);
            dienst.zuweisen(person);

            // Entferne Zuweisung
            Command command = new DienstZuweisungCommand(dienst, null);
            commandManager.executeCommand(command);

            assertNull(dienst.getPersonId());

            commandManager.undo();

            assertEquals(1L, dienst.getPersonId());
            assertEquals("Test Person", dienst.getPersonName());
        }
    }

    @Nested
    @DisplayName("DienstStatusCommand Integration")
    class DienstStatusIntegration {

        @Test
        @DisplayName("Undo einer Status-Änderung")
        void undoStatusAenderung() {
            Dienst dienst = new Dienst(LocalDate.of(2024, 6, 15), DienstArt.DIENST_24H);
            assertEquals(DienstStatus.GEPLANT, dienst.getStatus());

            Command command = new DienstStatusCommand(dienst, DienstStatus.BESTAETIGT);
            commandManager.executeCommand(command);

            assertEquals(DienstStatus.BESTAETIGT, dienst.getStatus());

            commandManager.undo();

            assertEquals(DienstStatus.GEPLANT, dienst.getStatus());
        }
    }

    // Test-Helper Command
    private static class TestCommand implements Command {
        private final Runnable executeAction;
        private final Runnable undoAction;

        TestCommand(Runnable executeAction, Runnable undoAction) {
            this.executeAction = executeAction;
            this.undoAction = undoAction;
        }

        @Override
        public void execute() {
            executeAction.run();
        }

        @Override
        public void undo() {
            undoAction.run();
        }

        @Override
        public String getDescription() {
            return "Test Command";
        }
    }
}
