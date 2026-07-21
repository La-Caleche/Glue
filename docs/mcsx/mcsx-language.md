# The `.mcsx` Language

`.mcsx` is an HTML/JSX-like markup language. It is parsed in two phases — a mode-aware
`McsxTokenizer` produces a flat token list, then `McsxParser` builds the AST. There is **no
expression language**: `{ref}` and `{{ref}}` are trimmed reference strings, nothing more.

All types live in `fr.lacaleche.glue.mcsx.core.mcsx`, which is pure `java.*` and headless-testable.

## Documents

A document is an optional `<import>` prelude followed by **exactly one root element**:

```xml
<import name="Button" from="mcsx:components/button"/>
<import name="Card" from="mcsx:components/card"/>

<div class="flex-col gap-4 p-4">
    <text>Hello</text>
</div>
```

Content after the root element is an error (`unexpected content after root element`). Documents are
loaded with `DocumentLoader` and parsed with `McsxParser.parseDocument` (imports allowed).
`McsxParser.parse` parses a bare fragment and rejects any import.

## Elements

```
element := '<' name attribute* ( '/>' | '>' content* '</' name '>' )
```

- **Self-closing:** `<button/>`, `<slot/>`, `<icon name="check"/>` → empty children list.
- **Open/close:** `<div>…</div>`. The closing tag name must match the opener exactly, or you get
  `mismatched closing tag </X>, expected </Y>`. Reaching EOF first gives
  `unterminated element <tag>, expected </tag>`.

### Names

A tag or attribute **name** must start with a letter or `_`, then allows letters, digits, `_`, `-`.
Case is preserved and carries meaning **by convention only** — lowercase tags are base primitives
(`div`, `text`, `button`, `icon`, …) and PascalCase tags are imported components (`Button`, `Card`).
The parser makes no distinction; the binder does.

## Attributes

Three forms:

| Form | Example | Parsed as |
|---|---|---|
| **Literal** | `size="20"` / `from='x'` | value = `"20"`, `binding = false` |
| **Boolean** (valueless) | `grow` | value = `""`, `binding = false` |
| **Binding** | `onClick={ping}` | value = `"ping"`, `binding = true` |

- Both single `'` and double `"` quotes are accepted for literals; quotes are stripped, contents are
  taken raw.
- After `=`, only a string or a `{binding}` is legal — anything else throws
  `expected a string or {binding} value after '=' for attribute 'X'`.
- A **binding** uses **single braces** immediately after `=`: `checked={enabled}`, `cond={tab.active}`.

> ⚠️ **`{name}` inside a quoted string is NOT a binding at the parser level.** `class="rounded {class}"`
> is a literal `STRING` containing brace characters. This *class interpolation* is resolved later by
> the binder (see [Components](components.md)); the tokenizer never looks inside quotes.

## Text and interpolation

Inside element content, text may contain `{{ref}}` interpolations:

```xml
<text>Count: {{count}}</text>
```

- **Only `{{` starts an interpolation.** A lone `{` in text is literal. `{{}}` (empty after trim)
  throws `empty interpolation '{{}}'`.
- **Double braces `{{ }}` work only in text; single braces `{ }` work only as attribute values.**
  There is no single-brace binding in text and no double-brace one in an attribute.

### Whitespace normalization

Text runs are normalized so pretty-printing indentation is safe:

- Internal whitespace in each literal part collapses to a single space.
- The first part is left-stripped; the last part is right-stripped.
- Literal parts that become empty are dropped; binding parts are always kept.
- A whitespace-only run between elements produces **no** text node.

So `"  Count:   {{n}}  "` becomes two parts — literal `"Count: "` (the space before `{{n}}` is kept)
and binding `"n"`. Indentation between elements never renders as stray text.

## Dotted binding paths

A reference may be a dotted path — `{{task.display}}`, `cond={tab.active}`. The parser stores the
whole path verbatim as one string; the binder navigates it (zero-arg method, then field) at bind
time. See [Controllers](controllers.md) §Binding resolution.

## Comments

`<!-- like this -->`. Comments are tokenized but discarded — they never appear in the AST. An
unterminated comment throws `unterminated comment, expected '-->'`.

## The `<import>` prelude

```xml
<import name="Alias" from="ns:path"/>
```

Rules (all enforced at parse time):

- **Must be self-closing** (`/>`), or `<import> must be self-closing`.
- `name` and `from` are **required, non-empty literals** (never bindings).
- Duplicate `name` → `duplicate <import> name 'X'`.
- Unknown attribute → `unknown <import> attribute 'X'`.
- Only allowed in the prelude, only via `parseDocument`. `<import>` anywhere else throws.

`from` is a document id (`"ns:path"`) resolved the same way as `@UIController` — see below.

## No escapes

There are **no character or entity escapes**. `&amp;`, `\"`, `&lt;` are not processed — every
character is literal. A `<` in text always starts a tag; there is no way to escape `<`, `{{`, or a
quote inside a string.

## Document ids and resource paths

`DocumentLoader` maps an id `"namespace:path"` to the classpath resource
`assets/<namespace>/ui/<path>.mcsx`:

| Id | Resource |
|---|---|
| `mcsx:demo` | `assets/mcsx/ui/demo.mcsx` |
| `mcsx:components/button` | `assets/mcsx/ui/components/button.mcsx` |

Only the **first** `:` splits namespace from path; the namespace and path must both be non-empty
(`:x` and `x:` throw `IllegalArgumentException`). Path segments after the colon keep their `/`, so
nesting works.

```java
McsxDocument doc = DocumentLoader.loadFromClasspath("mcsx:demo");
// or, with an explicit ClassLoader:
McsxDocument doc = DocumentLoader.load("mcsx:demo", myClassLoader);
```

A missing resource throws `IllegalArgumentException`; an I/O failure throws `UncheckedIOException`.
Files are read as UTF-8.

## The AST

All records live in `core.mcsx`:

```java
sealed interface McsxContent permits McsxElement, McsxText {}

record McsxElement(String tag, List<McsxAttribute> attributes,
                   List<McsxContent> children, int line, int column) implements McsxContent {
    String attribute(String name);   // literal value of a NON-binding attr, else null
}
record McsxText(List<Part> parts) implements McsxContent {
    record Part(String value, boolean binding) {}
}
record McsxAttribute(String name, String value, boolean binding) {}
record McsxDocument(Map<String, String> imports, McsxElement root) {}  // imports: name → from
```

> **`element.attribute(name)` hides bindings.** It returns `null` for a binding-valued attribute
> (exactly as if it were absent) and `""` for a valueless boolean attribute. To read a binding you
> must iterate `element.attributes()` and check `binding()`.

## Errors

All malformed input — at both the tokenize and parse stages — throws **`McsxParseException`** (an
unchecked `RuntimeException`). Line and column are **1-based** and point at the first character of the
offending token; the position is baked into the message:

```
empty interpolation '{{}}' at line 1, column 6
```

`DocumentLoader` uses different exceptions for id/IO problems: `IllegalArgumentException` (bad id or
missing resource) and `UncheckedIOException` (read failure).

## Real examples

Import prelude, bindings, and text interpolation (`demo.mcsx`):

```xml
<import name="Checkbox" from="mcsx:components/checkbox"/>

<div bg="#151a2e" pad="16">
    <text size="20" color="#5be49b">MCSX on ModernUI</text>
    <text>{{label}}</text>
    <button onClick={ping} bg="#5be49b" pad="8">
        <text color="#0b0e12" size="16">Increment</text>
    </button>
    <Checkbox checked={enabled}><text>Enabled</text></Checkbox>
</div>
```

Control flow with a class-interpolation hole (`editor.mcsx`):

```xml
<for each={leftTabs} as="tab" class="flex-row gap-2">
    <div onClick={selectLeftTab} class="rounded px-3 py-1 {tab.classes}">
        <text class="text-xs font-semibold">{{tab.name}}</text>
    </div>
</for>
```

See [Components](components.md) for `<slot/>`, `<variants>`/`<case>`, and how those `{…}` class holes
are resolved.
