---
date: 2026-04-26
updated: 2026-04-26
topic: scenario-builder
roadmap: docs/brainstorms/2026-04-24-core-gameplay-roadmap-requirements.md
roadmap-item: B
---

# Scenario Builder — Compose-and-Launch Dev Tool

## Problem Frame

`GameStateManager.startDemoScene` builds a hardcoded list of 5 `SpawnSlot`s (2 player, 3 enemy) and runs them through the spawn gate. Every dev playtest uses the same scenario. To validate combat-balance changes (item C), the slot-system refactor (D), the projectile-interaction model (E), terrain (F), and player-AI markers (G), devs need to compose ad-hoc battles with arbitrary team sizes and starting positions — and do it fast enough that the test loop stays tight.

A scenario builder lets a dev pick ships from the bundled library (item A's output), drop them onto a mini-map, and launch the game with that configuration. Saved scenarios accrete into a small regression-test library that catches breakage downstream.

This is the second roadmap item, sequenced immediately after A (which delivered the asset library mechanism). The 4 currently-bundled ships (`player_ship`, `enemy_light`, `enemy_medium`, `enemy_heavy`) are the v1 source pool; A's export action is what grows the pool over time. **B's leverage scales with library size** — with the v1 4-ship pool, B mostly enables positional / count variation; compositional variety unlocks as the dev exports new designs via Item A. Re-evaluate B's success bar after the library has more than ~8 ships.

## Goals

1. **Make ad-hoc battle composition take under a minute** — measured from opening the scenario-builder screen to clicking Launch (excludes app startup time). From an empty starter, the dev composes a 2v2 over the 4-ship pool and launches in a single sitting without reading docs.
2. **Build the saved-scenario library** that downstream items rely on for reproducible regression playtests. Devs save a few canonical scenarios and re-launch them on demand.
3. **Stay Desktop-dev-only** per CCD-6 — runtime gate, hidden on Android, hidden on the player landing screen.

## Non-Goals

- **Player-facing mission picker.** Out of scope; CCD-3 defers it.
- **Composition core as a separate module.** Earlier draft of this brainstorm included an R5 mandating module-level separability "for a future mission picker." Scope-guardian and adversarial review flagged this as speculative generality (CCD-3 explicitly defers the future consumer). The data classes (`Scenario`, `SpawnSlotConfig`) live in `:components:game-core:api` alongside `ShipDesign` because they're the domain model, not because of separability requirements. The launch-glue code stays in `:features:scenario-builder:impl`. If a player-facing mission picker is ever scoped, extract-then is a bounded refactor.
- **Terrain placement.** Deferred until item F lands. B's data model leaves a hook so F can extend without a schema migration, but the v1 UI doesn't expose terrain.
- **More than two teams.** Player and enemy only. `TEAM_PLAYER` / `TEAM_ENEMY` constants stay as the only team values.
- **Per-scenario AI tuning.** AI assignment is per-ship (the existing `withAI` Boolean on `SpawnSlot`). Choosing between different AI strategies is out of scope; `BasicAI` is the only option in v1.
- **Camera/zoom configuration as part of a scenario.** Item C (battle-feel) owns scale and zoom defaults.
- **Scenario validation beyond minimums.** Refuse to launch with zero ships in either team. Otherwise launch best-effort; the existing spawn gate already handles unflightworthy ships gracefully.
- **Committing scenarios to the repo.** Per CCD-3, app-data only in v1.
- **Editing a running scenario.** Once Launch fires, the scenario is locked.
- **Importing/converting the existing demo scene.** The demo's hardcoded layout is moved into a shared `DemoScenarioPreset` constant (see R6) so `startDemoScene` and the scenario-builder both read from one source — but B doesn't otherwise touch the demo path.
- **Process-death scenario recovery.** Compose Navigation back-stack scopes preserve in-progress state across nav transitions, but app process death loses unsaved work. Acceptable for a dev tool.

## Requirements

### R1. List editor + read-only mini-map preview

- **Top bar (full screen width, above both panels):** scenario name field, Save button, Load button, Launch button.
- **Left panel:** two stacked sections (Player Team, Enemy Team). Each section renders a list of slot rows. Each section has an "Add slot" button + an "Use demo defaults" button (always visible — no first-open-only lifetime; pressing it replaces the current slot list with `DemoScenarioPreset.SLOTS`).
- **Slot row:** ship-name dropdown (`ExposedDropdownMenuBox` matching the ship-builder pattern), x position numeric input, y position numeric input, AI toggle (Compose `Checkbox` matching ship-builder row pattern), remove icon button. **The list row is the only edit surface for positions** — no drag-to-position elsewhere.
- **Right panel: read-only mini-map preview.** Renders the spawn positions of all current slots, colour-coded by team (cyan for player, red for enemy — matching the existing in-game ship colours). World bounds default to `±500` SceneUnits on both axes (re-tune after Item C settles the scale baseline; tracked in Open Questions). The mini-map is a visual sanity check — no marker drag, no click-to-position. As-typed x/y values render live so the dev sees the layout update while editing.
- **One-way binding from list to mini-map:**
  - Type into x/y inputs → marker updates live. Out-of-bounds values render at the clamped edge but the input retains the typed value (no input-clamp side effect).
  - Hover or focus a list row → corresponding marker highlights on the mini-map.
  - **No drag, no snap, no two-way feedback.** Drop-drag interaction was scoped out at brainstorm time on cost-vs-benefit grounds (it duplicates the canvas-drag complexity the ship-builder already pays for, with marginal added value when the list inputs are the source of truth).
- **Empty state for the mini-map (zero slots):** world-bounds rectangle with a dashed outline and centred guidance text "Add slots to position ships."
- **Broken-slot row (R4):** orange warning icon at the left of the row, dropdown highlighted with `MaterialTheme.colorScheme.error` border, x/y inputs disabled until the dropdown is set to a valid ship. Broken slots do not render markers on the mini-map.

### R2. App-data persistence with named scenarios

- Each scenario has a name; saving writes JSON to `userDataDir/scenarios/<sanitized-name>.json`.
- Sanitisation rule: lift the existing `FileShipDesignRepository.sanitizeName` (`[^a-zA-Z0-9_\\- ]` → `_`) into a shared helper in `:components:shared:api`, or duplicate verbatim. Scenarios use the same sanitiser as ship designs for consistency.
- Save behaviour:
  - Empty scenario name field → Save button is disabled.
  - Non-empty name field, no existing file at the sanitised path → silent write.
  - Non-empty name field, existing file → confirm dialog ("Overwrite scenario *<name>*?") with default = Cancel. Defends against accidental clobber of saved regression scenarios. (Differs from Item A's silent-overwrite: scenarios are invisible state without a canvas the dev can rebuild from muscle memory; the explicit confirmation costs one click and prevents losing work.)
- Load dialog mirrors the existing `LoadDesignDialog` from the ship-builder — list of saved names, click to load, dismiss to cancel. Reuses the `FileStorage` expect/actual + `FileShipDesignRepository`-style pattern via a new `FileScenarioRepository`.

### R3. Library is sourced from the bundled-asset loader at runtime

- The ship-name dropdown lists the ship designs returned by `DefaultShipDesignLoader.loadAll()`. Today this is the 4 hardcoded entries in `DefaultShipDesignLoader.SHIP_FILENAMES` (`player_ship`, `enemy_light`, `enemy_medium`, `enemy_heavy`).
- **Adding a new ship to the dropdown requires a manual edit of `SHIP_FILENAMES` plus committing the JSON.** Item A's export action writes the JSON file but does not update `SHIP_FILENAMES` — that's a hand-edited manifest until a future iteration of A grows to also append filenames on export. The doc's earlier draft claimed "auto-discover" — that's incorrect against the current loader and has been corrected here.
- If the loader returns zero ships (cannot happen in practice today — defensive against future refactors), the dropdown shows an empty-state message and Launch is disabled.

### R4. Stale-reference handling: load best-effort with broken-slot indicator

- When loading a saved scenario, validate each slot's `designName` against the current bundled library (i.e., against `DefaultShipDesignLoader.loadAll()`).
- Slots whose `designName` no longer resolves are marked **broken** in the UI per R1's broken-slot-row spec. The dev re-picks a ship from the dropdown to repair, or removes the slot.
- **Launch is allowed with broken slots present** — broken slots are skipped at launch time with a one-line `[scenario]` log entry naming each skipped slot. A persistent banner above the slot list shows "*N* broken slot(s) will be skipped at launch." This avoids fighting the workflow where a dev wants to launch the working slots and ignore the broken one. R9's team-min check still applies: if skipping broken slots would leave a team with zero ships, Launch is disabled.
- The saved scenario file is not auto-rewritten on load. The dev's edits are saved on the next explicit Save click.

### R5. Empty-state and "Use demo defaults"

- First-time open (no scenarios saved): the screen shows an empty starter scenario (no slots).
- The "Use demo defaults" button (in each team panel per R1) is always available. Pressing it loads the canonical 5-ship demo layout via `DemoScenarioPreset.SLOTS` — same constant `startDemoScene` consumes (see R6).
- The dev can edit/save/launch from there without typing positions manually.

### R6. Canonical demo layout is a shared constant

- Extract the 5-ship demo layout (`player_ship` ×2 at canonical positions, `enemy_light/medium/heavy` at canonical positions) into a public `DemoScenarioPreset` constant in `:components:game-core:api` alongside `ShipDesign`.
- `GameStateManager.startDemoScene` consumes `DemoScenarioPreset.SLOTS` instead of inlining the list. R7's parallel-launch-path mechanism reuses the same conversion. Editing the canonical demo layout requires changing exactly one constant; the scenario builder preset and the production demo never drift.
- Acceptance check: a unit test asserts the production `startDemoScene` path produces the same SpawnSlot list as the scenario-builder preset path when both are constructed from `DemoScenarioPreset.SLOTS`.

### R7. Launch-and-return preserves the in-progress config

- Clicking Launch opens the same Game screen the existing landing-page Play button leads to, but with the scenario's spawn slots replacing `startDemoScene`'s default.
- **Launch path mechanism** (concrete enough for the plan to inherit, not a working assumption): introduce a `PendingScenario` singleton holder injected into both `ScenarioBuilderVM` (writer) and `GameVM` / `GameStateManager` (reader). Scenario-builder writes the slots to the holder before navigating to Game. `GameVM.init` reads the holder; if non-null, calls `GameStateManager.startScene(slots)` (a new public entry point); if null, falls back to `startDemoScene()` (which itself uses `DemoScenarioPreset.SLOTS`).
- **Restart semantics:** `GameStateManager.restartScene` re-runs the most recently launched scene — the holder retains the pending scenario across restarts within a Game session. Production Play (`PendingScenario` is null) restarts re-run `DemoScenarioPreset.SLOTS`.
- **Return path:** Compose Navigation back-stack survival preserves `ScenarioBuilderVM` while the Game screen is on top of it. Navigating back from Game (Exit on pause menu, or Back from Victory/Defeat overlay) pops Game and reveals scenario builder with the in-progress config still in `state`. **Restart on the Victory/Defeat overlay** re-runs the same scenario without going back to the builder.
- **Open/close lifecycle:** Unsaved config persists across launch-and-return only. Closing the screen via nav-Back (to landing) discards the unsaved config; reopening the screen shows an empty starter scenario. App process death also loses unsaved work; non-goal per the Non-Goals section.
- Production demo path (Landing → Play → `startDemoScene`) is functionally untouched — under the hood it's now `startScene(DemoScenarioPreset.SLOTS)` but observable behaviour is unchanged.

### R8. Desktop-dev-only via `DevToolsGate`

- Per CCD-6, the scenario builder is hidden on Android and on the production landing screen.
- Reaches the screen via a **single "Scenario Builder (dev)" link** on the landing screen, gated on `DevToolsGate.isAvailable`. No dev-menu shell — it's a single link, like Item A surfaced its export action inline rather than introducing a menu.
- `DevToolsGate` is a new, intentionally-minimal abstraction in `:components:shared:api`. **Today** it returns `RepoExporter.isAvailable` — the two conditions happen to coincide (Desktop JVM with a resolvable repo root). **Semantically** it answers "should we expose dev tooling on this build?" rather than "can we write to the repo?" Future dev tools that don't need repo-write can depend on `DevToolsGate`; tools that do (Item A's export) keep depending on `RepoExporter.isAvailable` as the stricter superset.
- **Link placement:** small text link in the bottom-left corner of the landing screen, visible only when `DevToolsGate.isAvailable`. Always-visible-when-gate-open (no hover affordance) — this is a dev tool for the solo dev, no harm in it being plain.
- **When future items add a second dev-only screen** (D's dev ship-builder, F's terrain authoring), the link grows into a tiny "Dev Tools" menu — but that's a forward-looking change, not pre-staged here. v1 ships with a single link.

### R9. Validation at launch: minimum-viable scenario

- Launch is disabled when:
  - Either team would end up with zero non-broken slots (i.e., zero slots to begin with, or all slots are broken).
  - Whatever R4 broken-slot logic resolves: broken slots are skipped at launch (per R4) but the team-minimum check applies to the post-skip slot count.
- Launch with otherwise-questionable content (e.g., unflightworthy designs) proceeds — the existing spawn gate already handles those gracefully and the dev sees the rejection as a `[combat]` log line. Matches the production demo behaviour.

## Decisions Resolved

| # | Decision | Choice | Rationale |
|---|---|---|---|
| 1 | UI shape | List editor + read-only mini-map preview | Spatial sanity from the mini-map without paying canvas-drag complexity for marginal benefit; list inputs are the only edit surface |
| 2 | Persistence | App-data saved + load dialog + confirm-on-overwrite | Devs accrete a small regression-test library; explicit overwrite confirm prevents losing scenarios that have no canvas-rebuild fallback |
| 3 | Stale-reference handling | Load best-effort, mark broken slots, **skip-at-launch with banner** (revised from earlier "block-Launch") | Doesn't fight the dev's "just launch the working slots" workflow; broken-slot UI still surfaces the issue loudly |
| 4 | Empty-state | Empty starter + always-visible "Use demo defaults" button per team | Fast path for the dev who just wants to tweak the existing demo |
| 5 | AI assignment | Per-ship `withAI` toggle, defaults match team convention | Matches existing `SpawnSlot` shape; player no-AI, enemy with AI by default |
| 6 | Launch-and-return | Return preserves config across launch-and-return; close/nav-Back discards | Concrete contract for back-stack preservation; no save-state magic for nav-back |
| 7 | Dev gate | New `DevToolsGate` signal; today equals `RepoExporter.isAvailable` | Decouples "show dev tooling" from "write to repo"; future dev tools without repo-write needs aren't excluded |
| 8 | Multiple teams | Out of scope; player + enemy only | TEAM_PLAYER / TEAM_ENEMY constants stay as the only values |
| 9 | Terrain placement | Deferred to item F; data model leaves a hook | Don't pre-build UI for unshipped behaviour |
| 10 | Repo-committing scenarios | Out of scope per CCD-3 | App-data only; revisit if devs want shared regression fixtures |
| 11 | Demo defaults source | Shared `DemoScenarioPreset` constant; `startDemoScene` and the preset both read it | One source of truth; no drift after C's scale retune |
| 12 | Composition core separability | **Dropped.** Data classes live in `:components:game-core:api` because that's the domain home, not for future-mission-picker reuse | Speculative generality flagged; CCD-3 defers the consumer |

## Working Assumptions

These are claims that planning should verify but the brainstorm is committing to as starting points:

1. **Compose Navigation back-stack scopes preserve `ScenarioBuilderVM`** while the Game screen is on top. Verified pattern: Compose Nav 2.x scopes ViewModelStore per `NavBackStackEntry`. The plan should confirm `LFNavHost` uses default `composable(...)` entries with no `popUpTo` on launch — if any nav pops the scenario-builder entry, the in-progress config is lost.
2. **Item A's `RepoExporter` is already injected into the DI graph** and can be reused for `DevToolsGate.isAvailable` without restructuring. The new gate type wraps the existing one; no new platform actuals.
3. **`DefaultShipDesignLoader.SHIP_FILENAMES` stays hand-edited** until a future Item A iteration grows to append filenames on export. R3 acknowledges this; the dropdown reads `loadAll()` so future dynamic enumeration is a one-line loader change.
4. **Mini-map is read-only Compose Canvas** rendering markers as filled circles per spawn slot. No `pointerInput` / `detectDragGestures` interaction — drag was scoped out (R1). The mini-map subscribes to the list's StateFlow so marker positions update live as x/y inputs change.

## Open Questions for Planning

- **Scenario JSON schema.** Mirrors the current private `SpawnSlot` data class but as `@Serializable` with a `formatVersion` (matching `ShipDesign`'s discipline). Concrete shape (field names, optional vs. required, default values) is a planning-time choice.
- **Mini-map world-bounds rectangle.** Default `±500` SceneUnits on both axes; re-tune after Item C settles the scale baseline (CCD-5).
- **Sanitiser placement.** Lift `FileShipDesignRepository.sanitizeName` into a shared helper in `:components:shared:api` (cleaner) vs. duplicate verbatim into `FileScenarioRepository` (smaller diff). Plan picks one.
- **DI seam for the launch-path holder.** `PendingScenario` as a `@Singleton` holder injected into both `ScenarioBuilderVM` and `GameStateManager` — straightforward kotlin-inject pattern. Plan confirms file paths and module placement (likely `:features:scenario-builder:api` with consumers depending on it).
- **Snap-to-grid step size on the mini-map.** Doc commits to 10 SceneUnits as a default; planning may pick a different value if it feels coarse against the world-bounds rectangle.

### Deferred (out of scope per CCDs)

- **BundleIndex entry for committed scenarios.** Out of scope per CCD-3 (committing scenarios to repo is deferred). Re-evaluate if scenarios ever get committed.
- **Process-death recovery via SavedStateHandle.** Out of scope per Non-Goals; dev tool, acceptable to lose unsaved state on app crash.

## Dependencies

- **Hard:** Item A — needs the bundled-asset loading mechanism and the `RepoExporter` instance for `DevToolsGate`. (Item A is shipped on `main`.)
- **Soft:** the dev-menu entry on the landing screen is new and B introduces it. Item C, D, E, F, G all benefit from B existing but none structurally depend on it.

## Success Criteria

1. From `./gradlew :composeApp:run`, the dev clicks the "Scenario Builder (dev)" link on the landing screen, populates a 2v2 scenario from scratch, and clicks Launch — total time **from screen-open to Launch click under a minute** (excludes app startup).
2. The dev saves the scenario as "two-vs-two even", relaunches the app, opens the scenario builder, loads the saved scenario, and clicks Launch — fewer than three clicks from app open.
3. Loading a scenario whose `designName` no longer exists in the library shows broken-slot indicators and a banner; Launch fires and skips the broken slot, logging the skip; the dev can repair in-place and re-save without losing the unbroken slots.
4. On Android: no Scenario Builder link on the landing screen; scenario builder is unreachable from any code path.
5. On Desktop without `lfp.repo.root` set: same as Android — Scenario Builder link is hidden (today). When `DevToolsGate` decouples from `RepoExporter.isAvailable` in a future iteration, this changes — the brainstorm captures the current equivalence in Decision #7.
6. The production demo path (Landing → Play) continues to work unchanged. A unit test asserts `DemoScenarioPreset.SLOTS` is the source of truth for both the production demo and the scenario-builder "Use demo defaults" preset.
7. The scenario-builder VM survives navigation to Game and back; in-progress config (saved or unsaved) is intact on return. Closing the screen via nav-Back to Landing discards unsaved config.

## Handoff

Next action: `/ce:plan` to produce the implementation plan from this requirements doc. The Open Questions for Planning are the concrete implementation choices the plan resolves; the Working Assumptions are starting positions the plan should verify and commit to.
