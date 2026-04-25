---
date: 2026-04-25
topic: asset-export
roadmap: docs/brainstorms/2026-04-24-core-gameplay-roadmap-requirements.md
roadmap-item: A
---

# Asset Export — Promote App-Data Designs and Items to Project Assets

## Problem Frame

Hull parts and ship designs authored in the ship-builder are saved to `appDirs.getUserDataDir()` via the existing `FileStorage` expect/actual + `FileItemLibraryRepository` / `FileShipDesignRepository` plumbing. That location is per-machine and ephemeral — a fresh install, a wiped app data directory, or moving between machines loses everything. There is no path today to take a part or ship the dev has just authored and turn it into committed project content.

The roadmap (A is its first item) needs a curated, version-controlled library of hull parts and ships for downstream items — B (scenario builder) composes battles from that library, D (slot-system refactor) needs example content to migrate, F (terrain/structures) reuses the slot model, and the eventual game-feel polish phase needs canonical content to tune against.

Asset export closes this gap: a single in-app action that takes an existing app-data JSON and writes it to the repo at the same path layout the runtime asset loaders already read from.

## Goals

1. **Promote curated, authored content into the version-controlled bundled library** so hull parts and ship designs the dev wants to keep stop being machine-local. (This is *promotion*, not general durability — work-in-progress drafts stay in app-data and are not magically backed up by export.)
2. **Match the existing `composeResources/files/` layout** so `DefaultShipDesignLoader` and any future part loader can read exported content with no schema or path changes.
3. **Keep the export action zero-friction in the common dev path** so the iterate → export → re-test loop doesn't pull the dev out of the app.
4. **Stay Desktop-dev-only** per CCD-2 (Android can't reach the repo filesystem; CCD-6 enforces via runtime gate, not compile-time exclusion).

## Non-Goals

- **Reverse direction (import committed assets back into the in-app library).** A dev who wants to iterate on a committed part can clone the JSON manually or delete the local copy and re-author. YAGNI for v1.
- **Turret-gun export.** `turret_guns.json` is a hand-edited bundled file with no in-app authoring affordance. Adding turret-gun export would require adding turret-gun authoring — separate feature.
- **Schema changes.** `ShipDesign.formatVersion` already exists at v3. A does not introduce or change schema. D bumps to v4 if/when slot-based content lands. (A by itself doesn't enforce the bump discipline — it serializes whatever `formatVersion` is at runtime. Adding a CI test that locks the discipline is in Open Questions for Planning.)
- **Batch / "export all dirty" actions.** Per-item export is enough for v1. Add later if the workflow demands it.
- **Android export.** No export UI on Android (CCD-2). Behaviour: hidden, not disabled-with-tooltip — the action shouldn't tease.
- **Asset deletion / management UI.** Dev manages committed assets via git. No in-app "remove from project library."
- **Exported-state indicator on the in-app library.** No badge / tag / colour change to flag "this item has a committed copy in the repo." `git status` is the intended signal.

## Requirements

### R1. Export action surfaces in the existing ship-builder UI

- One affordance for items (hull parts: HULL, MODULE, TURRET, KEEL) — surfaced on each library item in the parts panel.
- One affordance for ship designs — surfaced alongside the existing save/load dialog.
- The same code path (export plumbing) backs both.

### R2. Export targets match the existing composeResources layout

- Ship designs land in `components/game-core/api/src/commonMain/composeResources/files/default_ships/<filename>.json` (existing directory).
- Hull parts land in `components/game-core/api/src/commonMain/composeResources/files/default_parts/<filename>.json` (new directory).
- Auto-create the target directory if missing (first export). If creation fails (permissions, read-only mount, path misconfigured), surface an error toast — do NOT fall through to the clipboard fallback in this case; the env-var path is intended-and-broken, not absent.
- Layout question (flat vs. split by item type) is in Open Questions for Planning.

### R3. Filename derivation is deterministic from the design/item name

- `<name>` lowercased, runs of non-alphanumeric characters collapsed to a single `_`, leading/trailing `_` stripped, `.json` extension appended. Result must match the format used by the existing `default_ships/` files (`player_ship.json`, `enemy_heavy.json`).
- A's slug rule is **stricter** than the existing `FileShipDesignRepository.sanitizeName` regex (`[^a-zA-Z0-9_\\- ]` → `_`), which preserves spaces and case. A's rule is the canonical export slug; planning may either reuse the existing sanitizer with extra normalization on top, or replace it.
- Slug-rule edge cases (Unicode names, all-non-alphanumeric names, Windows-portable filename constraints, length limits) are in Open Questions for Planning.

### R4. Collision behaviour is silent overwrite with a toast

- If the target file exists, overwrite without prompting.
- Toast wording (paths shown relative to `composeResources/files/` to stay readable):
  - First export: `Exported to default_parts/heavy_cruiser.json`
  - Overwrite: `Overwrote default_parts/heavy_cruiser.json`
  - Clipboard fallback: see R5.
- Git is the diff-and-revert safety net for cross-commit accidents. **It is not a safety net for within-commit work loss** — re-exporting between commits replaces the prior file content with no in-app history. The intended workflow is iterate-then-commit at meaningful checkpoints; this expectation is part of A's UX contract, not an emergent surprise.
- Edge case: re-exporting a design with the same name as an existing bundled `default_ships/` entry will replace it. Acceptable given git visibility.

### R5. Export mechanism is repo-root-path with clipboard fallback

- Primary: a JVM-readable repo-root reference resolves the destination. The export action writes directly to `<repo-root>/components/game-core/api/src/commonMain/composeResources/files/<dir>/<file>`.
- The reference is set automatically when the dev runs via `./gradlew run`. Concrete mechanism (environment variable `LFP_REPO_ROOT` set via the `run` task's `environment(...)` setter, or a `-Dlfp.repo.root=$rootDir` JVM system property — they're equivalent for this purpose) is a planning-time choice; the requirement is "auto-set when launched via Gradle, readable at runtime."
- The `./gradlew :composeApp:run` task is the supported launch path. Packaged distributables (`./gradlew :composeApp:packageDmg` etc.) will not have the reference set — A's gate (R6) treats that as if export were unavailable.
- Fallback: if the reference is unset (e.g., IDE Run button without the Gradle launch config), the export action copies the JSON to the system clipboard.
- Clipboard-fallback toast wording: `JSON copied — paste into composeResources/files/default_parts/heavy_cruiser.json` (filename derived per R3).
- The fallback exists for IDE-Run convenience; planning should document the IDE Run config so devs can opt into the primary path.

### R6. Desktop-only via runtime gate

- Per CCD-6, the export affordance is hidden on Android (and any future non-Desktop targets). Enforcement is a runtime check, not a compile-time module exclusion — the export code is on the Android classpath but the UI surface gates on platform.
- The gate evaluates **`isJvm AND repo-root-reference resolves to an existing directory`**. Both conditions are needed: the second one keeps the export action hidden in packaged Desktop distributables (where the reference is unset), so a release build never tries to write into a non-existent repo path.
- Implementation reuses the existing `getPlatform()` expect/actual at `composeApp/src/commonMain/kotlin/jez/lastfleetprotocol/Platform.kt`. Adding a new `expect fun canExportToRepo()` is duplicative; planning composes the gate from `getPlatform()` plus the repo-root-existence check.
- Android item-row layout is identical to Desktop with the export affordance simply absent — no reserved space, no separate variant.

### R7. No app-data side effects

- Exporting a design or item must not modify, move, or delete the app-data copy. The dev keeps editing the in-app version; export is one-way promotion.

## Decisions Resolved

| # | Decision | Choice | Rationale |
|---|---|---|---|
| 1 | Export mechanism | Repo-root-path primary (auto-set via `./gradlew run`), clipboard fallback | Frictionless in the common Gradle path; clipboard covers IDE Run when the repo-root reference is unset. Concrete env-var-vs-system-property choice deferred to planning |
| 2 | Scope | Hull parts (all 4 item types) + ship designs | Both have the same problem and the same plumbing. Turret guns excluded — no in-app authoring exists for them |
| 3 | Filename derivation | Snake_case from name; canonical slug rule introduced by A (stricter than the existing `FileShipDesignRepository.sanitizeName`) | Produces filenames consistent with existing `default_ships/` examples; existing sanitizer preserves spaces and case so cannot be reused as-is |
| 4 | Collision behaviour | Silent overwrite + toast | Iterate-and-re-export is the common case; git is the safety net |
| 5 | Reverse direction (import) | Out of scope | YAGNI for v1 |
| 6 | Schema changes | Out of scope | `formatVersion` already exists; A doesn't touch the schema |
| 7 | Android export | Not supported (hidden) | CCD-2 |

## Open Questions for Planning

- **`default_parts/` layout** — flat vs. split by item type. Both work; pick the one that reads better in `git status` once D's slot model is known (so the layout doesn't get migrated when D lands).
- **Slug rule details** — Unicode handling (ASCII-only `[a-z0-9]+` vs. Unicode-aware), Windows-portable filename constraints, length limits, all-non-alphanumeric fallback. Path-traversal must be excluded by the rule regardless.
- **Repo-root reference mechanism** — `LFP_REPO_ROOT` env var (set via `tasks.named<JavaExec>("run") { environment(...) }`) or `lfp.repo.root` JVM system property (set via `-D` JVM arg). Either works. Planning picks one and captures the `composeApp/build.gradle.kts` stanza.
- **Clipboard API** — Compose Multiplatform `LocalClipboardManager.current` (composable layer) vs. Desktop-only `java.awt.Toolkit.systemClipboard` (use-case layer, JVM-actual). Tradeoff is layer-purity vs. testability of the export use-case.
- **File-write atomicity** — write to `<target>.tmp` then `Files.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING)` vs. direct `writeText`. The repo path is committed to git; partial writes are visible in `git diff`. Recommend atomic-rename for safety.
- **Export-use-case abstraction boundary** — single use-case taking `(jsonContent: String, targetSubdir: String, slug: String)` (simpler) vs. generic over `KSerializer<T>` (less coupling).
- **`formatVersion` enforcement** — should A also add a CI / unit test that asserts every committed `default_*.json` has `formatVersion == ShipDesign.CURRENT_VERSION`, so a future schema change can't silently drift the bundled assets out of sync? Lightweight; closes a real gap.
- **UI affordance placement** — context menu on the item row (matches existing save-action placement) is the leading candidate; final call is a small interaction-design pick during planning.

## Dependencies

- **None.** A is the first roadmap item and depends only on the existing FileStorage + repository infrastructure (already shipped).
- **Unblocks B** (scenario builder needs the ship library) and indirectly D (parts library benefits the slot-system migration).

## Success Criteria

1. From `./gradlew run`, the dev can author a hull part in the ship-builder, click an export action, and find a new JSON file in `components/game-core/api/src/commonMain/composeResources/files/default_parts/<name>.json` ready to commit.
2. The same flow works for ship designs into `default_ships/`.
3. Re-exporting an updated design overwrites the existing file with no prompts; a toast confirms the action.
4. Running from the IDE without the repo-root reference set: the export action copies JSON to clipboard with a clear toast, and the dev can paste into a new file in their editor.
5. On Android, the export action is not visible.
6. The exported JSON deserializes correctly via `kotlinx.serialization` against the current `ShipDesign` / `ItemDefinition` schemas — confirming A wrote a valid file at a valid path. (Whether downstream loaders pick up `default_parts/` is the responsibility of those loaders; A's contract ends at writing valid JSON.)

## Handoff

Next action: `/ce:plan` to produce the implementation plan from this requirements doc. Plan-time decisions are listed under Open Questions for Planning above.
