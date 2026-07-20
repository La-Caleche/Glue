package fr.lacaleche.glue.mcsx.core.dock;

import fr.lacaleche.glue.mcsx.core.dock.DockSplit.Dir;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * (De)serializes a {@link DockLayout} as JSON. The format stores structure only — tabs, split
 * directions, shares, window frames — never node ids: a loaded layout is re-minted through the
 * caller's {@link DockIds}, which is what keeps ids unique in the workspace that adopts it.
 *
 * <p>Reading is tolerant the way a config file needs to be: unknown fields are ignored (so a
 * newer writer stays loadable) and defaults fill small gaps, but a file whose structure cannot be
 * understood throws {@link DockLayoutException} so the caller can fall back to its default layout
 * instead of adopting garbage. Hand-rolled on purpose: {@code core.*} is pure {@code java.*}, and
 * the schema is small enough that a JSON library would cost more than these few methods.
 */
public final class DockLayoutCodec {

    private static final int VERSION = 1;

    private DockLayoutCodec() {
    }

    public static String write(DockLayout layout) {
        StringBuilder out = new StringBuilder(256);
        out.append("{\"version\":").append(VERSION).append(",\"tree\":");
        writeNode(out, layout.tree());
        out.append(",\"floats\":[");
        List<DockFloat> floats = layout.floats();
        for (int i = 0; i < floats.size(); i++) {
            DockFloat f = floats.get(i);
            if (i > 0) {
                out.append(',');
            }
            out.append("{\"x\":").append(f.x()).append(",\"y\":").append(f.y())
                    .append(",\"w\":").append(f.w()).append(",\"h\":").append(f.h())
                    .append(",\"z\":").append(f.z()).append(",\"node\":");
            writeNode(out, f.node());
            out.append('}');
        }
        out.append("]}");
        return out.toString();
    }

    private static void writeNode(StringBuilder out, DockNode node) {
        if (node == null) {
            out.append("null");
            return;
        }
        if (node instanceof DockLeaf leaf) {
            out.append("{\"type\":\"leaf\",\"tabs\":[");
            for (int i = 0; i < leaf.tabs().size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                writeString(out, leaf.tabs().get(i));
            }
            out.append("],\"active\":");
            writeString(out, leaf.active());
            out.append('}');
            return;
        }
        DockSplit split = (DockSplit) node;
        out.append("{\"type\":\"split\",\"dir\":\"").append(split.dir() == Dir.ROW ? "row" : "col")
                .append("\",\"sizes\":[");
        for (int i = 0; i < split.sizes().size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(split.sizes().get(i));
        }
        out.append("],\"children\":[");
        for (int i = 0; i < split.children().size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            writeNode(out, split.children().get(i));
        }
        out.append("]}");
    }

    private static void writeString(StringBuilder out, String value) {
        if (value == null) {
            out.append("null");
            return;
        }
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    /**
     * Parses a layout, re-minting every node id through {@code ids} and pruning anything the file
     * left degenerate (empty leaves, single-child splits).
     *
     * @throws DockLayoutException when the text is not JSON or not shaped like a layout
     */
    public static DockLayout read(String json, DockIds ids) {
        Object root = new JsonParser(json).parseDocument();
        Map<?, ?> doc = asObject(root, "document");
        DockNode tree = DockOps.prune(readNode(doc.get("tree"), ids));
        List<DockFloat> floats = new ArrayList<>();
        Object rawFloats = doc.get("floats");
        if (rawFloats != null) {
            for (Object item : asArray(rawFloats, "floats")) {
                Map<?, ?> obj = asObject(item, "float");
                DockNode node = DockOps.prune(readNode(obj.get("node"), ids));
                if (node == null) {
                    continue;
                }
                floats.add(new DockFloat(ids.next("float"), node,
                        readInt(obj, "x", 0), readInt(obj, "y", 0),
                        readInt(obj, "w", 360), readInt(obj, "h", 260),
                        readInt(obj, "z", floats.size() + 1)));
            }
        }
        return new DockLayout(tree, floats);
    }

    private static DockNode readNode(Object raw, DockIds ids) {
        if (raw == null) {
            return null;
        }
        Map<?, ?> obj = asObject(raw, "node");
        Object type = obj.get("type");
        if ("leaf".equals(type)) {
            List<String> tabs = new ArrayList<>();
            for (Object tab : asArray(obj.get("tabs"), "tabs")) {
                if (tab instanceof String s && !tabs.contains(s)) {
                    tabs.add(s);
                }
            }
            Object active = obj.get("active");
            String shown = active instanceof String s && tabs.contains(s) ? s
                    : tabs.isEmpty() ? null : tabs.getFirst();
            return new DockLeaf(ids.next("leaf"), tabs, shown);
        }
        if ("split".equals(type)) {
            // Sizes are read against the RAW child count and each dropped child takes its share
            // with it: a split of [0.7, 0.3, unreadable] must load as [0.7, 0.3] renormalized,
            // not fall back to even shares (which would silently re-proportion the user's panes).
            List<?> rawChildren = asArray(obj.get("children"), "children");
            List<Double> rawSizes = readSizes(obj.get("sizes"), rawChildren.size());
            List<DockNode> children = new ArrayList<>();
            List<Double> shares = new ArrayList<>();
            for (int i = 0; i < rawChildren.size(); i++) {
                DockNode node = readNode(rawChildren.get(i), ids);
                if (node != null) {
                    children.add(node);
                    shares.add(rawSizes.get(i));
                }
            }
            if (children.isEmpty()) {
                return null;
            }
            if (children.size() == 1) {
                return children.getFirst();
            }
            Object dir = obj.get("dir");
            return new DockSplit(ids.next("split"),
                    "col".equals(dir) ? Dir.COL : Dir.ROW, children, normalize(shares));
        }
        throw new DockLayoutException("node has unknown type: " + type);
    }

    /** Shares are renormalized; a malformed array (wrong count, negatives) falls back to even shares. */
    private static List<Double> readSizes(Object raw, int count) {
        List<Double> sizes = new ArrayList<>();
        if (raw instanceof List<?> list && list.size() == count) {
            for (Object value : list) {
                if (!(value instanceof Double d) || d < 0) {
                    sizes.clear();
                    break;
                }
                sizes.add(d);
            }
            if (sizes.size() == count) {
                return normalize(sizes);
            }
        }
        return evenShares(count);
    }

    /** Scales shares to sum to 1; a set that sums to nothing has no proportions left to keep. */
    private static List<Double> normalize(List<Double> shares) {
        double sum = 0d;
        for (double share : shares) {
            sum += share;
        }
        if (sum <= 0) {
            return evenShares(shares.size());
        }
        final double total = sum;
        return shares.stream().map(v -> v / total).toList();
    }

    private static List<Double> evenShares(int count) {
        double share = 1d / count;
        List<Double> even = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            even.add(share);
        }
        return even;
    }

    private static Map<?, ?> asObject(Object value, String what) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new DockLayoutException(what + " is not an object");
    }

    private static List<?> asArray(Object value, String what) {
        if (value instanceof List<?> list) {
            return list;
        }
        throw new DockLayoutException(what + " is not an array");
    }

    private static int readInt(Map<?, ?> obj, String key, int fallback) {
        return obj.get(key) instanceof Double d ? (int) Math.round(d) : fallback;
    }

    /**
     * A minimal JSON reader producing {@code Map<String,Object>} / {@code List<Object>} /
     * {@code String} / {@code Double} / {@code Boolean} / null. Strict about syntax, silent about
     * schema — the mapping above decides what the values mean.
     */
    private static final class JsonParser {

        private final String src;
        private int pos;

        JsonParser(String src) {
            this.src = src;
        }

        Object parseDocument() {
            Object value = parseValue();
            skipWhitespace();
            if (pos < src.length()) {
                throw error("trailing characters");
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (pos >= src.length()) {
                throw error("unexpected end of input");
            }
            char c = src.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                map.put(key, parseValue());
                skipWhitespace();
                char c = next();
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw error("expected ',' or '}'");
                }
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                char c = next();
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw error("expected ',' or ']'");
                }
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escape = next();
                switch (escape) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> {
                        if (pos + 4 > src.length()) {
                            throw error("truncated \\u escape");
                        }
                        try {
                            out.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException e) {
                            throw error("bad \\u escape");
                        }
                        pos += 4;
                    }
                    default -> throw error("bad escape '\\" + escape + "'");
                }
            }
        }

        private Double parseNumber() {
            int start = pos;
            while (pos < src.length() && "-+.eE0123456789".indexOf(src.charAt(pos)) >= 0) {
                pos++;
            }
            try {
                return Double.parseDouble(src.substring(start, pos));
            } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                throw error("bad number");
            }
        }

        private Boolean parseBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw error("bad literal");
        }

        private Object parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw error("bad literal");
        }

        private void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            if (pos >= src.length()) {
                throw error("unexpected end of input");
            }
            return src.charAt(pos);
        }

        private char next() {
            if (pos >= src.length()) {
                throw error("unexpected end of input");
            }
            return src.charAt(pos++);
        }

        private void expect(char c) {
            if (next() != c) {
                pos--;
                throw error("expected '" + c + "'");
            }
        }

        private DockLayoutException error(String message) {
            return new DockLayoutException(message + " at offset " + pos);
        }
    }
}
