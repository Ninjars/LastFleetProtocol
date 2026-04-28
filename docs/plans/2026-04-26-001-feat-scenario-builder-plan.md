---
title: "feat: Scenario Builder — compose-and-launch dev tool"
type: feat
status: active
date: 2026-04-26
origin: docs/brainstorms/2026-04-26-scenario-builder-requirements.md
---

# Scenario Builder — compose-and-launch dev tool

## Overview

Add a Desktop-dev-only screen that lets a dev pick ships from `DefaultShipDesignLoader.loadAll()`, position them via numeric x/y inputs (with a read-only mini-map preview for spatial sanity), save the scenario as a named JSON file in app-data, and launch the game with that configuration. Scenario state survives the launch-and-return loop so devs can iterate. The 5-ship layout currently hardcoded in `GameStateManager.startDemoScene` becomes a shared `DemoScenarioPreset` constant that both the production demo path and the scenario builder's "Use demo defaults" affordance read from. New `DevToolsGate` abstraction surfaces a single "Scenario Builder (dev)" link on the landing screen when the gate is open. Roadmap item B; depends on the asset-export plumbing shipped in item A.

**Integration notes.** New `:features:scenario-builder:{api,impl}` module pair; new `Scenario` / `SpawnSlotConfig` / `DemoScenarioPreset` / `PendingScenario` types in `:components:game-core:api`; new `DevToolsGate` in `:components:shared:api`; refactor of `GameStateManager` to expose a public `startScene(slots: List<SpawnSlotConfig>)` and reduce `startDemoScene()` to literally `startScene(DemoScenarioPreset.SLOTS)`; LandingVM gains a `DevToolsGate` injection + new `GoToScenarioBuilder` side effect; LFNavHost gains a `SCENARIO_BUILDER` destination. Production demo path's observable behaviour is unchanged. No existing API contracts change.

## Working Assumptions (carried from origin)

These were captured in `docs/brainstorms/2026-04-26-scenario-builder-requirements.md` and are inlined here so the plan reads on its own without cross-doc lookups.

1. **Compose Nav back-stack survives the Game overlay.** Default `composable(...)` entries scope a `ViewModelStore` per `NavBackStackEntry`; the `ScenarioBuilderVM` instance is retained while the Game destination is on top of it. No `popUpTo` is used on the Launch navigation. Verified by ship-builder's existing nav pattern.
2. **`SHIP_FILENAMES` is hand-edited.** The bundled-ship list in `DefaultShipDesignLoader.SHIP_FILENAMES` is updated by hand when a new ship is bundled. The scenario builder reads through `loadAll()` so it picks up new ships without further wiring.
3. **`appDirs.getUserDataDir()` is writable on both Desktop and Android targets.** Already proven by `FileShipDesignRepository`; reused unchanged.
4. **Compose-resources `Res.drawable.ic_*` icons render in both Compose Desktop and Compose Android.** Verified across item A and ship-builder; reused unchanged.

## Problem Frame

See origin: `docs/brainstorms/2026-04-26-scenario-builder-requirements.md`. In short: the production demo scene is a hardcoded 5-ship layout; every dev playtest uses the same scenario. Validating combat-balance changes (item C), the slot-system refactor (D), the projectile-interaction model (E), terrain (F), and player-AI markers (G) all need ad-hoc battle composition with arbitrary team sizes and positions. B is the roadmap's force-multiplier for those iterations.

## Requirements Trace

- **R1** — List editor + read-only mini-map preview. List rows are the only edit surface; mini-map renders positions live but accepts no drag. Hover/focus a row → marker highlights. Empty-state guidance. Broken-slot row indicator. Lands in Units 4 (VM) and 5 (UI).
- **R2** — App-data persistence with named scenarios; sanitiser; **confirm-on-overwrite** dialog (differs from item A's silent overwrite — scenarios are invisible state without a canvas the dev can rebuild from). Lands in Units 1 (sanitiser helper) and 3 (FileScenarioRepository).
- **R3** — Library sourced from `DefaultShipDesignLoader.loadAll()` at runtime. Lands in Unit 4.
- **R4** — Stale-reference handling: load best-effort, mark broken slots, **skip-broken-at-launch with banner**. Lands in Unit 4 (VM) and 5 (UI).
- **R5** — Empty-state with always-available "Use demo defaults" button per team panel; reads `DemoScenarioPreset.SLOTS`. Lands in Unit 1 (constant) and 5 (UI).
- **R6** — Canonical demo layout extracted to a shared `DemoScenarioPreset` constant; both `startDemoScene` and the preset path read it. Acceptance check via Unit 7. Lands in Units 1 (constant), 2 (refactor), and 7 (acceptance test).
- **R7** — Launch-and-return preserves in-progress config; `PendingScenario` singleton holder is the launch-path seam, **read-and-clear** in `GameVM.init` so the production Play path that follows never sees a stale value. The persistent "what does restart re-run?" answer is `GameStateManager.lastLaunched`, not `PendingScenario`. Compose Nav back-stack scoping preserves the `ScenarioBuilderVM` instance; close/nav-Back discards unsaved. Lands in Units 2 (holder + GameVM consume-on-read) and 4 (VM writes).
- **R8** — Desktop-dev-only via new `DevToolsGate`; today wraps `RepoExporter.isAvailable`; surfaces a single "Scenario Builder (dev)" text link appended to the existing landing-screen button column (no menu shell, no layout restructure). Lands in Unit 6.
- **R9** — Validation at launch is **proactive**: the Launch button is disabled when either team has zero non-broken slots; tooltip / inline message explains why. Pressing Launch never fails — it can only fire when the scenario is valid. Lands in Unit 4.

(see origin: `docs/brainstorms/2026-04-26-scenario-builder-requirements.md`)

## Scope Boundaries

- **Player-facing mission picker.** Out of scope; CCD-3 defers it.
- **Composition-core separability requirement.** Dropped at brainstorm time. Data classes live in `:components:game-core:api` because that's the domain home, not for future-mission-picker reuse.
- **Terrain placement.** Deferred until item F. The `Scenario` schema reserves a `terrain: List<TerrainConfig> = emptyList()` field as a forward-compat slot; v1 UI doesn't expose it. (See "Strategic decision" note below — the implementer may consult it before committing the schema.)
- **More than two teams.** `TEAM_PLAYER` / `TEAM_ENEMY` constants stay as the only team values.
- **Per-scenario AI tuning.** Per-ship `withAI` Boolean is the only AI control.
- **Camera/zoom configuration as part of a scenario.** Item C owns scale and zoom defaults.
- **Committing scenarios to the repo.** App-data only; CCD-3.
- **Editing a running scenario.** Once Launch fires, the scenario is locked.
- **Process-death scenario recovery.** Compose Nav back-stack scopes preserve in-progress state across nav transitions; app crash discards it. Acceptable for a dev tool.
- **Drag-to-position on the mini-map.** Dropped at brainstorm time on cost-vs-benefit grounds.
- **Mini-map input debouncing.** A 60Hz position update for ≤10 markers is well within Compose's redraw budget; no debounce needed.

## Context & Research

### Relevant Code and Patterns

- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManager.kt` — current `startDemoScene` with private `SpawnSlot` data class (5 fields: `designName`, `position`, `teamId`, `withAI`, `drawOrder`) + 5 hardcoded slots + spawn-gate loop. Refactor target for Unit 2.
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/ui/GameVM.kt:104-106` — current unconditional `init { viewModelScope.launch { gameStateManager.startDemoScene() } }`. Modified in Unit 2 to consume-on-read from `PendingScenario`.
- `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/data/FileShipDesignRepository.kt` — pattern for `FileScenarioRepository` (`FileStorage` + sanitizer + JSON). Sanitiser regex `[^a-zA-Z0-9_\\- ]` → `_` is lifted to a shared helper in Unit 1.
- `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/ShipBuilderVM.kt` + `ShipBuilderScreen.kt` — overall ViewModel + Screen pattern (intents, state, side effects, `HandleSideEffect`, `Scaffold` + `SnackbarHost`). Mirror for the scenario builder.
- `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/composables/PartsPanel.kt` — `LoadDesignDialog` shape mirrored for the scenario load dialog.
- `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/composables/ItemAttributesPanel.kt` — `ExposedDropdownMenuBox` pattern with `MenuAnchorType.PrimaryNotEditable` and `@OptIn(ExperimentalMaterial3Api::class)`. Reused verbatim for `SlotRow`'s ship picker.
- `features/landing/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/landingscreen/ui/LandingScreen.kt` + `LandingVM.kt` — landing-screen entry pattern. New `GoToScenarioBuilder` side effect; `canShowDevTools: Boolean` is a static field on `LandingState`, frozen once at VM init from `DevToolsGate.isAvailable` (mirrors item A's `canExport` pattern in ship-builder — not a `combine`d Flow because the gate doesn't change at runtime).
- `composeApp/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/ui/navigation/LFNavHost.kt` — adds `SCENARIO_BUILDER` `composable(...)` entry.
- `components/shared/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/ui/navigation/LFNavDestination.kt` — adds `const val SCENARIO_BUILDER = "scenario_builder"`. (`LFNavDestination` lives in `:components:shared:api`, not `:composeApp`.)
- `composeApp/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/di/AppComponent.kt` — provides `RepoExporter` already (item A). Adds providers for `DevToolsGate`, `ScenarioRepository`, `PendingScenario`, `ScenarioBuilderScreenEntry`. Adds `:features:scenario-builder:api` to deps.
- `features/ship-builder/api/src/commonMain/kotlin/.../shipbuilder/ShipBuilderScreenEntry.kt` — typealias pattern for the new `ScenarioBuilderScreenEntry`.
- `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ShipDesign.kt` — `formatVersion` + `CURRENT_VERSION` companion pattern. `Scenario` mirrors it.
- `components/shared/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/utils/export/RepoExporter.kt` — item A's `RepoExporter.isAvailable`. `DevToolsGate` wraps it today.
- `settings.gradle.kts` — adds `:features:scenario-builder:api` and `:features:scenario-builder:impl`.

### Institutional Learnings

- `docs/solutions/best-practices/plan-deepening-patterns-2026-04-17.md` — sealed-type design and Result-loop error handling. No directly-applicable scenario-builder prior art.

### External References

None. Codebase patterns cover the work fully.

## Key Technical Decisions

- **`Scenario` data class lives in `:components:game-core:api`** alongside `ShipDesign`, with `companion object { const val CURRENT_VERSION: Int = 1 }`. New schema; v1.
- **`SpawnSlotConfig` replaces the private `SpawnSlot` directly.** Verified: `GameStateManager`'s private `SpawnSlot(designName, position, teamId, withAI, drawOrder)` has only those five public-domain fields and no internal-only state. Replacing it is the cleaner option (vs. mapping between two types) and eliminates a permanent translation seam. Unit 2 deletes the private class.
- **`DemoScenarioPreset` is a Kotlin constant**, not a JSON file. Lives in `:components:game-core:api` next to `Scenario`. Both `GameStateManager.startDemoScene` and the scenario builder's "Use demo defaults" button read `DemoScenarioPreset.SLOTS`. Unit 7 asserts these two consumers pull identical lists (R6).
- **`PendingScenario` is a `@Singleton @Inject` holder** in `:components:game-core:api` exposing `var slots: List<SpawnSlotConfig>?`. **Consume-on-read semantics:** scenario-builder writes before navigating to Game; `GameVM.init` reads-and-nulls it in one step. The persistent answer to "what does `restartScene` re-run?" lives in `GameStateManager.lastLaunched`, not in `PendingScenario`. This eliminates the failure mode where the production Play path (no scenario builder) silently replays a previous custom scenario because the holder was never cleared.
- **`DevToolsGate` is a tiny abstraction in `:components:shared:api`**. Today the impl wraps `RepoExporter.isAvailable`; future dev tools that don't need repo-write can depend on `DevToolsGate` directly. AppComponent's `@Provides` returns `DevToolsGateImpl(repoExporter)`.
- **Sanitiser is lifted to `:components:shared:api`** as a top-level `fun sanitizeFilenameStem(name: String): String`. Both `FileShipDesignRepository.sanitizeName` and `FileScenarioRepository` route through it. Lift is in Unit 1; `FileShipDesignRepository` switches over in the same unit (small mechanical change, no behavioural delta).
- **`FileScenarioRepository` writes to `userDataDir/scenarios/`** following the `FileShipDesignRepository` pattern. New `ScenarioRepository` interface lives in `:components:game-core:api`; impl in `:features:scenario-builder:impl`.
- **`ScenarioBuilderVM` is constructor-injected with** `ScenarioRepository`, `DefaultShipDesignLoader`, `PendingScenario`, plus the standard side-effect/state plumbing. Reads the bundled-ship list once in `init` via `viewModelScope.launch`; on completion, recomputes `brokenSlotIds` against any already-loaded `slots` (race fix: a load that arrives before `loadAll()` resolves would otherwise mark every slot broken).
- **Mini-map is a `Canvas` Compose component** rendering markers as filled circles. `Modifier.pointerInput` is **not** used — the canvas is read-only. The list's StateFlow drives marker positions live as x/y inputs change.
- **`GameStateManager.startScene(slots: List<SpawnSlotConfig>)` is the new public entry**; `startDemoScene()` is reduced to literally calling `startScene(DemoScenarioPreset.SLOTS)`. The spawn-gate loop, design-cache logic, input-controller wiring, and `_gameResult` reset are unchanged.
- **`GameStateManager.restartScene` re-runs the most recently launched scene**: stash the launched `List<SpawnSlotConfig>` in a private `lastLaunched` field; `restartScene` re-calls `startScene(lastLaunched ?: DemoScenarioPreset.SLOTS)`. `clearScene` does **not** clear `lastLaunched` — that's intentional, so a restart-after-clear still re-runs the scenario. Production Play path launches `DemoScenarioPreset` and re-runs it identically — observable behaviour unchanged.
- **Launch path mechanism** (R7): scenario-builder writes to `PendingScenario.slots`, emits `LaunchScenario` side effect, screen handles by `navController.navigate(LFNavDestination.SCENARIO_BUILDER → GAME)` without `popUpTo`. The default `composable(...)` entry scopes a `ViewModelStore` per `NavBackStackEntry`, so `ScenarioBuilderVM` survives while Game is on top. Return is `popBackStack()`.
- **Landing-screen link** is a single `LFTextButton` appended to the existing landing button column, gated on `state.canShowDevTools`. No layout restructuring (no `Box` / `BottomStart` repositioning).

## Open Questions

### Resolved During Planning

- **Scenario JSON schema** → `@Serializable data class Scenario(val name: String, val formatVersion: Int = CURRENT_VERSION, val slots: List<SpawnSlotConfig>, val terrain: List<TerrainConfig> = emptyList())`. `TerrainConfig` is a placeholder `data class TerrainConfig(val placeholder: String = "")` for v1 — just enough to reserve the schema slot. Item F replaces it. (Strategic decision the implementer may revisit: see note at end of this section.)
- **Sanitiser placement** → lifted to `:components:shared:api/utils/Sanitisation.kt` as a top-level `fun sanitizeFilenameStem(name: String): String`. `FileShipDesignRepository` switches over in Unit 1.
- **`PendingScenario` DI seam** → `:components:game-core:api`. `@Singleton @Inject class PendingScenario { var slots: List<SpawnSlotConfig>? = null }`. Consume-on-read semantics in `GameVM.init`.
- **Snap-to-grid step size** → moot; drag is dropped from R1.
- **Launch-side-effect shape** → `ScenarioBuilderSideEffect.LaunchScenario` (data object) + `NavigateBack` + standard `ShowToast` for save confirmations.
- **Landing gate read** → LandingVM injects `DevToolsGate` and reads `isAvailable` once in `init`, freezing into a static `state.canShowDevTools` field. Mirrors item A's `RepoExporter.isAvailable` → `state.canExport` pattern in `ShipBuilderVM`.
- **Compose Nav scoping for `ScenarioBuilderVM`** → default `composable(LFNavDestination.SCENARIO_BUILDER) { ... }` with no `popUpTo`. Compose Navigation 2.x scopes a `ViewModelStore` per `NavBackStackEntry`; the entry remains in the back stack while Game is on top, so the `ScenarioBuilderVM` instance is retained.

### Deferred to Implementation

- **Exact scenario-name validation rules** beyond sanitisation (max length, leading-trailing whitespace handling, reserved names). Trivially decidable mid-implementation; the existing ship-builder's `RenameDesign` flow has the answer for free.
- **Mini-map marker visual size and color tokens.** Implementer picks something readable against the dark theme (likely `MaterialTheme.colorScheme.primary` for player, `colorScheme.error` for enemy, ~6dp radius). Easy to tune from screenshots.
- **Confirm-overwrite dialog visual** — `AlertDialog` is the default, mirroring `LoadDesignDialog`.
- **Team-panel width.** Recommended 200dp per panel based on the slot-row contents (dropdown + two numeric fields + checkbox + delete icon). Implementer can adjust after first render if cramped.

### Strategic decision available to implementer

The `Scenario.terrain` placeholder field is in v1 because origin Decision #9 chose to reserve the slot. An alternative is to drop the field from v1 entirely and let Item F bump `formatVersion` to 2 with a one-shot migration. The cost saved is small (one trivial field + a placeholder type), but it removes a YAGNI-flavoured artefact from the schema for the months between B and F landing. Either approach is defensible; the rest of the plan doesn't depend on which is chosen.

## Output Structure

```
features/scenario-builder/
├── api/
│   ├── build.gradle.kts                                                                — Unit 6
│   └── src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/scenariobuilder/
│       └── ScenarioBuilderScreenEntry.kt                                               — Unit 6
└── impl/
    ├── build.gradle.kts                                                                — Unit 3
    └── src/
        ├── commonMain/kotlin/jez/lastfleetprotocol/prototype/components/scenariobuilder/
        │   ├── data/
        │   │   └── FileScenarioRepository.kt                                           — Unit 3
        │   └── ui/
        │       ├── ScenarioBuilderScreen.kt                                            — Unit 5
        │       ├── ScenarioBuilderVM.kt                                                — Unit 4
        │       ├── composables/
        │       │   ├── ScenarioMiniMap.kt                                              — Unit 5
        │       │   ├── SlotRow.kt                                                      — Unit 5
        │       │   └── LoadScenarioDialog.kt                                           — Unit 5
        │       └── entities/
        │           ├── ScenarioBuilderIntent.kt                                        — Unit 4
        │           ├── ScenarioBuilderSideEffect.kt                                    — Unit 4
        │           └── ScenarioBuilderState.kt                                         — Unit 4
        └── commonTest/kotlin/jez/lastfleetprotocol/prototype/components/scenariobuilder/
            ├── data/
            │   └── FileScenarioRepositoryTest.kt                                       — Unit 3
            └── ui/
                └── ScenarioBuilderVMTest.kt                                            — Unit 4

components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/
└── scenarios/
    ├── Scenario.kt                  (Scenario, SpawnSlotConfig, ScenarioRepository, TerrainConfig) — Unit 1
    ├── DemoScenarioPreset.kt                                                           — Unit 1
    └── PendingScenario.kt                                                              — Unit 2b

components/game-core/api/src/jvmTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/scenarios/
└── DemoScenarioPresetParityTest.kt                                                     — Unit 7

components/shared/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/utils/
├── export/
│   └── DevToolsGate.kt                                                                 — Unit 6
└── Sanitisation.kt                                                                     — Unit 1

components/design/src/commonMain/composeResources/drawable/
└── ic_warning.xml                                                                      — Unit 5
```

## High-Level Technical Design

> *This illustrates the intended approach and is directional guidance for review, not implementation specification. The implementing agent should treat it as context, not code to reproduce.*

```mermaid
sequenceDiagram
    participant Dev as Dev
    participant SB as ScenarioBuilder<br/>(Screen + VM)
    participant Repo as FileScenarioRepository
    participant Pending as PendingScenario
    participant Game as Game<br/>(VM + StateManager)

    Dev->>SB: clicks "Scenario Builder (dev)" link on landing
    Note over SB: VM loads library via DefaultShipDesignLoader
    Dev->>SB: edits slots / loads saved scenario
    SB->>Repo: save (on Save click; confirm-on-overwrite)
    Repo-->>SB: persisted to userDataDir/scenarios/
    Dev->>SB: clicks Launch (only enabled when both teams have ≥1 valid slot)
    SB->>Pending: writes filtered slots
    SB->>Game: navigate(GAME) — back-stack pushes Game atop SB
    Game->>Pending: reads-and-clears slots; calls startScene(slots)
    Game->>Game: caches lastLaunched for future restartScene
    Note over SB: ScenarioBuilderVM remains alive in NavBackStackEntry-scoped ViewModelStore
    Dev->>Game: clicks Exit / returns
    Game->>SB: popBackStack — SB visible again with state intact
```

## Implementation Units

- [x] **Unit 1: Scenario data model + sanitiser helper + DemoScenarioPreset**

**Goal.** Land the pure-domain types and shared sanitiser. End state: `:components:game-core:api` and `:components:shared:api` build cleanly across JVM and Android with the new types in place; `FileShipDesignRepository` switches to the lifted sanitiser without behavioural change. No other consumer yet.

**Requirements.** R2 (sanitiser foundation), R5 + R6 (DemoScenarioPreset), and the data-model foundation for R1/R3/R4/R7/R9.

**Dependencies.** None.

**Files:**
- Create: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/scenarios/Scenario.kt` (`Scenario`, `SpawnSlotConfig`, `TerrainConfig`, `ScenarioRepository` interface, `Scenario.CURRENT_VERSION`)
- Create: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/scenarios/DemoScenarioPreset.kt` (`DemoScenarioPreset.SLOTS: List<SpawnSlotConfig>`)
- Create: `components/shared/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/utils/Sanitisation.kt` (top-level `fun sanitizeFilenameStem(name: String): String`)
- Create: `components/shared/api/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/utils/SanitisationTest.kt`
- Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/data/FileShipDesignRepository.kt` (replace private `sanitizeName` with the shared helper; same regex, no behavioural change)
- Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/data/FileItemLibraryRepository.kt` (same — if it has its own copy; verify before modifying)

**Approach.**
- `Scenario` and `SpawnSlotConfig` are `@Serializable data class`es. `Scenario.formatVersion` defaults to `CURRENT_VERSION = 1`. `SpawnSlotConfig` has the same five fields as the existing private `SpawnSlot` (`designName: String`, `position: SerializableSceneOffset`, `teamId: String`, `withAI: Boolean`, `drawOrder: Float`); the position uses the existing `SerializableSceneOffset` wrapper at `components/game-core/api/.../shipdesign/SceneOffsetSerializer.kt` (or its sibling — check before adding a new serializer).
- `TerrainConfig` is `@Serializable data class TerrainConfig(val placeholder: String = "")` — a forward-compat shell that Item F replaces.
- `DemoScenarioPreset.SLOTS` is `listOf(SpawnSlotConfig(...))` × 5 with the same designs/positions/teams/AI flags/drawOrders currently inlined in `GameStateManager.startDemoScene`.
- The shared sanitiser preserves the existing regex (`[^a-zA-Z0-9_\\- ]` → `_`). The lift is mechanical; both `FileShipDesignRepository` and the future `FileScenarioRepository` route through it.

**Patterns to follow.**
- `ShipDesign.kt` — `formatVersion` + `CURRENT_VERSION` + `companion object` pattern.
- `FileShipDesignRepository.sanitizeName` — exact regex.

**Test scenarios:**
- *Happy path (Sanitisation):* `"Heavy Cruiser"` → `"Heavy Cruiser"` (spaces and case preserved per the existing rule); `"Mk.II"` → `"Mk_II"`; `"a/b\\c:d"` → `"a_b_c_d"`.
- *Edge case (Sanitisation):* empty string → empty string; string containing only `_-` → unchanged.
- *Edge case (Scenario serialization):* a `Scenario` with empty `slots` and empty `terrain` round-trips through `Json.encodeToString` / `decodeFromString` to an equal value.
- *Happy path (DemoScenarioPreset):* `DemoScenarioPreset.SLOTS.size == 5`. The list contains 2 player slots and 3 enemy slots. Each slot's `designName` matches one of `DefaultShipDesignLoader.SHIP_FILENAMES`.

**Verification.**
- `./gradlew :components:game-core:api:allTests` and `:components:shared:api:allTests` pass.
- `./gradlew :features:ship-builder:impl:allTests` still passes (verifies the sanitiser lift is behaviour-preserving).
- `./gradlew build` succeeds.

---

- [x] **Unit 2a: GameStateManager refactor**

**Goal.** Replace the private `SpawnSlot` data class with the public `SpawnSlotConfig`; expose a public `startScene(slots: List<SpawnSlotConfig>)` entry; reduce `startDemoScene` to literally `startScene(DemoScenarioPreset.SLOTS)`; add a private `lastLaunched` cache so `restartScene` re-runs the active scene. End state: production demo path's observable behaviour is unchanged; the manager has a public seam for arbitrary slot lists, but no caller uses it yet (Unit 2b wires the new caller).

**Risk classification.** High-risk Kubriko code per `CLAUDE.md`'s game-runtime guidance. The spawn-gate loop, design cache, input-controller wiring, and ship lifecycle hooks must remain untouched; only the slot-list source changes.

**Requirements.** R6 (single source of truth for demo), R7 (restart re-runs the active scenario).

**Dependencies.** Unit 1.

**Files:**
- Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManager.kt` (delete the private `SpawnSlot` data class; add `fun startScene(slots: List<SpawnSlotConfig>)` public; reduce `startDemoScene` to `startScene(DemoScenarioPreset.SLOTS)`; add private `var lastLaunched: List<SpawnSlotConfig>? = null` and update on each `startScene` call; refactor `restartScene` to call `startScene(lastLaunched ?: DemoScenarioPreset.SLOTS)`; verify `clearScene` does NOT touch `lastLaunched`)
- Modify: `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManagerTest.kt` (cover `startScene` with custom slots + `restartScene` re-runs the last scenario + `clearScene` preserves `lastLaunched` + `startDemoScene` regression cover)

**Approach.**
- Delete the private `SpawnSlot` data class entirely. The five hardcoded slots inside `startDemoScene` move to `DemoScenarioPreset.SLOTS` (Unit 1). The internal spawn-gate loop is updated to iterate `List<SpawnSlotConfig>` directly. Verified: the private class has no internal-only fields beyond the five public ones, so this is a clean replacement (no mapping seam).
- `lastLaunched` is a private `var` on `GameStateManager` — null until first launch, replaced on each `startScene` call. `restartScene` reads it; if null, falls back to `DemoScenarioPreset.SLOTS` (defensive — shouldn't happen in normal flow).
- `clearScene` resets actor state for a fresh scene but **does not** clear `lastLaunched` — that's intentional, so a result-screen → "Restart" path still re-runs the scenario that was launched.

**Patterns to follow.**
- `GameStateManager.startDemoScene`'s spawn-gate loop is reused verbatim for `startScene` (just iterating the parameter instead of a hardcoded list).
- `GameStateManager` already has `private val playerShips`, `private val enemyShips`, and ship lifecycle wiring — none of those change.

**Test scenarios:**
- *Happy path (startScene):* call `startScene(customSlots)` with 1 player + 1 enemy slot. Both ships spawn; team lists populate.
- *Happy path (restartScene re-runs):* call `startScene(customSlots)`; spawn N ships; call `clearScene` + `restartScene`; same slot list spawns again.
- *Happy path (startDemoScene unchanged):* call `startDemoScene()`; same 5 ships spawn at the same canonical positions as before the refactor (regression check).
- *Edge case (clearScene preserves lastLaunched):* call `startScene(customSlots)`; call `clearScene`; assert `restartScene` still re-runs `customSlots`, not the demo.
- *Edge case (restartScene with no prior startScene):* fresh manager → `restartScene` falls back to `DemoScenarioPreset.SLOTS`.
- *Integration:* the existing spawn-gate loop's behaviour (skip unflightworthy, log gate failures) is unchanged for both demo and custom-scenario paths.

**Verification.**
- `./gradlew :features:game:impl:allTests` passes; existing `GameStateManagerTest` cases continue to pass.
- Manual run (post-Unit 6 wiring): `./gradlew :composeApp:run`, click Play, demo runs identically to pre-refactor.

---

- [x] **Unit 2b: `PendingScenario` holder + GameVM consume-on-read**

**Goal.** Introduce the launch-path seam: a `@Singleton` holder that scenario-builder writes to, and a `GameVM.init` that reads-and-clears it in one step before dispatching to `startScene` (custom) or `startDemoScene` (production demo). End state: an outside caller (Unit 4's VM) can write to `PendingScenario` and Game launches with custom slots; the production Play path that follows is guaranteed to see null.

**Requirements.** R7 (consume-on-read launch path).

**Dependencies.** Unit 2a.

**Files:**
- Create: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/scenarios/PendingScenario.kt`
- Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/ui/GameVM.kt` (constructor-inject `PendingScenario`; in `init`, read-and-clear `pending.slots` in one step — `val slots = pending.slots.also { pending.slots = null }`; call `gameStateManager.startScene(slots)` if non-null else `gameStateManager.startDemoScene()`)
- Modify: `composeApp/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/di/AppComponent.kt` (provide `PendingScenario` as `@Singleton`)
- Modify: `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/ui/GameVMTest.kt` (or create if missing; cover the consume-on-read paths)

**Approach.**
- `PendingScenario` is `@Inject @Singleton class PendingScenario { var slots: List<SpawnSlotConfig>? = null }`. No suspend, no flow — it's a one-shot transport with **consume-on-read** semantics: `GameVM.init` reads and immediately nulls the field. After consumption, `lastLaunched` (in `GameStateManager`, from Unit 2a) is the sole persistent record of "what's currently running."
- `GameVM.init` reads `pending.slots` once, nulls the field, then dispatches: non-null → `startScene(slots)`; null → `startDemoScene()`. The production Play-from-landing path always sees null and runs the demo, regardless of whether the scenario builder was used in this session.

**Patterns to follow.**
- `RepoExporterImpl` (item A) for `@Inject @Singleton` provider shape on `PendingScenario`.

**Test scenarios:**
- *Edge case (PendingScenario consume-on-read, null):* `GameVM.init` with `pending.slots == null` → `startDemoScene` is called. After init, `pending.slots` remains null.
- *Edge case (PendingScenario consume-on-read, non-null):* `GameVM.init` with `pending.slots = customSlots` → `startScene(customSlots)` is called; `startDemoScene` is not called. After init, `pending.slots` is null (consumed).
- *Regression (Play after scenario builder):* set `pending.slots = customSlots`; construct first `GameVM` (consumes); then construct a second `GameVM` (production Play after returning to landing) → `startDemoScene` is called, NOT `startScene(customSlots)`. This pins the consume-on-read fix.

**Verification.**
- `./gradlew :features:game:impl:allTests` passes.
- `./gradlew build` succeeds (DI graph validates with the new `PendingScenario` provider).

---

- [x] **Unit 3: `FileScenarioRepository` + `ScenarioRepository` contract**

**Goal.** Persistence for named scenarios in `userDataDir/scenarios/`. End state: a `ScenarioRepository` instance can save/load/list/delete `Scenario`s by name; tests cover round-trip and sanitisation behaviour.

**Requirements.** R2.

**Dependencies.** Unit 1.

**Files:**
- Create: `features/scenario-builder/impl/build.gradle.kts` (mirrors `features/ship-builder/impl/build.gradle.kts` pattern; deps on `:components:game-core:api`, `:components:shared:api`, `:components:design`, kotlinx-serialization, kotlin-inject; `android.namespace = "jez.lastfleetprotocol.prototype.components.scenariobuilder.impl"`)
- Create: `features/scenario-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/scenariobuilder/data/FileScenarioRepository.kt`
- Create: `features/scenario-builder/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/scenariobuilder/data/FileScenarioRepositoryTest.kt`
- Modify: `settings.gradle.kts` (add `:features:scenario-builder:api` and `:features:scenario-builder:impl`)
- (`ScenarioRepository` interface itself was created in Unit 1 inside `Scenario.kt`)

**Approach.**
- `FileScenarioRepository @Inject : ScenarioRepository` mirrors `FileShipDesignRepository` 1:1: a private `Json { prettyPrint = true; ignoreUnknownKeys = true }` instance, a `SCENARIOS_DIRECTORY = "scenarios"` constant, and the four interface methods (`save`, `load`, `listAll`, `delete`) routing through `FileStorage.{saveFile,loadFile,listFiles,deleteFile}` with the lifted `sanitizeFilenameStem`.
- `save(scenario: Scenario)` → encodes JSON → writes to `scenarios/<sanitised-name>.json`.
- `load(name: String): Scenario?` → reads file → decodes JSON; returns null on missing/decode-failure (matches `FileShipDesignRepository.load`'s null-on-error pattern).
- `listAll(): List<String>` → strips `.json` extension, returns names.
- `delete(name: String)` → deletes the sanitised file.

**Patterns to follow.**
- `FileShipDesignRepository.kt` (verbatim shape).
- `features/ship-builder/impl/build.gradle.kts` (verbatim shape, with the new namespace).

**Test scenarios:**
- *Happy path (round-trip):* save a `Scenario(name = "two-vs-two", slots = [...])` → list returns `["two-vs-two"]` → load returns the same `Scenario`.
- *Happy path (sanitisation):* save `Scenario(name = "Mk.II Tests")` → list returns `["Mk.II Tests"]` (the dev-facing name preserves the original) **but** the on-disk filename is `Mk_II Tests.json` (per the sanitiser). Load by name `"Mk.II Tests"` returns the saved scenario.
- *Edge case (overwrite):* save the same name twice with different content → load returns the second content.
- *Edge case (delete):* save → delete → list returns empty; load returns null.
- *Edge case (load missing):* load a name that was never saved → returns null.
- *Edge case (corrupted JSON):* manually plant invalid JSON at the expected path → load returns null without throwing.

**Verification.**
- `./gradlew :features:scenario-builder:impl:allTests` passes.
- `./gradlew build` succeeds (confirms settings.gradle.kts module wiring is correct).

---

- [x] **Unit 4: `ScenarioBuilderVM` + intents/state/side-effects (test-first)**

**Goal.** The MVI core of the scenario builder: state holds the in-progress scenario, intents drive edits and persistence, side effects route to the screen layer. End state: VM tests cover all intent paths against fakes for `ScenarioRepository`, `DefaultShipDesignLoader`, and `PendingScenario`; the VM compiles cleanly and is wireable from the screen.

**Requirements.** R1 (slot rows + state), R2 (save/load + confirm-overwrite), R3 (library source), R4 (broken-slot detection at load time), R5 (use-demo-defaults), R7 (write to `PendingScenario`), R9 (proactive Launch validation).

**Dependencies.** Units 1, 2a, 2b, 3.

**Execution note.** Test-first. Each new intent has a clean fake-driven test before the wiring is written.

**Files:**
- Create: `features/scenario-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/scenariobuilder/ui/entities/ScenarioBuilderIntent.kt`
- Create: `features/scenario-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/scenariobuilder/ui/entities/ScenarioBuilderState.kt`
- Create: `features/scenario-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/scenariobuilder/ui/entities/ScenarioBuilderSideEffect.kt`
- Create: `features/scenario-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/scenariobuilder/ui/ScenarioBuilderVM.kt`
- Create: `features/scenario-builder/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/scenariobuilder/ui/ScenarioBuilderVMTest.kt`

**Approach.**
- **Intents:** `AddSlot(team: TeamId)`, `RemoveSlot(slotId: String)`, `UpdateSlotShip(slotId, designName)`, `UpdateSlotPosition(slotId, x, y)`, `ToggleSlotAi(slotId)`, `RenameScenario(name: String)`, `SaveClicked`, `LoadDialogOpenClicked`, `LoadDialogDismissed`, `LoadScenario(name: String)`, `UseDemoDefaults(team: TeamId)` (per-team — replaces only that team's slots), `LaunchClicked`, `BackClicked`, `ConfirmOverwrite`, `CancelOverwrite`.
- **State:** `ScenarioBuilderState(designName: String, slots: List<SlotEntry>, savedScenarios: List<String>, showLoadDialog: Boolean, showOverwriteConfirm: Pair<String, Scenario>?, brokenSlotIds: Set<String>, libraryShipNames: List<String>, canShowLoadDialog: Boolean)`. `SlotEntry` carries `(id, team, designName, x, y, withAI)`. Broken slots have a `designName` not in `libraryShipNames`. Plus a derived `val canLaunch: Boolean get() = libraryReady && slots.any { it.team == PLAYER && it.id !in brokenSlotIds } && slots.any { it.team == ENEMY && it.id !in brokenSlotIds }` for the Launch-button enable state. `libraryReady` flips true once `loadAll()` resolves (so the button doesn't briefly read "ready" with an empty library).
- **Proactive validation (R9):** the Launch button is bound to `state.canLaunch`. There is no `validationMessage` field — invalid configurations cannot reach `LaunchClicked` because the button is disabled. A small inline "Launch needs ≥1 ship per team" hint appears beside the button when `canLaunch == false`.
- **Side effects:** `LaunchScenario` (signals the screen to navigate to Game; VM has already written `PendingScenario.slots`), `NavigateBack`, `ShowToast(text: String)`.
- **VM init:** launch a coroutine in `viewModelScope` to call `defaultShipDesignLoader.loadAll()`; set `state.libraryShipNames = result.keys.toList()` and `state.libraryReady = true` when it resolves; then **recompute `state.brokenSlotIds`** against any slots already loaded (race fix: a `LoadScenario` that arrived before `loadAll()` resolved would otherwise have flagged every slot broken). Also set `canShowLoadDialog = scenarioRepository.listAll().isNotEmpty()`.
- **Library-update race:** any state transition that updates `slots` or `libraryShipNames` must recompute `brokenSlotIds = slots.filter { it.designName !in libraryShipNames }.map { it.id }.toSet()`.
- **`AddSlot`** appends a new `SlotEntry` with a fresh UUID-style id, `designName = libraryShipNames.firstOrNull() ?: ""` (empty string is treated as broken), default position `(0, 0)`, default `withAI` based on team (player → false, enemy → true), and emits no side effect.
- **`UseDemoDefaults(team)`** replaces `state.slots.filter { it.team == team }` with the per-team subset of `DemoScenarioPreset.SLOTS`. Generates fresh ids; the other team's slots stay.
- **`SaveClicked`** validates the name is non-empty; if `scenarioRepository.listAll().contains(sanitisedName)`, sets `showOverwriteConfirm`; else calls `scenarioRepository.save(currentScenario)` and emits `ShowToast("Saved to scenarios/<name>.json")`.
- **`ConfirmOverwrite`** writes the pending scenario, clears `showOverwriteConfirm`, emits `ShowToast`.
- **`LoadScenario(name)`** calls `scenarioRepository.load(name)`; on success, populates state's `slots` from the loaded `Scenario.slots` (fresh `SlotEntry` ids); broken-slot detection runs against `libraryShipNames`. On null/decode-failure, emits `ShowToast("Failed to load <name>")` and leaves state unchanged.
- **`LaunchClicked`** (only callable when `canLaunch == true`): filter broken slots out, write the resulting `List<SpawnSlotConfig>` to `pendingScenario.slots`, emit `LaunchScenario`. The VM does NOT clear `pendingScenario` after writing — `GameVM.init` consumes-on-read.

**Patterns to follow.**
- `ShipBuilderVM` for intent dispatch shape, side-effect channel use, `viewModelScope.launch` for IO calls.
- `ShipBuilderVMExportTest` (item A) for the side-effect collector pattern with `CoroutineStart.UNDISPATCHED`.

**Test scenarios:**
- *Happy path (AddSlot + RemoveSlot):* state starts with no slots; `AddSlot(player)` × 2 + `AddSlot(enemy)` × 1 → state has 3 slots; `RemoveSlot(id)` removes one. No side effects emitted.
- *Happy path (UpdateSlotShip + UpdateSlotPosition):* slot is added; `UpdateSlotShip(id, "enemy_heavy")` and `UpdateSlotPosition(id, 200, 50)` update fields; the slot's `withAI` is unchanged.
- *Happy path (UseDemoDefaults per team):* `UseDemoDefaults(player)` → state has the 2 player slots from `DemoScenarioPreset.SLOTS`. `UseDemoDefaults(enemy)` adds the 3 enemy slots. Order matches the preset.
- *Happy path (SaveClicked, no collision):* fake repo's `listAll()` returns empty; `RenameScenario("two-vs-two")` + `SaveClicked` → repo's `save()` is called once with the current scenario; `ShowToast` side effect emitted with "Saved to" prefix.
- *Edge case (SaveClicked, collision):* fake repo's `listAll()` returns `["two-vs-two"]`; `SaveClicked` → state's `showOverwriteConfirm` is non-null; no save call yet. `ConfirmOverwrite` → save fires; `CancelOverwrite` → save does not fire and `showOverwriteConfirm` clears.
- *Happy path (LoadScenario):* fake repo returns a `Scenario` with 2 slots both pointing at valid library ships → state's `slots` populated; `brokenSlotIds` is empty; `designName` updated.
- *Edge case (LoadScenario, broken slots):* fake repo returns a `Scenario` with one slot pointing at `designName = "deleted_ship"` not in `libraryShipNames` → `state.brokenSlotIds` contains that slot's id; the slot still appears in `state.slots`.
- *Edge case (LoadScenario, missing):* fake repo's `load("missing")` returns null → state unchanged; `ShowToast` with "Failed to load" prefix emitted.
- *Edge case (library-update race):* `LoadScenario` runs before `loadAll()` resolves (state has slots, empty `libraryShipNames`, `libraryReady = false`); when `loadAll()` resolves, `brokenSlotIds` is recomputed and matches the newly-known library — slots with valid names lose the broken flag.
- *Edge case (canLaunch — proactive validation):* state has 0 player slots → `canLaunch == false`. State has 0 enemy slots → `canLaunch == false`. State has 2 player slots both broken + 1 valid enemy slot → `canLaunch == false`. State has 1 valid player + 1 valid enemy → `canLaunch == true`.
- *Edge case (canLaunch — library not ready):* even with 1 valid slot per team, if `libraryReady == false`, `canLaunch == false` (defensive — broken-slot detection can't run yet).
- *Happy path (LaunchClicked, broken slot skipped):* state has 2 player slots (one broken) + 1 enemy slot → `canLaunch == true` (one valid player slot exists); `LaunchClicked` proceeds; `pendingScenario.slots` contains 1 player + 1 enemy (the broken slot is filtered out); `LaunchScenario` emitted.
- *Happy path (LaunchClicked, writes PendingScenario):* fake `pendingScenario` captures the written list; the captured list contains exactly the non-broken `SpawnSlotConfig`s in slot-list order.
- *Edge case (init populates library):* fake `DefaultShipDesignLoader.loadAll()` returns a 4-key map → state's `libraryShipNames` matches the keys; `libraryReady == true`.

**Verification.**
- `./gradlew :features:scenario-builder:impl:allTests` passes.
- All test scenarios above are present and named clearly.

---

- [x] **Unit 5: `ScenarioBuilderScreen` UI**

**Goal.** The user-facing screen: top bar + two team panels + read-only mini-map. End state: from the dev menu, the dev sees the scenario builder, can compose a 2v2 from scratch, save and load, and click Launch.

**Requirements.** R1 (full UI surface), R4 (broken-slot indicator + banner), R5 ("Use demo defaults" buttons), R9 (Launch button bound to `canLaunch`).

**Dependencies.** Unit 4.

**Files:**
- Create: `features/scenario-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/scenariobuilder/ui/ScenarioBuilderScreen.kt`
- Create: `features/scenario-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/scenariobuilder/ui/composables/SlotRow.kt`
- Create: `features/scenario-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/scenariobuilder/ui/composables/ScenarioMiniMap.kt`
- Create: `features/scenario-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/scenariobuilder/ui/composables/LoadScenarioDialog.kt`
- Create: `components/design/src/commonMain/composeResources/drawable/ic_warning.xml` (a simple Material warning triangle vector — used by `SlotRow` to flag broken slots and by the "broken slots will be skipped" banner)
- Modify: `components/design/src/commonMain/composeResources/values/strings.xml` (new strings: `scenario_builder_title`, `scenario_player_team`, `scenario_enemy_team`, `scenario_add_slot`, `scenario_use_demo_defaults`, `scenario_save`, `scenario_load`, `scenario_launch`, `scenario_launch_disabled_hint`, `scenario_remove_slot`, `scenario_x_position`, `scenario_y_position`, `scenario_ai_toggle`, `scenario_broken_slot_banner`, `scenario_load_dialog_title`, `scenario_no_saved_scenarios`, `scenario_overwrite_confirm_title`, `scenario_overwrite_confirm_message`, `scenario_minimap_empty`, `scenario_minimap_world_bounds_label`)
- Modify: `components/design/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/ui/resources/LFRes.kt` (register the new strings + `ic_warning`)

**Approach.**
- Top-level `Scaffold` with `SnackbarHost` (mirrors `ShipBuilderScreen`'s post-export wiring).
- **Top bar (full width):** `Row` with scenario name `OutlinedTextField` (flex), `LFTextButton`s for Save / Load / Launch. Launch is `enabled = state.canLaunch`. When disabled, an inline hint text (`scenario_launch_disabled_hint`) renders next to it. Plus a back icon for `BackClicked`.
- **Body:** horizontal `Row(Modifier.weight(1f))`. Left = `Column` with two team panels (each ~200dp wide); right = `Box(Modifier.weight(1f))` containing the mini-map.
- **Team panel** (`ScenarioTeamPanel`, inline composable inside `ScenarioBuilderScreen.kt`): collapsible-style header (player / enemy) + slot list (one `SlotRow` per slot, vertically stacked) + "Add Slot" button + "Use Demo Defaults" button. Panel width fixed at 200dp.
- **`SlotRow` (200dp panel; vertical layout to keep readable at that width):**
  - Row 1: ship-name `ExposedDropdownMenuBox` (full width) + remove-icon button at end. Use the existing `ItemAttributesPanel` ExposedDropdownMenuBox pattern verbatim — `MenuAnchorType.PrimaryNotEditable` on the anchor + `@OptIn(ExperimentalMaterial3Api::class)` on the host composable.
  - Row 2: x position `OutlinedTextField` (numeric) + y position `OutlinedTextField` (numeric), each ~50% width.
  - Row 3: `Checkbox` for AI + label.
  - When `state.brokenSlotIds.contains(slotEntry.id)`: prepend `Res.drawable.ic_warning` icon to Row 1, dropdown border tinted red, x/y inputs `enabled = false`. Tooltip-text or label below row reads `scenario_broken_slot_banner`-equivalent inline copy.
- **`ScenarioMiniMap`:** Compose `Canvas` rendering a dashed rectangle for the world bounds (`±500` SceneUnits → maps to canvas size); for each non-broken slot, draws a filled circle at the linearly-mapped (x, y) position. Player team = `MaterialTheme.colorScheme.primary`; enemy team = `MaterialTheme.colorScheme.error`. Empty-state: dashed rectangle + centred guidance text "Add slots to position ships." No `pointerInput` — fully read-only. Subscribes to the VM's StateFlow via the `state` parameter; positions update live.
- **Broken-slot banner:** when `state.brokenSlotIds.isNotEmpty()`, `Card` with `ic_warning` + text "N broken slot(s) will be skipped at launch." Rendered above the team-panel column.
- **Load dialog (`LoadScenarioDialog`):** `AlertDialog` mirroring the ship-builder's `LoadDesignDialog`. List of `state.savedScenarios`; click to dispatch `LoadScenario(name)`; dismiss → `LoadDialogDismissed`. Empty-state shows `scenario_no_saved_scenarios` text.
- **Overwrite confirm dialog:** triggered by `state.showOverwriteConfirm != null`. `AlertDialog` with title `"Overwrite scenario \"<name>\"?"` and Confirm/Cancel buttons mapping to `ConfirmOverwrite` / `CancelOverwrite` intents.

**Patterns to follow.**
- `ShipBuilderScreen` for screen scaffold, `HandleSideEffect` dispatch (NavigateBack → `popBackStack()`; LaunchScenario → `navigate(LFNavDestination.GAME)` (no `popUpTo`); ShowToast → `snackbarHostState.showSnackbar`).
- `ItemAttributesPanel.kt` for `ExposedDropdownMenuBox` shape with `MenuAnchorType.PrimaryNotEditable` and the `@OptIn(ExperimentalMaterial3Api::class)` annotation.
- `LoadDesignDialog` (in `ShipBuilderScreen.kt`) for the load-dialog shape.
- `PartsPanel` for the per-row icon-button rendering (`LFIconButton.size(32.dp)`).

**Test expectation:** none at the unit-test level — UI-only with no compose-ui-test infrastructure in this repo (matches Item A's Unit 5 plan-time precedent). Verification is via manual run; the VM tests in Unit 4 cover the underlying state and intent-dispatch logic.

**Verification.**
- `./gradlew :features:scenario-builder:impl:compileKotlinJvm` and `:compileDebugKotlinAndroid` succeed.
- `./gradlew build` succeeds.
- Manual run from `./gradlew :composeApp:run` (after Unit 6 wiring): the screen is reachable, the dev can compose a scenario, save/load, click Launch.

---

- [x] **Unit 6: `DevToolsGate` + landing-screen wiring + nav + AppComponent**

**Goal.** Make the scenario builder reachable. End state: on Desktop with `lfp.repo.root` set, the landing screen shows a "Scenario Builder (dev)" link appended to the existing button column; clicking it navigates to the scenario builder; on Android (or Desktop without the gate open), no link.

**Requirements.** R8 (DevToolsGate + landing link + nav).

**Dependencies.** Units 4, 5.

**Files:**
- Create: `components/shared/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/utils/export/DevToolsGate.kt` (interface + default impl that wraps `RepoExporter`)
- Create: `features/scenario-builder/api/build.gradle.kts`
- Create: `features/scenario-builder/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/scenariobuilder/ScenarioBuilderScreenEntry.kt` (typealias)
- Modify: `composeApp/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/ui/navigation/LFNavHost.kt` (add `composable(LFNavDestination.SCENARIO_BUILDER) { scenarioBuilderScreen(navController) }`)
- Modify: `components/shared/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/ui/navigation/LFNavDestination.kt` — add `const val SCENARIO_BUILDER = "scenario_builder"`
- Modify: `features/landing/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/landingscreen/ui/LandingVM.kt` (constructor-inject `DevToolsGate`; add `canShowDevTools: Boolean` as a static field on `LandingState`, initialised once in `init` from `devToolsGate.isAvailable`; add `ScenarioBuilderClicked` intent and `GoToScenarioBuilder` side effect)
- Modify: `features/landing/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/landingscreen/ui/LandingState.kt` (or wherever the landing state is defined — verify) — add `canShowDevTools` field
- Modify: `features/landing/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/landingscreen/ui/LandingScreen.kt` (handle the new side effect; **append** the `LFTextButton` to the existing button column inside the existing layout — no `Box` / `BottomStart` repositioning. Wrap in `if (state.canShowDevTools)`.)
- Modify: `composeApp/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/di/AppComponent.kt` (provide `DevToolsGate`, `ScenarioRepository`, `ScenarioBuilderScreenEntry`; add `:features:scenario-builder:api` to `composeApp` deps)
- Modify: `composeApp/build.gradle.kts` (add `:features:scenario-builder:api` and `:features:scenario-builder:impl` to `commonMain.dependencies`)

**Approach.**
- `DevToolsGate` is `interface DevToolsGate { val isAvailable: Boolean }`. Default impl: `class DevToolsGateImpl @Inject constructor(private val repoExporter: RepoExporter) : DevToolsGate { override val isAvailable: Boolean get() = repoExporter.isAvailable }`. Today the gate equals item A's gate; future dev tools that don't need repo-write can switch to a different impl without restructuring the consumer side.
- `LandingVM` injects `DevToolsGate`; reads `isAvailable` once at construction; sets `internalState = internalState.copy(canShowDevTools = devToolsGate.isAvailable)`. **Static** — not a `combine`d Flow — because the gate doesn't change at runtime within a process. Mirrors item A's `canExport` pattern in `ShipBuilderState`.
- `LandingScreen` adds the link via `if (state.canShowDevTools) { LFTextButton(text = "Scenario Builder (dev)", onClick = { eventHandler.accept(LandingIntent.ScenarioBuilderClicked) }) }` placed **inside the existing button column**, not a new `Box` overlay. The exact position (above/below existing buttons) is up to the implementer to keep the column reading naturally; below is the natural choice.
- `LFNavHost` gains `composable(LFNavDestination.SCENARIO_BUILDER) { scenarioBuilderScreen(navController) }`. Default `composable(...)` semantics scope a `ViewModelStore` per `NavBackStackEntry`; the entry remains in the back stack while Game is on top of it (no `popUpTo` is used on the Launch navigation), so the `ScenarioBuilderVM` instance is retained. Return is `popBackStack()`.
- `AppComponent` provides `DevToolsGate` (`DevToolsGateImpl` constructor-resolves `RepoExporter`), `ScenarioRepository` (`FileScenarioRepository`), `ScenarioBuilderScreenEntry` (the screen composable as a typed function reference). `composeApp` adds the new module deps.

**Patterns to follow.**
- `RepoExporter` provider in `AppComponent` (item A) for the `DevToolsGate` provider shape.
- `ShipBuilderScreenEntry` and `LandingScreen.kt`'s `GoToShipBuilder` side effect (or whichever existing nav side effect is used) for the new `ScenarioBuilderScreenEntry` and `GoToScenarioBuilder`.

**Test scenarios:**
- *Test expectation:* none at the unit-test level for the wiring itself — it's plumbing best verified by running the app.

**Verification.**
- `./gradlew build` succeeds.
- Manual run: from `./gradlew :composeApp:run`, the landing screen shows the "Scenario Builder (dev)" link; clicking it opens the scenario builder. From an Android emulator (or with `lfp.repo.root` cleared), the link is absent.

---

- [x] **Unit 7: `DemoScenarioPreset` parity + production-demo unchanged**

**Goal.** Lock the discipline. Tests assert that (a) the production `startDemoScene` path produces the same `SpawnSlotConfig` list as the scenario-builder "Use demo defaults" path, and (b) the canonical demo's positional and identity fields haven't drifted from a fixture.

**Requirements.** R6 (single source of truth).

**Dependencies.** Units 1, 2a.

**Files:**
- Create: `components/game-core/api/src/jvmTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/scenarios/DemoScenarioPresetParityTest.kt` (the `jvmTest` source set already exists in `components/game-core/api/build.gradle.kts` from item A Unit 6).

**Approach.**
- `DemoScenarioPreset.SLOTS` is the assertion target. The test:
  - Asserts `SLOTS.size == 5` and `SLOTS.count { it.teamId == TEAM_PLAYER } == 2`, `count { it.teamId == TEAM_ENEMY } == 3`.
  - Asserts each slot's `designName` matches the corresponding canonical name from the pre-refactor `startDemoScene` (`player_ship`, `player_ship`, `enemy_light`, `enemy_medium`, `enemy_heavy`).
  - Asserts each slot's `position` matches the canonical positions (player at `(-300, ±50)`, enemy spread at `(300, -120) / (350, 0) / (300, 120)` — verify exact values from the pre-refactor source before pinning).
  - Asserts the AI flags match (player `withAI = false`, enemy `withAI = true`).

**Patterns to follow.**
- `BundledAssetVersionTest` (item A) for the `jvmTest` shape and the assertion style.

**Test scenarios:**
- *Happy path:* `DemoScenarioPreset.SLOTS` matches the canonical 5-ship layout in size, ordering, designs, positions, teams, and AI flags.
- *Drift detection:* a deliberate manual edit of one position value in `DemoScenarioPreset` fails the test. (Reverted before commit; this is a confidence check on the test's mechanism.)

**Verification.**
- `./gradlew :components:game-core:api:allTests` passes.
- `./gradlew :composeApp:run`, click Play → demo runs identically to pre-refactor (a final manual sanity check that nothing about observable demo behaviour regressed).

## System-Wide Impact

- **Interaction graph:** `LandingVM` gains a `DevToolsGate` injection; landing-screen layout gains one new conditional `LFTextButton` inside the existing button column. `GameVM` consume-on-reads `PendingScenario` in init. `GameStateManager` gains a public `startScene` entry, deletes the private `SpawnSlot` data class, and adds a private `lastLaunched` cache. `AppComponent` gains 4 new providers.
- **Error propagation:** `FileScenarioRepository` follows `FileShipDesignRepository`'s null-on-failure pattern. `ScenarioBuilderVM` translates failures to `ShowToast`. The spawn-gate loop in `GameStateManager` is unchanged — broken-slot skipping happens upstream in the VM, not in the manager.
- **State lifecycle (consume-on-read):** `PendingScenario` has exactly one writer (scenario builder, on Launch click) and exactly one reader (`GameVM.init`). The reader nulls the field as it reads, so the production Play path that follows is guaranteed to see null. There is no defensive "clear on screen entry" needed; the consume-on-read pattern eliminates the failure mode at the source. The persistent answer to "what does `restartScene` re-run?" is `GameStateManager.lastLaunched` — a separate cache, untouched by `clearScene`.
- **API surface parity:** `ShipDesignRepository` and `ScenarioRepository` are sibling interfaces; `FileShipDesignRepository` and `FileScenarioRepository` are sibling impls. The shared sanitiser unifies their filename rules.
- **Integration coverage:** Unit 7 covers the production demo's parity with the scenario builder's preset. Unit 2a's tests cover `GameStateManager.startScene` / `restartScene` / `clearScene` invariants. Unit 2b's tests cover `GameVM`'s consume-on-read path (including the regression test for production-Play-after-scenario-builder). Unit 4's tests cover the VM-to-`PendingScenario` write path. End-to-end (UI click → Game launches with custom slots) is verified manually.
- **Unchanged invariants:** `startDemoScene` exists and remains the production demo entry point. `RepoExporter` and item A's behaviour are untouched. Existing ship-builder, item library, and parts-panel flows are untouched. The `SpawnGate` and `evaluateSpawnGate` logic in `GameStateManager` is untouched. `clearScene` does not touch `lastLaunched`.

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| The `SpawnSlot` → `SpawnSlotConfig` refactor in Unit 2a might subtly change the production demo path. | Unit 7's parity test asserts the demo path is identical pre/post refactor. Unit 2a also keeps `startDemoScene()` as the production demo's public entry point; no caller signature changes. |
| Unit 2a modifies high-risk Kubriko code (per `CLAUDE.md`). | The change set is narrow: replace one private data class, add one public `fun startScene`, add one `var lastLaunched` cache. The spawn-gate loop, design cache, input controller wiring, and `_gameResult` reset are untouched. Validate on both Android and Desktop targets manually before merge. |
| `ScenarioBuilderVM` reads `defaultShipDesignLoader.loadAll()` in `init`. `loadAll()` is `suspend`. A `LoadScenario` arriving before it resolves would mis-flag every slot as broken. | `state.libraryReady` flips true when `loadAll()` resolves; `canLaunch` requires `libraryReady`; `brokenSlotIds` is recomputed whenever `slots` or `libraryShipNames` changes. Race covered by Unit 4 test "library-update race." |
| `DevToolsGate` is a thin wrapper today; adding it now risks "abstraction with one impl." | Origin Decision #7 acknowledges this; the abstraction's value is *semantic decoupling* for future dev tools that don't need repo-write. If no future dev tool ever appears, the abstraction is one extra interface — minor cost. |
| `Scenario.terrain` field is reserved as `List<TerrainConfig> = emptyList()` for Item F's eventual landing. | The empty-list default makes existing scenarios forward-compatible; if Item F changes the schema, `formatVersion` bumps and a migration step lands with F. v1 doesn't need to anticipate F's exact shape. (See Open Questions → Strategic decision for the alternative of dropping the field entirely.) |
| Mini-map's `±500` SceneUnit world bounds may feel cramped or oversized after Item C settles the scale baseline (CCD-5). | Origin doc flags this; tracked as a follow-up "re-tune after C lands." Cheap to change. |

## Documentation / Operational Notes

- No runbook or rollout concerns — Desktop-dev-only feature with no release-build surface.
- Future dev tools that don't need repo-write can depend on `DevToolsGate` directly; document this in the `DevToolsGate.kt` KDoc when it lands.

## Sources & References

- **Origin document:** [`docs/brainstorms/2026-04-26-scenario-builder-requirements.md`](../brainstorms/2026-04-26-scenario-builder-requirements.md)
- **Roadmap:** [`docs/brainstorms/2026-04-24-core-gameplay-roadmap-requirements.md`](../brainstorms/2026-04-24-core-gameplay-roadmap-requirements.md) (item B)
- **Prior item:** [`docs/plans/2026-04-25-001-feat-asset-export-plan.md`](2026-04-25-001-feat-asset-export-plan.md) (item A — provides `RepoExporter` for the `DevToolsGate` wrapper)
- Related code: `features/game/impl/src/commonMain/kotlin/.../components/game/managers/GameStateManager.kt` (refactor target), `features/landing/impl/src/commonMain/kotlin/.../components/landingscreen/ui/LandingVM.kt` (gate consumer), `features/ship-builder/impl/src/commonMain/kotlin/.../components/shipbuilder/data/FileShipDesignRepository.kt` (FileRepository pattern reference)
