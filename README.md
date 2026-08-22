# Mouse Strokes HUD (Fabric, Minecraft 1.21.11)

A client-side-only HUD widget: one 20%-opacity black square with a
configurable-colored outline, and one circular indicator of the same color
that moves away from center in the exact physical direction of raw mouse
movement, then smoothly returns to center when the mouse stops.

This project was generated as source only. It has NOT been compiled or run -
see "Static review only" in the accompanying chat explanation for exactly
what was and wasn't independently verified, and against which sources. It
has since been through one static build-readiness audit pass (also not a
real compile) - see that report for what changed and why.

## Requirements

- Java 21 (Minecraft 1.21.11 requires Java 21; Loom 1.14 also requires Java 21 to run Gradle itself)
- Minecraft 1.21.11, Fabric Loader >= 0.18.1, Fabric API 0.141.x+1.21.11 (or newer - check https://fabricmc.net/develop/)
- Do NOT hand-pick a Gradle version yourself. Get the wrapper from the
  official template generator (see step 1 below) - it already pins the
  exact matching Gradle build for whatever Minecraft version you select.

## Recommended way to build this

This zip contains source and Gradle *scripts* only (`build.gradle`,
`settings.gradle`, `gradle.properties`) - it does **not** contain a Gradle
wrapper jar/scripts, since generating a correct `gradle-wrapper.jar` requires
network access this environment didn't have, and guessing the wrapper's exact
Gradle version was explicitly out of scope per the task's own rules.

1. Get a Gradle wrapper that actually matches Loom 1.14/Minecraft 1.21.11.
   **Do not** clone `FabricMC/fabric-example-mod`'s `1.21` branch for this
   the way an earlier draft of this README said to - that branch is a
   moving target the Fabric team continuously updates, and as of this
   audit it has already moved on to plain `1.21` with a newer Loom
   (`1.16-SNAPSHOT`) and Loader (`0.19.3`), not 1.21.11. Relying on it would
   have silently handed you the wrong toolchain. Instead, use the
   authoritative source for a version-matched project: go to
   https://fabricmc.net/develop/template/ , select Minecraft **1.21.11**,
   generate, and download that zip. It always ships a `gradlew`/`gradlew.bat`/
   `gradle/wrapper/` pre-matched to whatever Loom needs for the version you
   picked - that is the "generated `gradle-wrapper.properties`" this project's
   own instructions point you to, since I cannot fetch it myself from here.
2. From the generated template, keep only `gradlew`, `gradlew.bat`, and
   `gradle/wrapper/` (delete its `src/`, `build.gradle`, `gradle.properties`,
   `settings.gradle`), then copy this project's versions of those four things
   into their place.
3. Open `gradle.properties` and double check `yarn_mappings`, `loader_version`,
   `fabric_version`, and `loom_version` against whatever the template
   generator just used for 1.21.11 - versions drift, and the ones in this
   file are what were current when this project was written.
4. From the project root:
   - Linux/macOS: `./gradlew build`
   - Windows: `gradlew.bat build`
5. The mod jar appears at `build/libs/mousestrokes-0.1.0.jar` (NOT the
   `-sources.jar` next to it - see the chat explanation's "which jar" note).
6. Install Fabric Loader 1.21.11 in your Minecraft launcher, put Fabric API's
   jar for 1.21.11 AND `mousestrokes-0.1.0.jar` into `.minecraft/mods/`, and
   launch.

### Alternative, if you already have any Gradle installed somewhere (e.g. bundled with IntelliJ)

You can generate a fresh wrapper yourself instead of using the template
generator: `cd` into this project and run `gradle wrapper` using that
existing Gradle install. Loom will tell you on the first `./gradlew build`
attempt if the wrapper's Gradle version is too old/new for the declared
Loom version - if so, re-run `gradle wrapper --gradle-version <version>`
with whatever version the error message asks for. This is a slower,
trial-and-error path compared to step 1 above, included only as a fallback.

## Files

```
build.gradle              - Gradle build script (Loom + Fabric deps)
gradle.properties         - version pins (MC/Yarn/Loader/Fabric API/Loom)
settings.gradle           - plugin repositories
src/main/resources/
  fabric.mod.json          - mod descriptor (client-only environment)
  mousestrokes.mixins.json - Mixin config
src/main/java/com/example/mousestrokes/
  MouseStrokesMod.java             - ClientModInitializer, wiring
  MouseStrokesClientLifecycle.java - reset/drain on lifecycle boundaries
  mixin/MouseMixin.java            - observes Mouse.onCursorPos (HEAD inject)
  telemetry/MouseTelemetry.java    - thread-guarded raw delta accumulator
  telemetry/MouseVector.java       - dx/dy + lazy magnitude/angle
  state/MouseStrokeState.java      - target/render offset state machine
  config/MouseStrokesConfig.java   - persisted, clamped configuration
  config/MouseStrokesConfigScreen.java - minimal built-in config screen
  render/MouseStrokesHud.java      - HudElement: square + outline + circle
```

See the accompanying chat response for the full verification ledger,
data-flow explanation, and known limitations.
