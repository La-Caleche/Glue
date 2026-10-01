# Client-test assets

This directory is copied into each disposable client-test profile before Minecraft starts.
Paths here are paths relative to Minecraft's game directory:

```text
assets/
  config/iris.properties
  shaderpacks/test-shaderpack/shaders/...
  resourcepacks/...
  saves/...
```

`clientTest`, `clientTestSodium` and `clientTestIris` prepare separate directories under
`build/run/<task-name>/`. README files are excluded. Assets are never packaged into the test mod,
the showcase jar or a library jar. No path or shaderpack launch property is needed.

Small fixtures and configuration can be versioned here. Third-party shaderpacks, resource packs
and local world copies are ignored by Git. The supplied Iris configuration selects the local
`shaderpacks/test-shaderpack` directory; see its [setup notes](shaderpacks/README.md).
