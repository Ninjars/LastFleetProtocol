## AI Architecture Guide

This document helps agents make architecture-safe changes in this repository.

## Canonical Module Graph

Defined in `settings.gradle.kts`:

- `:composeApp`
- `:components:design`
- `:components:shared:api`, `:components:shared:impl`
- `:components:game-core:api`, `:components:game-core:impl`
- `:components:preferences:api`, `:components:preferences:impl`
- `:features:splash:api`, `:features:splash:impl`
- `:features:landing:api`, `:features:landing:impl`
- `:features:game:api`, `:features:game:impl`

## Dependency Direction Rules

### Preferred Direction

- UI/app composition lives in `:composeApp`.
- Feature and component public contracts live in `api` modules.
- Generic UI components should be defined in `:components:design` to be shared across feature
  modules
- Generic logic, such as helper functions or basic data classes can be defined in
  `:components:shared:api`.
- Implementations live in `impl` modules.
- Callers should depend on `api` contracts where possible.

### Current Reality Notes

- `:composeApp:commonMain` currently imports both `api` and `impl` modules for
  composition/integration.
- Some dependency edges are pragmatic rather than ideal (for example, there are places where `api`/
  `impl` layering is not strict).
- `:feature:game` currently contains logic that should be in `:components:game-core`.

When working on ordinary feature tasks, preserve existing behavior and module wiring unless the task
explicitly requests architecture refactoring.

## Compose + KMP Placement Rules

- Code should be added to appropriate modules depending on the feature or functionality being worked
  on.
- Use platform source sets (`androidMain`, `jvmMain`, etc.) only when platform APIs are required.
- Keep platform-specific code minimal and isolated behind clear interfaces.

## MVI & ViewModel Boundaries

Use these boundaries when implementing changes:

- ViewModel:
    - receives UI intents,
    - updates state flow,
    - emits side-effects via channel.
- UI Composable:
    - reads state,
    - dispatches intents,
    - does not contain business logic.
- Use cases:
    - host business rules and data orchestration,
    - are invoked by ViewModels or other use cases.

## Safe Change Patterns

- Add new behavior in `impl`, expose only required contracts in `api`.
- Keep constructor/API signatures stable unless task explicitly includes breaking changes.
- If changing cross-module contracts, update all affected modules in one cohesive change.
- Prefer incremental refactors over broad restructures.

## High-Risk Patterns to Avoid

- Adding new dependency edges just to “make code compile quickly”.
- Moving game/simulation logic into UI layer.
- Creating platform-specific logic in `commonMain` without abstraction.
- Replacing existing patterns with a different architecture style mid-feature.

## Quick Pre-Edit Checklist

Before editing:

1. Which module owns the behavior?
2. Is this API contract change or implementation-only change?
3. Does this belong in `commonMain`?
4. Will Kubriko runtime constraints be affected?
5. Are docs or contracts now outdated?
