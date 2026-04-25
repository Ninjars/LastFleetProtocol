---
title: "feat: Asset Export — promote app-data designs and items to repo composeResources"
type: feat
status: completed
date: 2026-04-25
origin: docs/brainstorms/2026-04-25-asset-export-requirements.md
---

# Asset Export — promote app-data designs and items to repo composeResources

## Overview

Add a Desktop-dev-only export action to the ship-builder so authored hull parts and ship designs can be promoted from `appDirs.getUserDataDir()` (where `FileItemLibraryRepository` and `FileShipDesignRepository` already save them) into the committed `components/game-core/api/src/commonMain/composeResources/files/` tree the runtime asset loaders read from. The action writes JSON directly when `./gradlew :composeApp:run` is the launch path; otherwise falls back to copying JSON to the system clipboard with a guidance toast. This is roadmap item A — first step toward a curated, version-controlled library.

**Integration notes.** New `RepoExporter` interface in `components/shared/api` (alongside existing `FileStorage`); two new `ShipBuilderSideEffect` variants (`ShowToast`, `CopyToClipboard`); first introduction of `SnackbarHost` and `LocalClipboard` use in this codebase; one Gradle JVM-arg added to `composeApp/build.gradle.kts`'s `compose.desktop.application` block; one new commonTest in game-core/api for `formatVersion` drift detection. No existing API contracts change.

## Problem Frame

See origin: `docs/brainstorms/2026-04-25-asset-export-requirements.md`. In short: assets stay machine-local until something promotes them. A solo dev who reinstalls, switches machines, or wipes app data loses everything. Roadmap items B / D / F all want a curated repo-tracked library; A is the prerequisite.

## Requirements Trace

- **R1** — Export action surfaces in the existing ship-builder UI; same plumbing for items and designs. **R1a** (VM intents + use-case wiring) lands in Unit 4; **R1b** (UI affordances) lands in Unit 5.
- **R2** — Targets land in `composeResources/files/{default_ships,default_parts}/<slug>.json`; auto-create `default_parts/`; error on creation failure (no clipboard fall-through for that case).
- **R3** — Filename is canonical snake_case slug derived from name; A introduces this rule (stricter than `FileShipDesignRepository.sanitizeName`).
- **R4** — Silent overwrite + concrete toast for new / overwrite states.
- **R5** — Repo-root-path primary (auto-set when launched via Gradle), clipboard fallback when unset.
- **R6** — Desktop-dev-only via runtime gate `isJvm AND repo-root resolves to validated repo directory`. Reuses existing `getPlatform()`. Hidden on Android, hidden in packaged Desktop builds. Units 1 + 2 own the gate logic (commonMain composition + JVM sentinel); Unit 4 exposes it through `state.canExport`; Unit 5 binds to it for visibility.
- **R7** — No app-data side effects.
- **R8** *(new — closes the discipline gap noted in origin Non-Goals)* — A `commonTest` in `:components:game-core:api` asserts every committed `default_*.json` deserializes successfully and (where applicable) carries the current `formatVersion`. Lands in Unit 6.

(see origin: `docs/brainstorms/2026-04-25-asset-export-requirements.md`)

## Scope Boundaries

- **Reverse direction (import).** Out of scope. Trigger to revisit: dev hits the iterate-on-committed-content case in practice.
- **Turret-gun export.** Out of scope. `turret_guns.json` is hand-edited.
- **Schema changes.** Out of scope. `formatVersion` already exists at v3 on `ShipDesign`; A serializes whatever the current schema is.
- **Batch / "export all dirty"** actions. Per-item only.
- **Android export.** Hidden on Android; not disabled-with-tooltip.
- **Asset deletion / management UI.**
- **Exported-state indicator** (e.g., a badge on items that already have a committed copy). `git status` is the intended signal.
- **The dev's polygon-tool refactor into a dev authoring screen.** That's roadmap item D.
- **Compose-UI snapshot tests.** No compose-ui-test infrastructure exists in this repo. Unit 4 verification is structured manual checks (Test scenarios per state listed in that unit).

## Context & Research

### Relevant Code and Patterns

- `components/shared/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/utils/FileStorage.kt` + `FileStorage.{jvm,android}.kt` — existing KMP file-IO pattern using `expect/actual` and `appDirs.getUserDataDir()`. New repo-write actuals follow this shape (commonMain `expect`, JVM actual does the work, Android actual returns unavailable).
- `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/data/FileShipDesignRepository.kt` — current sanitizer (`[^a-zA-Z0-9_\\- ]` → `_`, preserves spaces and case). A's slug rule is canonical and stricter; this file is **not** modified.
- `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/data/FileItemLibraryRepository.kt` — companion repo for individual item JSONs in `userDataDir/items/`.
- `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/ShipBuilderVM.kt` — MVI ViewModel; new export intents land here. Existing intents (`DuplicateLibraryItem`, `EditLibraryItem`, `DeleteLibraryItem`) provide the per-item action shape to mirror.
- `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/entities/ShipBuilderSideEffect.kt` — currently only `NavigateBack`; gets `ShowToast` and `CopyToClipboard` extensions.
- `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/composables/PartsPanel.kt` — `ItemRow` is a private composable that stacks always-visible `LFIconButton` action icons in a `Column` (no overflow menu, no long-press menu). The export action follows the same pattern as a fourth stacked icon.
- `composeApp/src/commonMain/kotlin/jez/lastfleetprotocol/Platform.kt` — `expect fun getPlatform(): Platform` already exists. Reused by the gate; **no new platform-discriminator expect/actual** is added (per origin doc).
- `composeApp/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/di/AppComponent.kt` — kotlin-inject DI root; `RepoExporter` binding lands here.
- `composeApp/build.gradle.kts` — `compose.desktop.application` block; the JVM-arg injection lands here.
- `components/design/src/commonMain/composeResources/values/strings.xml` + `LFRes.String` — UI strings convention. Toast wording lands here per CLAUDE.md.
- `features/ship-builder/impl/src/commonTest/kotlin/.../ShipBuilderVMKeelPickerTest.kt` — VM test pattern using `kotlinx-coroutines-test` + `Dispatchers.setMain(UnconfinedTestDispatcher())`. Carry forward unchanged.

### External References

- Compose Multiplatform 1.10.3 (per `gradle/libs.versions.toml`): `androidx.compose.ui.platform.LocalClipboardManager` is **deprecated** in favour of `androidx.compose.ui.platform.LocalClipboard` (a `Clipboard` interface with suspending `setClipEntry(...)`). Both still compile; the new code uses `LocalClipboard` to avoid landing on a deprecated API.
- `compose.desktop.application { jvmArgs += listOf(...) }` is supported by the Compose Desktop Gradle plugin 1.6+ — the DSL-level path is preferred. Falls back to `tasks.named("run", JavaExec::class) { jvmArgs(...) }` only if the DSL form fails verification.
- `java.nio.file.Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` — standard JVM atomic-rename pattern.

## Key Technical Decisions

- **System property `lfp.repo.root`** set via `compose.desktop.application { jvmArgs += listOf("-Dlfp.repo.root=${rootDir.absolutePath}") }`. System properties compose more cleanly with the Compose Desktop application DSL than env vars; both met R5, this is simpler to wire.
- **Repo-root validation by sentinel file.** Resolving the property is not enough — a misconfigured `lfp.repo.root=/` would clear the gate and let writes land in unexpected places. Validation requires the resolved root to *contain* a sentinel file (`settings.gradle.kts` is canonical for this repo). Resolution failure or sentinel absence → gate closed → clipboard fallback (or Error if R2's auto-create-dir branch hits the gate failure later).
- **`RepoExporter` lives in `components/shared/api`** — alongside `FileStorage`. Matches the existing KMP-bridged-utility pattern. Future authoring surfaces (D's dev ship-builder, possibly F's terrain authoring) reuse the same contract without reaching into ship-builder/impl.
- **Use-case is an injectable interface** — `interface RepoExporter` + `class RepoExporterImpl @Inject constructor()`. Lets VM tests inject a fake without touching `expect/actual`. The impl delegates to thin platform-actuals (`expect fun resolveRepoRoot(): String?` and `expect fun writeRepoFile(absolutePath: String, content: String)`).
- **Slug helper takes `(name, fallbackSeed)`.** Pure function, but not pure-from-name alone — when the name slug is empty (all non-alphanumeric, Unicode-only, etc.), the helper falls back to `untitled-<8-char-prefix-of-fallbackSeed>`. Callers pass the item/design id as the fallback seed. Documented in Unit 1.
- **Single use-case, three thin call paths** — `RepoExporter.export(content, subdir, slug, replacing) -> ExportResult`. Returns `Wrote(relativePath, isOverwrite)` / `RequiresClipboard(content, suggestionPath)` / `Error(reason)` / `BundleCollision(bundledName)`.
  - The new `BundleCollision` variant guards a real foot-gun: a duplicate of a bundled-catalogue item may keep the same name and silently overwrite the bundled JSON. The exporter rejects writes whose slug matches a bundled-asset slug **unless `replacing` is the same id as the existing bundled file's content** (i.e., it's the same logical asset being re-exported, not a different one masquerading).
- **Side-effects channel handles toast + clipboard** — `ShipBuilderSideEffect` adds `ShowToast(text: String)` and `CopyToClipboard(text: String, toastMessage: String)`. The screen handles both via `LocalClipboard.current` and a `SnackbarHost`. VM stays platform-agnostic.
- **Gate combines `getPlatform()` + repo-root validation** — exposed as `RepoExporter.isAvailable: Boolean`. Computed once at construction (the system property doesn't change at runtime). UI components observe this for visibility. Gate is also re-checked at `export()` time so a leaked-visible button doesn't crash.
- **Slug rule v1** — `name.lowercase()` → replace runs of `[^a-z0-9]` with `_` → trim leading/trailing `_` → cap at 64 chars (truncate at last `_` boundary if needed) → if empty, fall back to `untitled-<first-8-chars-of-fallbackSeed>`. Defends against path-traversal (`.` and `/` are non-alphanumeric). **Known limitation:** semantically meaningful punctuation is lost (`F-22` → `f_22`, `Mk.II` → `mk_ii`). Acceptable for v1; revisit if it becomes friction.
- **Atomic-rename for repo writes** — JVM actual: ensure parent directory exists, write to `<target>.tmp` *in the same parent directory* (guaranteeing same-filesystem move), then `Files.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING)`. On any exception, `try { ... } finally { tmp.deleteIfExists() }` cleans up so a failed write doesn't leave a half-file in the source tree.
- **Layout: flat `default_parts/`** — `default_parts/<slug>.json` for all four item types. Loader can dispatch on `ItemDefinition.itemType`. If D wants split-by-type, migration is one-time `git mv` with no schema impact.
- **`state.canExport` is read once at VM init.** `RepoExporter.isAvailable` is fixed at app start (system property + filesystem state). VM reads it synchronously in `init { ... }` and freezes the boolean into `ShipBuilderState`. No reactive plumbing needed; existing tests using `Dispatchers.setMain(UnconfinedTestDispatcher())` continue to work without setup changes.
- **`formatVersion` drift test** — Unit 5 adds a `commonTest` that loads every `default_*.json`, asserts deserialization succeeds, and (for ships) asserts the parsed `formatVersion` equals `ShipDesign.CURRENT_VERSION` (introduce this `const` if not already present). `ItemDefinition` has no `formatVersion` field today (verified at `components/game-core/api/src/commonMain/.../ItemDefinition.kt`); the parts arm of the test asserts deserialization-succeeds only, with a TODO for D to introduce versioning.

## Open Questions

### Resolved During Planning

- **Mechanism: env var vs system property?** → System property `lfp.repo.root`.
- **Where does the use-case live?** → `components/shared/api`.
- **Use-case shape** → injectable interface with `(content, subdir, slug, replacing) -> ExportResult`.
- **Clipboard API choice** → `LocalClipboard.current` (the non-deprecated API) at the screen layer, dispatched via side-effect.
- **Atomicity** → atomic-rename via `Files.move` with same-parent tmp file; `try/finally` cleanup.
- **`default_parts/` layout** → flat for v1.
- **`formatVersion` CI enforcement** → yes, as Unit 5; `commonTest` in `:components:game-core:api`.
- **UI affordance placement** → fourth stacked `LFIconButton` on `ItemRow` (matches existing pattern); explicit "Export Design" button next to "Load Design".
- **Toast presentation** → existing Compose `SnackbarHostState` at the **screen root** of `ShipBuilderScreen` (single host, not nested). Snackbar queueing handled by `SnackbarHostState.showSnackbar`'s built-in suspend semantics — rapid exports queue naturally.
- **Repo-root validation** → require a sentinel file (`settings.gradle.kts`) at the resolved root.
- **Bundle-collision guard** → `RepoExporter` rejects writes whose slug matches a bundled-asset slug for a different logical asset; new `BundleCollision` `ExportResult` variant.
- **Toast path length** → use the relative-from-`composeResources/files/` form (e.g. `default_parts/heavy_cruiser.json`) — short enough for one Snackbar line on Desktop. Full path appears nowhere in user-visible toasts.
- **`ItemDefinition` versioning** → confirmed absent (verified). Unit 5's parts arm covers deserialization only; D adds versioning when slot-based content lands.
- **`ShipBuilderState.canExport` field shape** → `Boolean` with default `false`; existing `copy(...)` call sites and default-constructed tests remain source-compatible.
- **`SnackbarHostState` placement** → screen root, single host.

### Deferred to Implementation

- **`composeApp/build.gradle.kts` exact stanza.** Plan commits to: `compose.desktop.application { jvmArgs += listOf("-Dlfp.repo.root=${rootDir.absolutePath}") }` (DSL-level, supported in Compose Desktop plugin 1.6+). If verification fails, fall back to `tasks.named("run", JavaExec::class) { jvmArgs("-Dlfp.repo.root=${rootDir.absolutePath}") }`. Verification: a single startup log line of `System.getProperty("lfp.repo.root")` confirms propagation. **The verification log line must be deleted before the implementing commit lands** — it must not ship.
- **Whether the explicit "Export Design" button replaces or sits alongside the existing "Load Design" button.** Trivial layout call; whatever fits the existing stats panel layout.
- **Slug edge cases** — Unicode names, very long names, Windows-portable filename constraints (`<>:"|?*` chars). Slug rule v1 collapses all of these via `[^a-z0-9]+ → _`; explicit test scenarios in Unit 1 cover the documented cases. Any further edge case the implementer hits gets a test added inline.

## Output Structure

```
components/shared/api/src/
├── commonMain/kotlin/jez/lastfleetprotocol/prototype/utils/export/
│   ├── RepoExporter.kt              (interface, ExportResult sealed type, RepoExporterImpl) — Unit 1
│   ├── SlugRule.kt                  (pure fn: name+fallbackSeed → slug; SLUG_RULE_VERSION) — Unit 1
│   └── RepoFsActual.kt              (expect: resolveRepoRoot, isValidRepoRoot, writeRepoFile) — Unit 1
├── androidMain/kotlin/jez/lastfleetprotocol/prototype/utils/export/
│   └── RepoFsActual.android.kt      (no-op stub: returns null/false/throws) — Unit 1
├── jvmMain/kotlin/jez/lastfleetprotocol/prototype/utils/export/
│   └── RepoFsActual.jvm.kt          (System.getProperty + sentinel check + atomic-rename) — Unit 2
├── commonTest/kotlin/jez/lastfleetprotocol/prototype/utils/export/
│   └── SlugRuleTest.kt              — Unit 1
└── jvmTest/kotlin/jez/lastfleetprotocol/prototype/utils/export/
    ├── RepoExporterJvmTest.kt       — Unit 2
    └── ExportRoundTripTest.kt       (RepoExporter ↔ kotlinx.serialization round-trip) — Unit 7

components/game-core/api/src/commonTest/kotlin/.../shipdesign/
└── BundledAssetVersionTest.kt       — Unit 6

components/game-core/api/src/commonMain/composeResources/files/
└── .gitignore                        (*.tmp to defend against orphaned partial writes) — Unit 2
```

## Implementation Units

- [ ] **Unit 1: `RepoExporter` interface + slug rule + commonMain logic + Android no-op stub**

**Goal:** Land the pure-commonMain export contract and slug rule. End state: `./gradlew build` succeeds across JVM and Android targets with a working `RepoExporter` whose `isAvailable` is always `false` (no JVM actual yet). All commonTest scenarios pass against a manually-injected fake actual. Subsequent units have a stable interface to depend on while Unit 2 lands the JVM integration risk.

**Requirements:** R3 (slug rule), R6 (gate logic in commonMain), partial R2/R5 (interface shape).

**Dependencies:** None — first unit, fully testable in isolation.

**Files:**
- Create: `components/shared/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/utils/export/RepoExporter.kt` (interface, `ExportResult` sealed type with `Wrote` / `RequiresClipboard` / `Error` / `BundleCollision`, `ExportSubject` data class, `RepoExporterImpl @Inject`)
- Create: `components/shared/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/utils/export/SlugRule.kt` (pure `fun toSlug(name: String, fallbackSeed: String): String`; `const val SLUG_RULE_VERSION = 1`)
- Create: `components/shared/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/utils/export/RepoFsActual.kt` (`expect fun resolveRepoRoot(): String?`, `expect fun isValidRepoRoot(absolutePath: String): Boolean`, `expect fun writeRepoFile(absolutePath: String, content: String)`)
- Create: `components/shared/api/src/androidMain/kotlin/jez/lastfleetprotocol/prototype/utils/export/RepoFsActual.android.kt` (returns null / `false` / throws `UnsupportedOperationException`)
- Create: `components/shared/api/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/utils/export/SlugRuleTest.kt`
- Modify: `components/shared/api/build.gradle.kts` — add `commonTest.dependencies { implementation(libs.kotlin.test) }`. Mirror the `composeApp/build.gradle.kts` pattern.

**Approach:**
- `RepoExporter` interface: `val isAvailable: Boolean`, `fun export(content: String, targetSubdir: String, slug: String, replacing: ExportSubject?): ExportResult`.
- `ExportSubject(id: String, sourceKind: SourceKind)` — `SourceKind` is `enum class { ItemDefinition, ShipDesign }`. Used by the bundle-collision guard to compare incoming-id against bundled-asset-id.
- `ExportResult` sealed: `Wrote(relativeRepoPath, isOverwrite, slugRuleVersion)` / `RequiresClipboard(content, suggestionRelativePath)` / `Error(reason)` / `BundleCollision(bundledAssetName)`. Including `slugRuleVersion` in `Wrote` lets a future migration tool detect "this file was exported under v1 of the rule" if the rule changes.
- `RepoExporterImpl` composes the gate from `getPlatform()` + `resolveRepoRoot() != null` + `isValidRepoRoot(...)`. All three pass → `isAvailable == true`. With Android stub, `resolveRepoRoot()` returns null; with JVM not yet wired (Unit 2), this is also null. So Unit 1 alone produces `isAvailable == false` everywhere — UI affordances stay hidden until Unit 2 lands.
- Slug helper is a pure commonMain function. Signature: `fun toSlug(name: String, fallbackSeed: String): String`.
  - `name.lowercase()` → replace runs of `[^a-z0-9]+` with `_` → trim leading/trailing `_` → cap at 64 chars (truncate at last `_` boundary if needed) → if empty, return `"untitled-${fallbackSeed.take(8)}"`.
- Android actual returns null/false/throws (the gate prevents the write call from ever firing).

**Patterns to follow:**
- `components/shared/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/utils/FileStorage.kt` for the `expect/actual` shape.
- Existing kotlin-inject `@Inject` constructor pattern (`FileShipDesignRepository`).

**Test scenarios:**
- *Happy path (slug):* `"Heavy Cruiser"` → `"heavy_cruiser"`. `"Player Ship!"` → `"player_ship"`. `"  trailing  "` → `"trailing"`.
- *Edge case (slug, length cap):* a 200-char input is truncated at 64 chars; result still ends on a non-`_` boundary.
- *Edge case (slug, fallback):* `name=""`, `fallbackSeed="abcdef1234567890"` → `"untitled-abcdef12"`. `name="???"`, same seed → same fallback.
- *Edge case (slug, Unicode):* `name="戦艦"` → fallback.
- *Edge case (slug, path traversal):* `name="../etc/passwd"` → `"etc_passwd"`; no `..` or `/` survives.
- *Edge case (slug, lossy punctuation):* `"F-22"` → `"f_22"`. `"Mk.II"` → `"mk_ii"`. Documented loss.
- *Gate closed (commonMain):* `RepoExporter.isAvailable` is `false` when the Android actual is in play; `export(...)` returns `RequiresClipboard(...)` regardless of inputs.
- *Bundle collision logic (testable in commonMain via fake actuals):* with a fake actual that reports `isValidRepoRoot=true` and a bundled-slug-lookup helper returning `["player_ship"]` mapped to id `"X"`, calling `export(content, "default_ships", "player_ship", replacing=ExportSubject("Y", ShipDesign))` returns `BundleCollision("Player Ship")`. With `replacing.id == "X"`, the call proceeds (would-write-result via the fake actual).

**Verification:**
- `./gradlew :components:shared:api:allTests` passes.
- `./gradlew build` succeeds across JVM and Android targets.

---

- [ ] **Unit 2: JVM actual + Gradle JVM-arg injection + repo-root sentinel + atomic-rename**

**Goal:** Wire the Desktop integration. End state: from `./gradlew :composeApp:run`, `System.getProperty("lfp.repo.root")` returns the actual repo root, the sentinel check passes, and `RepoExporter.isAvailable` is `true`. From a JVM unit test against a fake repo-root, `export(...)` writes a JSON file atomically. This unit isolates the Gradle / `JavaExec` / atomic-rename integration risk so it can be verified before VM and UI work depends on it.

**Requirements:** R2 (auto-create dir, error on failure), R5 (mechanism), R6 (gate validates repo root), R7.

**Dependencies:** Unit 1.

**Files:**
- Create: `components/shared/api/src/jvmMain/kotlin/jez/lastfleetprotocol/prototype/utils/export/RepoFsActual.jvm.kt` (System.getProperty + sentinel check + atomic-rename + try/finally cleanup)
- Create: `components/shared/api/src/jvmTest/kotlin/jez/lastfleetprotocol/prototype/utils/export/RepoExporterJvmTest.kt`
- Create: `components/game-core/api/src/commonMain/composeResources/files/.gitignore` (`*.tmp` — belt-and-suspenders against accidentally-committed partial writes)
- Modify: `components/shared/api/build.gradle.kts` — add `jvmTest` sourceSet block (mirrors the `commonTest` block from Unit 1).
- Modify: `composeApp/build.gradle.kts` — add `compose.desktop.application { jvmArgs += listOf("-Dlfp.repo.root=${rootDir.absolutePath}") }`. **Verify** via a single startup log line of `System.getProperty("lfp.repo.root")`; **delete the log line before the commit lands**.

**Approach:**
- JVM actual `resolveRepoRoot`: `System.getProperty("lfp.repo.root")?.takeIf { it.isNotBlank() }`.
- JVM actual `isValidRepoRoot(path)`: returns `true` only if the path exists, is a directory, AND contains a `settings.gradle.kts` file. Defends against `lfp.repo.root=/` clearing the gate.
- JVM actual `writeRepoFile(absolutePath, content)`: order is **(1)** `Files.createDirectories(parent)`; **(2)** create `<parent>/<slug>.json.tmp`; **(3)** write content to tmp; **(4)** `Files.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING)`. Wrap (2)–(4) in `try { ... } finally { Files.deleteIfExists(tmp) }`. `IOException` / `AtomicMoveNotSupportedException` propagate up; `RepoExporterImpl` translates to `ExportResult.Error`.
- Compose Desktop Gradle wiring: DSL-first (`compose.desktop.application { jvmArgs += listOf(...) }`); if propagation fails, fall back to `tasks.named("run", JavaExec::class) { jvmArgs("-Dlfp.repo.root=${rootDir.absolutePath}") }`.

**Patterns to follow:**
- `components/shared/api/src/jvmMain/kotlin/jez/lastfleetprotocol/prototype/utils/FileStorage.jvm.kt` for the JVM actual layout.
- `composeApp/build.gradle.kts:77-79` for the `kotlin.test` sourceSet pattern.

**Test scenarios:**
- *Happy path (export):* `repo-root` set to a temp dir containing a fake `settings.gradle.kts`, `export("...", "default_parts", "heavy_cruiser", replacing=null)` writes `<root>/components/game-core/api/src/commonMain/composeResources/files/default_parts/heavy_cruiser.json` and returns `Wrote(relativePath, isOverwrite=false, slugRuleVersion=1)`.
- *Happy path (overwrite):* same call twice — second returns `Wrote(..., isOverwrite=true, ...)`; file content is the second call's payload.
- *Edge case (auto-create dir):* target parent directory does not exist; export creates it.
- *Edge case (tmp-file location):* the .tmp file's parent equals the target's parent (not `java.io.tmpdir`).
- *Edge case (tmp cleanup on success):* no `<target>.tmp` survives after success.
- *Error path (creation failure):* parent path is read-only / nonexistent root → returns `Error(...)`, NOT `RequiresClipboard` (R2 explicitly bans the fall-through for "intended-and-broken" paths).
- *Error path (write failure cleanup):* simulate `Files.move` failure (e.g., target directory deleted between createDirectories and move) — `.tmp` is removed via the finally block.
- *Gate (no repo root):* `System.getProperty` returns null → `isAvailable == false`; `export(...)` returns `RequiresClipboard(content, suggestionPath)`.
- *Gate (sentinel missing):* repo root resolves to a directory that does NOT contain `settings.gradle.kts` → `isAvailable == false`. (Defends against `lfp.repo.root=/`.)
- *Bundle collision (JVM-integration form):* slug matches a real bundled-asset slug from `composeResources/files/default_ships/` AND `replacing.id` differs → returns `BundleCollision(bundledAssetName)`. Requires the exporter to load bundled-asset id-to-slug mapping at construction.

**Verification:**
- All jvmTest scenarios pass.
- `./gradlew :composeApp:run` starts the app; the verification log line confirms `System.getProperty("lfp.repo.root")` resolves to the repo root.
- The verification log line is deleted before the commit.
- `./gradlew allTests` and `./gradlew build` succeed.

---

- [ ] **Unit 3: `ShipBuilderSideEffect` extensions + `SnackbarHost` + clipboard wiring**

**Goal:** Plumb the screen-layer side effects so the next unit's VM can emit toasts and clipboard copies. End state: triggering a fake side effect from VM tests or the app produces a visible toast and (for clipboard cases) clipboard content.

**Requirements:** R4, R5 (screen-layer infrastructure half).

**Dependencies:** None (independent of Units 1 and 2; can land in parallel).

**Files:**
- Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/entities/ShipBuilderSideEffect.kt` (add `ShowToast(text: String)`, `CopyToClipboard(text: String, toastMessage: String)`)
- Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/ShipBuilderScreen.kt` (add `SnackbarHostState` at screen root via `Scaffold` or equivalent; handle `ShowToast` via `snackbarHostState.showSnackbar(text)`; handle `CopyToClipboard` via `LocalClipboard.current.setClipEntry(...)` then snackbar)
- Modify: `components/design/src/commonMain/composeResources/values/strings.xml` (toast format strings)
- Modify: `components/design/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/design/LFRes.kt` (register the new strings under `LFRes.String`)

**Approach:**
- Two new sealed-interface entries on `ShipBuilderSideEffect`. Existing `NavigateBack` shows the dispatch pattern (`ShipBuilderScreen.kt:58`).
- `SnackbarHostState` lives at screen root; one host, no nesting. `LaunchedEffect`-style dispatch handles both new side effects alongside `NavigateBack`.
- `LocalClipboard.current` (the non-deprecated API in CMP 1.10.x) is captured at the composable layer; `CopyToClipboard` calls `clipboard.setClipEntry(ClipEntry.fromText(text))` (suspending) then `snackbarHostState.showSnackbar(toastMessage)`.
- Snackbar queueing is automatic — `showSnackbar` is suspending, so rapid exports stack up naturally without a custom queue.
- New string resources: `export_toast_new` (`"Exported to %1$s"`), `export_toast_overwrite` (`"Overwrote %1$s"`), `export_toast_clipboard` (`"JSON copied — paste into %1$s"`), `export_toast_error` (`"Export failed: %1$s"`), `export_toast_bundle_collision` (`"Slug \"%1$s\" matches bundled asset — rename to disambiguate"`).
- The VM does the format substitution and passes literal strings into `ShowToast` / `CopyToClipboard`. Strings.xml format placeholders are read by the VM via `stringResource(...)` access (see Deferred to Implementation if VM-side string-resource access requires plumbing — `LandingScreen.kt` is the pattern reference).

**Patterns to follow:**
- Existing `LandingScreen.kt` for `stringResource(LFRes.String.<id>)` lookup.
- Compose Material 3 `Scaffold` + `SnackbarHost` standard composition.
- Existing `ShipBuilderSideEffect.NavigateBack` dispatch in `ShipBuilderScreen.kt:58`.

**Test expectation:** none for this unit at the unit-test level — it's UI plumbing best verified by running the app and triggering Unit 4's intents. The visible verification is part of Unit 5.

**Verification:**
- The screen compiles cleanly across JVM and Android source sets.
- `LocalClipboard.current` import resolves (not the deprecated `LocalClipboardManager`).
- Manually exercising Unit 4's export intent produces a snackbar (and, in fallback mode, clipboard content).

---

- [ ] **Unit 4: `ShipBuilderVM` export intents + use-case wiring**

**Goal:** Add `ExportLibraryItem` and `ExportCurrentDesign` intents, wire to `RepoExporter`, map results to side effects from Unit 3. End state: VM tests can drive the export intents and observe the right side effects given a fake `RepoExporter`.

**Requirements:** R1a, R4, R5 (VM logic half), R6 (exposes gate via `state.canExport`).

**Dependencies:** Units 1, 2, and 3.

**Execution note:** Test-first. Each new intent has a clean fake-`RepoExporter` test before the wiring is written.

**Files:**
- Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/entities/ShipBuilderIntent.kt` (add `ExportLibraryItem(item: ItemDefinition)`, `ExportCurrentDesign`)
- Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/entities/ShipBuilderState.kt` (add `val canExport: Boolean = false`)
- Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/ShipBuilderVM.kt` (constructor-inject `RepoExporter`; read `repoExporter.isAvailable` once in `init { ... }` and seed `state.canExport`; handle the two new intents in `accept(...)`; map `ExportResult` to side effects)
- Modify: `composeApp/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/di/AppComponent.kt` (provide `RepoExporter` binding via `@Provides`)
- Create: `features/ship-builder/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/ShipBuilderVMExportTest.kt`

**Approach:**
- Two new intents, mirroring the shape of `DuplicateLibraryItem(item: ItemDefinition)` (existing).
- Handler: serialize the target via existing `Json` instance → derive `(content, slug)` from the item/design name and id → call `repoExporter.export(content, subdir, slug, ExportSubject(id, kind))` → translate result:
  - `Wrote(path, isOverwrite=false)` → `ShowToast(formatted "Exported to <path>")`
  - `Wrote(path, isOverwrite=true)` → `ShowToast(formatted "Overwrote <path>")`
  - `RequiresClipboard(content, suggestion)` → `CopyToClipboard(content, formatted "JSON copied — paste into <suggestion>")`
  - `Error(reason)` → `ShowToast(formatted "Export failed: <reason>")`
  - `BundleCollision(bundledName)` → `ShowToast(formatted "Slug \"<slug>\" matches bundled asset — rename to disambiguate")`
- `targetSubdir` is `"default_parts"` for items, `"default_ships"` for designs.
- VM derives the slug — passes the raw `name` and `id` into the `RepoExporter`'s slug helper invocation (so the slug source is shared between item-export and design-export paths).
- `state.canExport` is read once in `init`. Existing `_state.update { it.copy(...) }` call sites continue to work because the new field has a default.

**Patterns to follow:**
- Existing intent handling shape in `ShipBuilderVM.accept(...)`.
- `@Inject` constructor; `@Provides` shape in `AppComponent`.
- `ShipBuilderVMKeelPickerTest.kt` for `Dispatchers.setMain(UnconfinedTestDispatcher())` setup.

**Test scenarios:**
- *Happy path (item, new file):* fake returns `Wrote("default_parts/heavy_cruiser.json", isOverwrite=false)`. VM emits one `ShowToast` containing `"Exported to default_parts/heavy_cruiser.json"`.
- *Happy path (design, overwrite):* fake returns `Wrote(...isOverwrite=true)`. VM emits one `ShowToast` with the "Overwrote" prefix.
- *Happy path (clipboard fallback):* fake returns `RequiresClipboard("...", "default_parts/heavy_cruiser.json")`. VM emits one `CopyToClipboard` with both the JSON content and the formatted toast message.
- *Error path:* fake returns `Error("repo path not writable")`. VM emits one `ShowToast` containing the "Export failed" prefix and the reason.
- *Bundle collision path:* fake returns `BundleCollision("Player Ship")`. VM emits one `ShowToast` with the rename guidance.
- *Edge case (slug derivation routes through use-case):* VM passes the raw item/design name AND id into the use-case — VM does not pre-slug. Verified by inspecting captured args.
- *Edge case (gate closed at init):* `RepoExporter.isAvailable = false` at construction → `state.canExport == false` from the first emitted state.
- *Edge case (gate closed but action triggered defensively):* fake `isAvailable == false` and the export action is somehow still triggered (e.g., test bypasses the UI gate) — VM still calls the use-case, which itself returns `RequiresClipboard` per its own contract. No special-case in the VM.
- *Integration:* VM correctly serializes a `ShipDesign` via `state.toShipDesign()` before passing to the use-case (asserted by capturing the `content` arg and round-trip-decoding it).

**Verification:**
- All test scenarios pass.
- Manual run: from `./gradlew :composeApp:run`, exercising Export Design produces a `default_ships/<slug>.json` file. From IDE Run (or with `lfp.repo.root` cleared), the same action lands JSON in clipboard.

---

- [ ] **Unit 5: `PartsPanel` and ship-design save area UI affordances**

**Goal:** Surface the export action in the existing UI as a fourth stacked icon on `ItemRow` and an explicit button in the design save area. End state: a Desktop dev with the JVM arg set sees an Export icon per custom item and an "Export Design" button; on Android these affordances are absent; with the gate closed on Desktop, the affordances are still visible and produce the clipboard-fallback flow per Unit 3.

**Requirements:** R1b, R6.

**Dependencies:** Units 3 and 4.

**Files:**
- Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/composables/PartsPanel.kt` (add `onExportItem: ((ItemDefinition) -> Unit)?` param to `PartsPanel`; thread to each `ItemRow`; render as fourth stacked `LFIconButton`)
- Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/ShipBuilderScreen.kt` (wire `onExportItem` to dispatch `ExportLibraryItem`; add an "Export Design" button next to "Load Design"; gate visibility on `state.canExport`)
- Modify: `components/design/src/commonMain/composeResources/values/strings.xml` (`builder_export_item`, `builder_export_design` labels; export icon content-description)
- Modify: `components/design/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/design/LFRes.kt` (register the new strings; if a new icon is needed, register it under `LFRes.Drawable`)

**Approach:**
- `ItemRow` already takes `onDuplicate / onEdit / onDelete` as nullable callbacks rendering as stacked `LFIconButton`s. Add `onExport: (() -> Unit)?` matching that shape; bundled-catalogue items pass `null` (export disabled — they're already in the asset library); custom items pass the dispatcher.
- The icon stack in `ItemRow` adds a fourth entry — vertical stacking absorbs it without horizontal layout changes. If visual density becomes an issue post-shipping, an overflow-menu refactor is a follow-up; do not introduce it in v1 of A.
- For ship designs: a new "Export Design" button in the stats / save area, next to "Load Design", emitting `ExportCurrentDesign` on click.
- Both affordances gate on `state.canExport == true` — hidden when `false`, never greyed-out (CCD-6 "hidden, not disabled-with-tooltip"). On Android, `state.canExport` is always `false` because the JVM gate fails; on Desktop without `lfp.repo.root`, `state.canExport` is `false` and the dev sees no export buttons (correct behaviour — the IDE-Run path requires fixing the launch config rather than seeing-and-clicking-and-getting-clipboard).
  - **Trade-off note:** this means Desktop IDE-Run dev never sees the export buttons until they switch to `./gradlew :composeApp:run`. The clipboard-fallback path is then unreachable from the UI. This is intentional — making it visible would invite confusion ("I clicked Export but no file appeared"). If the dev wants to copy JSON without the Gradle launch, they can hand-edit. Implementer documents this in commit message.

**Patterns to follow:**
- Existing `onDuplicate / onEdit / onDelete` callback flow in `PartsPanel.ItemRow` (stacked `LFIconButton`s, no overflow menu).
- Existing "Load Design" button in the stats / save area (`ShipBuilderIntent.LoadDesignClicked` is dispatched from there).
- `stringResource(LFRes.String.<id>)` lookup pattern.

**Test scenarios:**
<!-- No compose-ui-test infrastructure exists in this repo; verification is structured manual checks. -->
- *Manual happy path (Desktop Gradle):* `./gradlew :composeApp:run`, open ship-builder, custom item shows Export icon; click → file appears at `<repo>/components/.../default_parts/<slug>.json`; toast confirms.
- *Manual happy path (Desktop ship export):* "Export Design" button appears next to "Load Design"; click → file in `default_ships/`.
- *Manual gate (Desktop IDE Run):* Run from IDE without `lfp.repo.root` set; export buttons should NOT appear.
- *Manual gate (Android):* No export affordances visible anywhere.
- *Manual rapid-export:* Click Export on the same item three times in quick succession; snackbars queue and display in order.
- *Manual bundle-collision:* Duplicate `Player Ship` (bundled), don't rename, click Export Design; toast surfaces the bundle-collision message and no file is written.

**Verification:**
- All manual scenarios pass.
- `./gradlew build` succeeds (no missing strings.xml entries in `LFRes`).

---

- [ ] **Unit 6: `formatVersion` drift unit test**

**Goal:** Lock the discipline. Asserts every committed `default_*.json` deserializes successfully and (for ships) carries the current `formatVersion`. Surfaces drift the moment a future schema change forgets to bump the bundled assets.

**Requirements:** R8.

**Dependencies:** None — independent of all other units.

**Files:**
- Create: `components/game-core/api/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/BundledAssetVersionTest.kt`
- Modify (if needed): `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ShipDesign.kt` to add a `companion object { const val CURRENT_VERSION = 3 }` if absent. The test references this constant rather than a magic number.

**Approach:**
- Use `Res.readBytes("files/default_ships/$name.json")` (the existing `DefaultShipDesignLoader` pattern) to read each known default ship file; `Json.decodeFromString<ShipDesign>(...)`; assert `parsed.formatVersion == ShipDesign.CURRENT_VERSION`.
- For `default_parts/` (created post-Unit-1 onward): the test enumerates whatever JSONs exist and asserts deserialization-succeeds via `Json.decodeFromString<ItemDefinition>(...)`. Day-of-merge there are zero parts files; the parts arm is a no-op with a top-of-file comment marking the gap.
- File-level comment notes that `ItemDefinition` does not currently carry a `formatVersion` field (verified at planning time); D introduces versioning when slot-based content lands and this test gains stronger coverage.

**Patterns to follow:**
- `DefaultShipDesignLoader.loadAll()` for the `Res.readBytes(...)` pattern.
- Existing `ShipSystemsTest.kt` style for kotlin-test-flavoured KMP tests.

**Test scenarios:**
- *Happy path (ships):* every known default ship file (`player_ship`, `enemy_light`, `enemy_medium`, `enemy_heavy`) deserializes successfully; `formatVersion == ShipDesign.CURRENT_VERSION`.
- *Empty-state (parts):* `default_parts/` exists but contains zero files (or doesn't exist) → test passes trivially with a single-line "no parts to validate" assertion-pass.
- *Future-state (parts present):* if any `.json` file is committed under `default_parts/`, the test reads and decodes it; failure to decode fails the test.

**Verification:**
- `./gradlew :components:game-core:api:allTests` passes.
- A deliberate manual edit to one bundled ship JSON (`formatVersion: 3` → `formatVersion: 99`) fails the test; revert before committing.

---

- [ ] **Unit 7: Export → kotlinx.serialization round-trip integration test**

**Goal:** Verify the user-facing contract that the requirements doc actually cares about: a design exported via `RepoExporter` deserializes back into an equal `ShipDesign` (and similarly for `ItemDefinition`). Closes the test-coverage gap where Unit 2 verifies write mechanics but nothing verifies that the written file is valid downstream content.

**Requirements:** R2 (the file is at the right path, in the right format), R7 (no app-data side effects observable in the round-trip).

**Dependencies:** Units 1 and 2.

**Files:**
- Create: `components/shared/api/src/jvmTest/kotlin/jez/lastfleetprotocol/prototype/utils/export/ExportRoundTripTest.kt`

**Approach:**
- Construct a representative `ShipDesign` (and a representative `ItemDefinition`) in memory.
- Serialize via the same `Json` instance used by `FileShipDesignRepository` / `FileItemLibraryRepository` — confirms A's serialization is consistent with the existing repos.
- Drive `RepoExporter.export(...)` to write the JSON to a temp dir laid out like the repo (with fake `settings.gradle.kts` sentinel).
- Read the written file back with `Files.readString(...)`.
- Deserialize via `Json.decodeFromString<ShipDesign>(...)` and assert structural equality with the original.
- Repeat for `ItemDefinition`.

This is intentionally separate from Unit 6's `BundledAssetVersionTest`. Unit 6 catches `formatVersion` drift on existing committed assets; Unit 7 catches a regression where A's write path produces JSON that Unit 6's loader-pattern can't decode (BOM, trailing whitespace, kotlinx-serialization default-omission affecting required fields, etc.).

**Patterns to follow:**
- `Json` instance pattern from `FileShipDesignRepository.kt:14-17` (`prettyPrint = true`, `ignoreUnknownKeys = true`).
- `kotlin.test.assertEquals` for structural equality on the data classes.

**Test scenarios:**
- *Happy path (ship round-trip):* a non-trivial `ShipDesign` with placed hulls, a Keel, modules, turrets → exported → re-deserialized → `assertEquals(original, decoded)`.
- *Happy path (item round-trip):* an `ItemDefinition` with vertices and attributes → exported → re-deserialized → `assertEquals(original, decoded)`.
- *Edge case (empty / minimal):* a `ShipDesign` with the minimum required fields → round-trip preserves the fields. (Catches a regression where required fields with default values get dropped during serialization.)
- *Edge case (special characters in design name):* a design with `name = "Heavy Cruiser Mk.II"` → round-trip preserves the `name` field exactly. (Catches accidental UTF-8 corruption or escape-sequence loss.)

**Verification:**
- `./gradlew :components:shared:api:jvmTest --tests "*ExportRoundTripTest*"` passes.
- Test asserts the bytes-on-disk after export, when fed through `Json.decodeFromString`, produce data classes equal to the originals.

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| The DSL-level `compose.desktop.application { jvmArgs += ... }` may not propagate to the run-task's JVM in the project's CMP 1.10.3 setup. | Plan commits to DSL-first; falls back to `tasks.named("run", JavaExec::class)`. Verification: one-off startup log line confirms `System.getProperty("lfp.repo.root")` resolves. **Verification log line is deleted before the commit lands.** |
| Atomic-rename via `Files.move` may fall back to non-atomic on certain filesystems. | JVM actual writes `<target>.tmp` *in the same parent directory* as the target, guaranteeing same-filesystem move. `try/finally` cleans up on exception. If `ATOMIC_MOVE` throws on an exotic filesystem, the wrapped exception surfaces as `ExportResult.Error` with a clear reason. |
| Snackbar / `LocalClipboard` infrastructure is genuinely new to this codebase — no existing pattern to mirror. | Both are CMP standard APIs. `LocalClipboard` (the non-deprecated form in CMP 1.10.x) is preferred over `LocalClipboardManager` to avoid landing on a deprecated surface. If integration surprises emerge in Unit 2, Unit 1 (slug + atomic write + tests) stays usable from headless tests so Units 3–5 can proceed. |
| Slug rule is lossy for semantically-meaningful punctuation (`F-22` → `f_22`, `Mk.II` → `mk_ii`). | Documented as a known limitation. Slug rule v1 prioritises portability and traversal-safety over preserving every character class. **Off-ramp explicit:** `SlugRule.kt` exposes `const val SLUG_RULE_VERSION = 1`, and `ExportResult.Wrote` carries the version field — when the rule changes, a one-shot migration tool reads existing committed slugs (all known to be v1 by the absence of any v2 marker file or inferred from git history pre-rule-bump), re-derives them under v2, and renames. **Threshold to revisit:** the first time the lossy slug actively hurts (collision, dev rename pain, or readability complaint in code review). Acceptable for v1 in solo-dev context. |
| Two custom items with the same name silently overwrite each other's exports. | Surfaced in R4 toast wording (`"Overwrote default_parts/<slug>.json"` is identical for re-export and for cross-item collision). For v1 this is accepted: the dev's `git status` is the audit trail. If it becomes a foot-gun, a future revision adds an id-mismatch warning to the overwrite path. |
| A bundled-asset slug collision (e.g., a duplicate of `Player Ship` keeping the same name) silently shadows the catalogue. | Pre-export validation in `RepoExporter` rejects writes whose slug matches a bundled asset's slug for a different logical id. Surfaces as `ExportResult.BundleCollision(bundledAssetName)` and a guidance toast. |

## Sources & References

- **Origin document:** [`docs/brainstorms/2026-04-25-asset-export-requirements.md`](../brainstorms/2026-04-25-asset-export-requirements.md)
- **Roadmap:** [`docs/brainstorms/2026-04-24-core-gameplay-roadmap-requirements.md`](../brainstorms/2026-04-24-core-gameplay-roadmap-requirements.md) (item A)
- Related code: `components/shared/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/utils/FileStorage.kt`, `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/data/FileShipDesignRepository.kt`, `composeApp/src/commonMain/kotlin/jez/lastfleetprotocol/Platform.kt`, `composeApp/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/di/AppComponent.kt`
