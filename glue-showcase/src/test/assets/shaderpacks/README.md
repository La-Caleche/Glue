# Shader fixture

Put an unpacked Iris shaderpack in `test-shaderpack/`, with `shaders/` immediately inside it.
The native Iris configuration in `../config/iris.properties` already selects that name.
To use a differently named directory or ZIP, edit that configuration as in a normal game profile.

The migration was exercised with Complementary Unbound 5.7.1 + Euphoria Patches 1.8.6. The local
fixture uses that pack; its third-party files are intentionally not committed. A fresh checkout
needs a local shader fixture only for tests which activate shaders. The vanilla and Sodium
profiles do not require one.

Run a shader scenario with:

```powershell
.\gradlew.bat :glue-showcase:clientTestIris --tests viewport-sky
```
