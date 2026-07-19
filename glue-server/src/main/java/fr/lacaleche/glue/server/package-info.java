/**
 * Server-side infrastructure for Glue: networking senders and world-save persistence that run on the
 * logical server &mdash; the integrated server in singleplayer, the dedicated server in multiplayer.
 *
 * <p>This module carries no client or rendering code and declares no {@code environment}, so it loads
 * in both environments and its server lifecycle hooks fire wherever a server actually runs.</p>
 */
package fr.lacaleche.glue.server;
