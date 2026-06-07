package fr.lacaleche.glue.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Linear undo/redo stack.
 * When a new command is pushed after an undo, the redo future is discarded.
 */
public class HistoryManager {

    private static final int DEFAULT_MAX_HISTORY = 128;

    private final int maxHistory;
    private final List<Command> commands = new ArrayList<>();
    /**
     * Points to the next slot — commands[0..cursor-1] are undoable.
     */
    private int cursor = 0;

    public HistoryManager() {
        this(DEFAULT_MAX_HISTORY);
    }

    public HistoryManager(int maxHistory) {
        this.maxHistory = maxHistory;
    }

    /**
     * Executes the command, then pushes it onto the stack.
     * Any redo future beyond the cursor is discarded.
     */
    public void execute(Command command) {
        command.execute();

        while (commands.size() > cursor) {
            commands.removeLast();
        }

        commands.add(command);
        cursor++;

        if (commands.size() > maxHistory) {
            commands.removeFirst();
            cursor--;
        }
    }

    public boolean canUndo() {
        return cursor > 0;
    }

    public boolean canRedo() {
        return cursor < commands.size();
    }

    public void undo() {
        if (!canUndo()) return;
        cursor--;
        commands.get(cursor).undo();
    }

    public void redo() {
        if (!canRedo()) return;
        commands.get(cursor).execute();
        cursor++;
    }

    public int getCursor() {
        return cursor;
    }

    public List<Command> getCommands() {
        return Collections.unmodifiableList(commands);
    }

    public void clear() {
        commands.clear();
        cursor = 0;
    }
}
