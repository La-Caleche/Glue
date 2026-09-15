# Glue Web

Client-only Chromium surfaces for Minecraft 1.21.8 / Java 21.

- Artifact: `fr.lacaleche.glue:glue-web:<version>`
- Fabric mod id: `glue-web`
- Public API: `fr.lacaleche.glue.web`
- [API guide](../docs/src/content/docs/web/index.md)
- [Packaging and development](../docs/development/glue-web.md)

```java
WebSurface surface = WebSurface.builder(URI.create("https://example.org/"))
        .size(1024, 768)
        .frameRate(60)
        .transparent(true)
        .open();
```

Open and operate surfaces on Minecraft's client thread. The host forwards input, draws into its GUI
rectangle and closes the surface. Fabric registers the pipeline and shuts down remaining surfaces
and the shared runtime automatically. The native runtime is preloaded asynchronously at mod startup,
before a surface is requested. A discreet bottom-right indicator shows download/install/startup
progress across menus and gameplay, then fades out. The native distribution and browser profile are
stored under the game directory's `glue-web/`.

The library contains the renderer, input/cursor adapters and an explicit origin-scoped message
bridge. Browser chrome, HTTP fixture hosting, React pages and benchmark probes belong to applications
or showcase. The temporary [`web-demo/`](../web-demo/README.md) is built separately and never enters
the library jar. Gradle builds and tests Glue Web without Node or pnpm.

JCEF's installer/helpers and Commons IO are relocated privately. Native `org.cef` JNI names must stay
unchanged. This does not promise coexistence with a second independently initialized CEF wrapper.
