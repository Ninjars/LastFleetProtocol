# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Last Fleet Protocol is a real-time 2D game built with Kotlin Multiplatform (KMP) targeting Android and Desktop (JVM). iOS scaffolding exists but is currently commented out. The UI layer uses Compose Multiplatform with Material 3. Game rendering and simulation use the [Kubriko](https://github.com/pandulapeter/kubriko) engine.

## Build Commands

```shell
# Build Android debug APK
./gradlew :composeApp:assembleDebug

# Run desktop (JVM) app
./gradlew :composeApp:run

# Run all tests
./gradlew allTests

# Run a single test class (example)
./gradlew :composeApp:jvmTest --tests "jez.lastfleetprotocol.AngleTests"

# Full project compilation check
./gradlew build
```

Requires JDK 21. Gradle configuration cache and build caching are enabled.

## Architecture

### Module Structure (api/impl split)

All features and most components use an `api`/`impl` module split. Public contracts live in `api`; implementations live in `impl`. Callers depend on `api` modules.

- **`:composeApp`** — composition root. Imports all `api` and `impl` modules, hosts navigation (`LFNavHost`), theme, and the root `App` composable with the background Kubriko viewport.
- **`:components:design`** — shared UI components (Material 3 themed).
- **`:components:shared:api/impl`** — cross-cutting utilities and data classes.
- **`:components:game-core:api/impl`** — game simulation logic, state, and loading status contracts.
- **`:components:preferences:api/impl`** — user preferences (music, SFX toggles).
- **`:features:splash:api/impl`**, **`:features:landing:api/impl`**, **`:features:game:api/impl`** — screen-level features.

### Dependency Direction

Feature `impl` modules depend on their own `api` plus component `api` modules. Only `:composeApp` wires `impl` modules together. Do not add cross-feature dependencies or shortcut through `impl` modules.

### MVI Pattern

- **ViewModel** (extends `LFViewModel` patterns): receives intents, updates `StateFlow<State>`, emits side effects via `Channel`.
- **Composable**: reads state, dispatches intents, contains no business logic.
- **Use cases**: host business rules, invoked by ViewModels.

### Dependency Injection

Uses [kotlin-inject](https://github.com/evant/kotlin-inject) (v0.8.0) with KSP code generation. The root `AppComponent` in `composeApp` binds all feature entries and Kubriko managers. Two Kubriko instances are managed: `KUBRIKO_BACKGROUND` (ambient rendering) and `KUBRIKO_GAME` (active gameplay).

### Kubriko Game Engine

The game uses multiple Kubriko managers (StateManager, ActorManager, SpriteManager, AudioManager, ViewportManager, PointerInputManager, PersistenceManager). When modifying game runtime code:
- Treat simulation/render loop changes as high-risk; validate across both Android and Desktop.
- Keep deterministic gameplay logic separated from presentation.
- Extend existing Kubriko plugin patterns rather than introducing ad-hoc engine glue.
- Be cautious with loading and audio lifecycle—avoid side effects from unstable composition recomposition.

### Platform Source Sets

- `commonMain` — all cross-platform code (strongly preferred for new code).
- `androidMain` — Android-specific (Activity, platform expect/actual).
- `jvmMain` — Desktop-specific (main function, coroutines-swing).

Use platform source sets only when platform APIs are required.

## Key Conventions

- All source files are Kotlin.
- Cross-platform logic goes in `commonMain`.
- New behavior goes in `impl`; expose only required contracts in `api`.
- Preserve module boundaries—do not add convenience dependencies that bypass feature/component APIs.
- When changing cross-module contracts, update all affected modules in one cohesive change.
- **UI text must not be hardcoded in composables.** Define all user-visible strings in `:components:design` `commonMain/composeResources/values/strings.xml`, add a corresponding entry to `LFRes.String` in `LFRes.kt`, and reference it in layouts via `stringResource(LFRes.String.<id>)`. See `LandingScreen.kt` for the pattern.

## AI Agent Docs

For deeper context on specific topics, see `docs/ai_index.md` which routes to:
- `docs/ai_architecture.md` — module dependency rules
- `docs/ai_workflows.md` — implementation workflows and validation checklists
- `docs/ai_kubriko_constraints.md` — Kubriko runtime constraints
- `docs/game_design.md`, `docs/ship.md`, `docs/weapons.md` — game design and mechanics