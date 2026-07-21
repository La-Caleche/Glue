package fr.lacaleche.glue.mcsx.view;

import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The screen's keyboard shortcuts, declared as {@code <key combo="ctrl+k" onPress={handler}/>}.
 * Installed as the root view's key listener, so a combo fires wherever focus happens to be.
 *
 * <p>{@code Esc} is special: it first dismisses the topmost overlay, and only reaches a declared
 * binding when nothing is open. Without that, closing a dialog would also close the screen.
 */
public final class KeyBindings implements View.OnKeyListener {

    /** Named keys beyond the letters and digits, which are derived from the character. */
    private static final Map<String, Integer> NAMED_KEYS = Map.ofEntries(
            Map.entry("escape", KeyEvent.KEY_ESCAPE),
            Map.entry("esc", KeyEvent.KEY_ESCAPE),
            Map.entry("enter", KeyEvent.KEY_ENTER),
            Map.entry("space", KeyEvent.KEY_SPACE),
            Map.entry("tab", KeyEvent.KEY_TAB),
            Map.entry("delete", KeyEvent.KEY_DELETE),
            Map.entry("backspace", KeyEvent.KEY_BACKSPACE),
            Map.entry("up", KeyEvent.KEY_UP),
            Map.entry("down", KeyEvent.KEY_DOWN),
            Map.entry("left", KeyEvent.KEY_LEFT),
            Map.entry("right", KeyEvent.KEY_RIGHT));

    /** A parsed {@code ctrl+shift+k}. */
    private record Binding(int keyCode, boolean ctrl, boolean shift, boolean alt, Runnable action) {

        boolean sameCombo(int code, boolean withCtrl, boolean withShift, boolean withAlt) {
            return keyCode == code && ctrl == withCtrl && shift == withShift && alt == withAlt;
        }
    }

    private final List<Binding> bindings = new ArrayList<>();
    private final OverlayHost overlays;

    public KeyBindings(OverlayHost overlays) {
        this.overlays = overlays;
    }

    /**
     * Registers a combo and returns its unregistration handle — the caller ties it to the
     * declaring subtree's lifetime, so a shared workspace-wide KeyBindings does not accumulate
     * bindings from every document ever bound into it.
     *
     * <p>A combo may be claimed once. Two documents sharing one workspace-wide KeyBindings — two
     * dock panes, say — cannot both own {@code ctrl+f}: whichever bound last would silently shadow
     * the other, and panes bind lazily on first show, so the winner would depend on the order they
     * happened to become visible. The conflict is raised here instead, where it names a line.
     *
     * @throws IllegalArgumentException when {@code combo} names a key this mapping doesn't know,
     *                                  or when it is already registered
     */
    public Runnable register(String combo, Runnable action) {
        boolean ctrl = false;
        boolean shift = false;
        boolean alt = false;
        Integer keyCode = null;
        for (String part : combo.toLowerCase(Locale.ROOT).split("\\+")) {
            String token = part.trim();
            switch (token) {
                case "ctrl", "control", "cmd", "meta" -> ctrl = true;
                case "shift" -> shift = true;
                case "alt", "option" -> alt = true;
                default -> keyCode = keyCodeOf(token, combo);
            }
        }
        if (keyCode == null) {
            throw new IllegalArgumentException("no key in combo '" + combo + "'");
        }
        for (Binding existing : bindings) {
            if (existing.sameCombo(keyCode, ctrl, shift, alt)) {
                throw new IllegalArgumentException("combo '" + combo + "' is already bound");
            }
        }
        Binding binding = new Binding(keyCode, ctrl, shift, alt, action);
        bindings.add(binding);
        return () -> bindings.remove(binding);
    }

    private static int keyCodeOf(String token, String combo) {
        Integer named = NAMED_KEYS.get(token);
        if (named != null) {
            return named;
        }
        if (token.length() == 1) {
            char c = token.charAt(0);
            if (c >= 'a' && c <= 'z') {
                return KeyEvent.KEY_A + (c - 'a');
            }
            if (c >= '0' && c <= '9') {
                return KeyEvent.KEY_0 + (c - '0');
            }
        }
        throw new IllegalArgumentException("unknown key '" + token + "' in combo '" + combo + "'");
    }

    @Override
    public boolean onKey(View view, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }
        if (keyCode == KeyEvent.KEY_ESCAPE && overlays != null && overlays.dismissTop()) {
            return true;
        }
        for (Binding binding : bindings) {
            if (binding.sameCombo(keyCode, event.isCtrlPressed(),
                    event.isShiftPressed(), event.isAltPressed())) {
                binding.action().run();
                return true;
            }
        }
        return false;
    }
}
