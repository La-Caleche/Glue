package fr.lacaleche.glue.mcsx.viewport;

/**
 * Marks a view whose area belongs to the game rather than the overlay while a vanilla screen is
 * open: the overlay hit-test skips it, so clicks and scrolls over it reach the screen rendering
 * inside the embedded viewport instead of being swallowed by the dock chrome.
 */
public interface GamePassthrough {
}
