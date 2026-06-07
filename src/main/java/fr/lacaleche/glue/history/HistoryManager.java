package fr.lacaleche.glue.history;

import java.util.ArrayDeque;
import java.util.Deque;

public class HistoryManager {
    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();
    private static final int MAX_HISTORY = 50;

    private Runnable onChange;

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public void push(Command command) {
        undoStack.push(command);
        redoStack.clear();
        if (undoStack.size() > MAX_HISTORY) {
            undoStack.removeLast();
        }
        notifyChange();
    }

    public void undo() {
        if (!undoStack.isEmpty()) {
            Command command = undoStack.pop();
            command.undo();
            redoStack.push(command);
            notifyChange();
        }
    }

    public void redo() {
        if (!redoStack.isEmpty()) {
            Command command = redoStack.pop();
            command.redo();
            undoStack.push(command);
            notifyChange();
        }
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    private void notifyChange() {
        if (onChange != null) onChange.run();
    }
}
