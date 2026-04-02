# Project Instructions for AI Agents

This file is the canonical entrypoint for AI assistants working in this repository.

## 1) Project Snapshot

- Kotlin Multiplatform game project (Android + Desktop/JVM currently active; iOS scaffolding is present but commented in places).
- UI stack is Compose Multiplatform with Material 3.
- Architecture is modular and mostly split into `api` and `impl` modules.
- Real-time 2D game rendering/simulation uses Kubriko.

## 2) Core Rules (Always Follow)

- Use Kotlin for all new source files.
- Prefer putting cross-platform logic in `commonMain`.
- Respect module boundaries. Do not add convenience dependencies that bypass feature/component APIs.
- Follow existing MVI conventions:
  - ViewModels should align with `LFViewModel` patterns.
  - UI observes state via `Flow<State>`.
  - UI emits feature-scoped intents.
  - Side effects are emitted via channel and handled at navigation layer.
- Keep business logic in use cases (not directly in Composables).

## 3) Module Topology (Operational Guidance)

Defined in `settings.gradle.kts`:

- App composition:
  - `:composeApp`
- Components:
  - `:components:design`
  - `:components:shared:api`, `:components:shared:impl`
  - `:components:game-core:api`, `:components:game-core:impl`
  - `:components:preferences:api`, `:components:preferences:impl`
- Features:
  - `:features:splash:api`, `:features:splash:impl`
  - `:features:landing:api`, `:features:landing:impl`
  - `:features:game:api`, `:features:game:impl`

Current integration style:

- `:composeApp:commonMain` imports both feature/component `api` and `impl` modules and acts as composition root.
- Feature `impl` modules generally depend on their own `api` module plus component `api` modules.

Important: Some modules may currently have non-ideal dependency edges. Preserve behavior unless task explicitly includes refactoring.

## 4) Kubriko Constraints

When touching game runtime code:

- Treat simulation/render loop changes as high-risk and validate across targets.
- Keep deterministic gameplay logic separated from presentation concerns.
- Be careful with loading and audio lifecycle behavior; avoid side effects from unstable composition points.
- Prefer extending existing Kubriko plugin/tool usage patterns over introducing ad-hoc engine glue.

Kubriko references:

- Local clone: `/Users/jez/dev/multiplatform/kubriko-main`
- Docs: `https://github.com/pandulapeter/kubriko/tree/main/documentation`

## 5) Task Routing (Where Agents Should Read First)

Start with `docs/ai_index.md`, then follow these paths:

- Architecture/module dependency tasks → `docs/ai_architecture.md`
- Implementation planning/execution tasks → `docs/ai_workflows.md`
- Game-loop/rendering/audio/loading tasks → `docs/ai_kubriko_constraints.md`
- Domain design/mechanics tasks →
  - `docs/game_design.md`
  - `docs/ship.md`
  - `docs/weapons.md`

## 6) Standard Execution Contract for Agents

For non-trivial tasks:

1. Confirm scope and affected modules.
2. Identify architecture constraints before editing.
3. Prefer minimal, local changes over broad rewrites.
4. Update docs when behavior/contracts change.
5. Validate with targetted checks first, then broader checks as needed.

## 7) Definition of Done for AI Changes

A task is done when:

- Changes compile (or are logically consistent if compile is not possible in-session).
- Module boundaries and architectural intent are preserved.
- New/changed behavior is documented when needed.
- The final summary clearly states:
  - what changed,
  - where it changed,
  - what constraints were considered,
  - what validation was performed.
