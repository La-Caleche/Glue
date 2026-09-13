# Glue Java Style

This guide defines the conventions for maintained Java sources and Java tests in Glue. It is not a
general engineering handbook. Repository architecture, module boundaries, workflow, and verification
commands live in [`AGENTS.md`](../../AGENTS.md).

Do not restyle unrelated code merely to make an existing file conform.

## Precedence

Follow `AGENTS.md`'s instruction order. Nearby code decides only choices these canonical documents do
not specify. Preserve demonstrated public and durable contracts, but do not add speculative
compatibility shims.

## Language and Types

- Target Java 21 and use only features supported by the configured toolchain and runtime frameworks.
- Use explicit local variable types by default. Do not introduce `var` unless a maintained package has
  deliberately standardized it.
- Prefer records for genuinely immutable values and parameter carriers when codecs, reflection, and
  framework construction permit them.
- Use ordinary classes when mutation, identity, inheritance, lifecycle, or serialization requires it.
- Use interfaces for real boundaries or multiple implementations, not for every class.
- Avoid raw types and unchecked casts. Isolate and narrowly suppress an unavoidable framework-boundary
  cast.

## Formatting

- Indent with four spaces, never tabs.
- Put opening braces on the declaration or control-flow line and closing braces on their own line.
- Keep one public top-level type per file.
- Leave one blank line after a type declaration and between meaningful member groups.
- Do not align declarations with artificial runs of spaces or leave trailing whitespace.
- Wrap long signatures, argument lists, chains, and conditions when it improves readability; there is
  no mechanical line limit.
- Do not reformat unrelated code.

Prefer compact single-statement guards without braces when they are unambiguous:

```java
if (target == null) return;
if (entries.isEmpty()) return;
```

Use braces for multiple statements, nested branches, mixed branch shapes, or a comment inside the
branch.

## Imports

- Put project and third-party imports before JDK imports.
- Separate JDK imports with one blank line and static imports into their own group.
- Use explicit imports in new code; do not add wildcard imports.
- Remove unused imports from modified files.
- Use a fully qualified name inline only to resolve a genuine collision.

## Naming and Organization

- Use lowercase package segments, PascalCase types, camelCase members and locals, and uppercase snake
  case for true constants.
- Use precise domain language and established acronyms. Avoid vague names such as `data`, `process`,
  or `manager` when a more specific name exists.
- Name operations with verbs and predicates with `is`, `has`, or `can` where appropriate.
- Do not prefix new interfaces with `I`.
- Use role suffixes such as `Manager`, `Factory`, `Parser`, or `Handler` only when they accurately
  describe the responsibility.
- Organize packages by domain before technical role. Do not create a package for one type without a
  concrete reason.

## Type Design

- Expose the smallest useful public API. Keep fields private by default, use package-private
  visibility for intentional package collaboration, and use `protected` only for deliberate
  extension state or behavior.
- Mark constructor-established, never-reassigned fields `final`.
- Do not apply `final` mechanically to every class, parameter, or local variable.
- Establish required dependencies and valid state in constructors, but keep substantial I/O,
  registration, and lifecycle side effects in explicit methods.
- Add a builder only when optional state or a fluent domain language justifies it.
- Keep utility classes cohesive and non-instantiable. Do not create a general-purpose helper dumping
  ground.
- Reuse existing infrastructure before introducing a service, singleton, registry, or abstraction.

Default member order for new types:

1. Static constants and state.
2. Instance fields.
3. Constructors.
4. Lifecycle and interface overrides.
5. Public behavior and accessors.
6. Protected extension methods.
7. Private helpers.
8. Static factories and nested types.

Do not reorder an existing type solely to enforce this list.

## Methods and Control Flow

- Keep methods focused and side effects visible.
- Prefer direct code over a helper used once unless extraction gives a substantial operation a useful
  name.
- Extract repeated logic, resource ownership, complex branching, or a distinct domain operation.
- Use guard clauses instead of avoidable nesting.
- Annotate every override with `@Override`.
- Avoid boolean parameters when named operations or an enum materially clarify call sites.
- Use streams for clear transformations and aggregate predicates. Use loops for stateful mutation,
  multiple side effects, indexed traversal, checked exceptions, and early exits.
- Never use an atomic object or one-element array merely to mutate state inside a sequential lambda.

## Nullability and Collections

- Null is acceptable for absence when the contract is clear and matches the surrounding API.
- Guard nullable results before dereferencing them and validate mandatory boundary inputs early.
- Make non-obvious public nullability, mutability, ownership, and lifecycle contracts explicit.
- Return empty collections rather than `null` when emptiness is the accurate result.
- Do not expose mutable internal collections without an explicit ownership reason.
- Use `Optional` selectively for public lookup results or when an external API already returns it. Do
  not use it for fields or parameters, and do not call `get()` without immediately visible proof.

## Failures and Logging

- Throw `IllegalArgumentException` for invalid caller input and `IllegalStateException` for invalid
  lifecycle or setup state.
- Catch the narrowest practical exception and preserve the cause when translating it.
- Catch broadly only at integration boundaries that can add context or choose recovery.
- Never silently swallow unexpected failures. Restore the interrupt flag after catching
  `InterruptedException` unless a documented framework contract says otherwise.
- Use the project's logger with parameterized messages. Do not use `System.out`, `System.err`, or
  `printStackTrace` in application code.
- Keep log messages concise, professional, and free of secrets.

## Lifecycle and Concurrency

- Give each resource a clear owner and cleanup path; make shutdown and closure idempotent.
- Use try-with-resources for lexically owned resources and explicit lifecycle methods for long-lived
  resources.
- Do not start unmanaged threads or asynchronous work without a concrete need and cancellation path.
- Prefer thread confinement and immutable snapshots over broad locking.
- Marshal work onto Minecraft's client thread, ModernUI's UI thread, or the render thread before
  touching APIs confined to that domain.
- Use atomic and concurrent types only for demonstrated cross-thread state.

## Comments and API Documentation

- Prefer precise names, types, and control flow over explanatory prose.
- Document public or extensible contracts when callers need lifecycle, ownership, thread, nullability,
  failure, or ordering information not visible in the signature.
- Put contract documentation on the interface or abstract declaration rather than repeating it on
  implementations.
- Comment the reason for a non-obvious algorithm, workaround, compatibility rule, or platform
  constraint. Do not narrate the next line.
- Do not add decorative section comments, placeholder Javadocs, `{@inheritDoc}`-only Javadocs,
  commented-out implementations, or unfinished TODO/stub content.
- Update or remove comments when behavior changes.

## Tests and Change Hygiene

- Use JUnit 5 and package tests with the production code they cover.
- Name tests after observable behavior and use deterministic assertions.
- Cover boundaries and failure paths when they carry meaningful risk.
- Prefer small real collaborators over excessive mocking; do not introduce an interface solely to
  mock a trivial class.
- Before finishing a Java change, remove unused imports, dead code, debug output, placeholders, and
  accidental formatting churn from the touched files.
