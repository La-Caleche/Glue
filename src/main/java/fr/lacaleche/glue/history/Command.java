package fr.lacaleche.glue.history;

public interface Command {
    void undo();

    void redo();
}
