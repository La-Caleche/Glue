package fr.lacaleche.glue.history;

/**
 * An undoable/redoable action.
 * Implementations must be self-contained: {@link #undo()} must perfectly
 * reverse {@link #execute()}, and {@link #execute()} must be idempotent
 * when called after undo.
 */
public interface Command {

    String getLabel();

    void execute();

    void undo();
}
