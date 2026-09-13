package fr.lacaleche.glue.mcsx.client.dock.internal.layout;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockAxis;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayout;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayoutException;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockNode;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockSplit;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockTabs;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.StrictJsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** JSON version 1 persistence for semantic layouts. Runtime object identity is never stored. */
@Environment(EnvType.CLIENT)
public final class DockLayoutCodec {

    private static final int VERSION = 1;
    private static final int DEFAULT_WIDTH = 360;
    private static final int DEFAULT_HEIGHT = 260;

    private DockLayoutCodec() {
    }

    public static String write(DockLayout layout) {
        Objects.requireNonNull(layout, "layout");

        JsonObject document = new JsonObject();
        document.addProperty("version", VERSION);
        document.add("tree", writeNode(layout.tree()));

        JsonArray windows = new JsonArray();
        for (DockWindow window : layout.windows()) windows.add(writeWindow(window));
        document.add("windows", windows);
        return document.toString();
    }

    public static DockLayout read(String json) {
        if (json == null) throw new DockLayoutException("Layout JSON cannot be null");

        try {
            JsonElement parsed = StrictJsonParser.parse(json);
            requireFiniteNumbers(parsed);
            JsonObject document = asObject(parsed, "document");
            readInt(document, "version", VERSION);
            DockNode tree = readNode(document.get("tree"));
            List<DockWindow> windows = readWindows(document.get("windows"));
            DockLayout layout = new DockLayout(tree, windows);
            return DockOperations.sanitize(layout, DockOperations.openSet(layout));
        } catch (DockLayoutException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DockLayoutException("Invalid dock layout", exception);
        }
    }

    private static JsonObject writeWindow(DockWindow window) {
        JsonObject result = new JsonObject();
        result.addProperty("x", window.x());
        result.addProperty("y", window.y());
        result.addProperty("width", window.width());
        result.addProperty("height", window.height());
        result.addProperty("stackingOrder", window.stackingOrder());
        result.add("node", writeNode(window.node()));
        return result;
    }

    private static JsonElement writeNode(DockNode node) {
        if (node == null) return JsonNull.INSTANCE;

        JsonObject result = new JsonObject();
        if (node instanceof DockTabs tabs) {
            result.addProperty("type", "tabs");
            JsonArray panes = new JsonArray();
            for (String pane : tabs.tabs()) panes.add(pane);
            result.add("tabs", panes);
            result.addProperty("active", tabs.active());
            return result;
        }

        DockSplit split = (DockSplit) node;
        result.addProperty("type", "split");
        result.addProperty("axis", split.axis() == DockAxis.HORIZONTAL ? "horizontal" : "vertical");
        JsonArray shares = new JsonArray();
        for (double share : split.shares()) shares.add(share);
        result.add("shares", shares);
        JsonArray children = new JsonArray();
        for (DockNode child : split.children()) children.add(writeNode(child));
        result.add("children", children);
        return result;
    }

    private static List<DockWindow> readWindows(JsonElement rawWindows) {
        List<DockWindow> windows = new ArrayList<>();
        if (rawWindows == null || rawWindows.isJsonNull()) return windows;

        for (JsonElement rawWindow : asArray(rawWindows, "windows")) {
            JsonObject window = asObject(rawWindow, "window");
            DockNode node = readNode(window.get("node"));
            if (node == null) continue;

            int x = readInt(window, "x", 0);
            int y = readInt(window, "y", 0);
            int width = positiveOrDefault(readInt(window, "width", DEFAULT_WIDTH), DEFAULT_WIDTH);
            int height = positiveOrDefault(readInt(window, "height", DEFAULT_HEIGHT), DEFAULT_HEIGHT);
            int order = readInt(window, "stackingOrder", windows.size() + 1);
            windows.add(new DockWindow(node, x, y, width, height, order));
        }
        return windows;
    }

    private static DockNode readNode(JsonElement rawNode) {
        if (rawNode == null || rawNode.isJsonNull()) return null;

        JsonObject node = asObject(rawNode, "node");
        String type = readString(node.get("type"));
        if ("tabs".equals(type)) return readTabs(node);
        if ("split".equals(type)) return readSplit(node);
        throw new DockLayoutException("Node has unknown type: " + type);
    }

    private static DockNode readTabs(JsonObject node) {
        List<String> tabs = new ArrayList<>();
        for (JsonElement rawTab : asArray(node.get("tabs"), "tabs")) {
            String tab = readString(rawTab);
            if (tab != null && !tab.isBlank() && !tabs.contains(tab)) tabs.add(tab);
        }
        if (tabs.isEmpty()) return null;

        String active = readString(node.get("active"));
        return new DockTabs(tabs, tabs.contains(active) ? active : tabs.getFirst());
    }

    private static DockNode readSplit(JsonObject node) {
        JsonArray rawChildren = asArray(node.get("children"), "children");
        List<Double> rawShares = readShares(node.get("shares"), rawChildren.size());
        List<DockNode> children = new ArrayList<>();
        List<Double> shares = new ArrayList<>();
        for (int index = 0; index < rawChildren.size(); index++) {
            DockNode child = readNode(rawChildren.get(index));
            if (child != null) {
                children.add(child);
                shares.add(rawShares.get(index));
            }
        }
        if (children.isEmpty()) return null;
        if (children.size() == 1) return children.getFirst();

        DockAxis axis = "vertical".equals(readString(node.get("axis")))
                ? DockAxis.VERTICAL
                : DockAxis.HORIZONTAL;
        return new DockSplit(axis, children, normalizeShares(shares));
    }

    private static List<Double> readShares(JsonElement rawShares, int count) {
        if (rawShares != null && rawShares.isJsonArray()) {
            JsonArray values = rawShares.getAsJsonArray();
            if (values.size() == count) {
                List<Double> shares = new ArrayList<>(count);
                for (JsonElement value : values) {
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                        return evenShares(count);
                    }
                    double share = value.getAsDouble();
                    if (!Double.isFinite(share)) throw new DockLayoutException("Split share is not finite");
                    if (share < 0.0) return evenShares(count);
                    shares.add(share);
                }
                return normalizeShares(shares);
            }
        }
        return evenShares(count);
    }

    private static List<Double> normalizeShares(List<Double> shares) {
        double total = 0.0;
        for (double share : shares) {
            total += share;
            if (!Double.isFinite(total)) throw new DockLayoutException("Split share total overflowed");
        }
        if (total <= 0.0) return evenShares(shares.size());

        double divisor = total;
        return shares.stream().map(share -> share / divisor).toList();
    }

    private static List<Double> evenShares(int count) {
        if (count == 0) return List.of();

        double share = 1.0 / count;
        List<Double> shares = new ArrayList<>(count);
        for (int index = 0; index < count; index++) shares.add(share);
        return shares;
    }

    private static int positiveOrDefault(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static int readInt(JsonObject object, String key, int fallback) {
        JsonElement rawValue = object.get(key);
        if (rawValue == null || !rawValue.isJsonPrimitive()) return fallback;

        JsonPrimitive value = rawValue.getAsJsonPrimitive();
        if (!value.isNumber()) return fallback;
        double number = value.getAsDouble();
        if (!Double.isFinite(number)) throw new DockLayoutException(key + " is not finite");

        long rounded = Math.round(number);
        if (rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE) {
            throw new DockLayoutException(key + " is outside the integer range");
        }
        return (int) rounded;
    }

    private static String readString(JsonElement value) {
        if (value == null || !value.isJsonPrimitive()) return null;

        JsonPrimitive primitive = value.getAsJsonPrimitive();
        return primitive.isString() ? primitive.getAsString() : null;
    }

    private static void requireFiniteNumbers(JsonElement value) {
        if (value.isJsonArray()) {
            for (JsonElement element : value.getAsJsonArray()) requireFiniteNumbers(element);
            return;
        }
        if (value.isJsonObject()) {
            for (JsonElement element : value.getAsJsonObject().asMap().values()) requireFiniteNumbers(element);
            return;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                && !Double.isFinite(value.getAsDouble())) {
            throw new DockLayoutException("JSON number is not finite");
        }
    }

    private static JsonObject asObject(JsonElement value, String description) {
        if (value != null && value.isJsonObject()) return value.getAsJsonObject();
        throw new DockLayoutException(description + " is not an object");
    }

    private static JsonArray asArray(JsonElement value, String description) {
        if (value != null && value.isJsonArray()) return value.getAsJsonArray();
        throw new DockLayoutException(description + " is not an array");
    }
}
