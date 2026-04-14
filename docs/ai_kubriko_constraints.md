## AI Kubriko Constraints Guide

Guidance for tasks that interact with Kubriko-powered game runtime behavior.

## Why This Matters

Kubriko gives strong capabilities (rendering, plugins, runtime tooling), but real-time systems are sensitive to lifecycle and state-management mistakes. Agents should optimize for correctness and stability first.

## Core Constraints

- Treat game-loop logic as high-risk and isolate edits.
- Keep deterministic simulation logic separated from presentation/UI logic.
- Avoid triggering runtime side effects from unstable composition paths.
- Reuse established plugin wiring patterns already present in project modules.

## Rendering & Simulation

- Keep update rules predictable and consistent across frames.
- Avoid adding unnecessary allocations/work inside hot paths.
- Prefer explicit state transitions over hidden mutable coupling.
- If changing ordering of update/render operations, flag as high-impact in summary.

## Actor Drawing Coordinate System

- An actor's `DrawScope.draw()` operates in **body-local coordinates**, already translated and rotated to match the actor's `body.position` and `body.rotation`. Do not apply the body's transform again inside `draw()`.
- The canvas bounds match the actor's `body.size` (the unrotated bounding box). The origin `(0, 0)` is the **top-left corner** of the bounding box — all drawing coordinates should be relative to that corner.
- **Drawing is clipped to `body.size`.** Anything drawn outside the bounding box is not visible. If an element needs to render beyond the actor's bounds (e.g., a heading indicator extending past the hull nose), it must be a **separate child actor** with its own body and draw scope.
- `body.pivot` determines the rotation centre. Set it to `body.size.center` for rotation around the geometric centre.

## Loading Behavior

- Loading flow should be explicit and observable.
- Avoid hidden async chains that can leave loading state unresolved.
- Keep loading state transitions routed through ViewModel/use-case boundaries where possible.

## Audio Behavior

- Audio triggers should come from intentional game events, not incidental recompositions.
- Guard against repeated play/stop calls caused by rapidly changing UI state.
- Keep long-lived audio lifecycle ownership clear (who starts/stops, and when).

## Plugin Usage Expectations

Project currently uses Kubriko engine plus multiple plugins/tools (including audio, pointer, sprites, physics/collision, and logging/tooling in some modules).

When adding behavior:

- Prefer extending existing plugin-based architecture over custom engine bypasses.
- Do not duplicate plugin responsibilities in ad-hoc code.
- Keep plugin configuration close to owning module runtime setup.

## Cross-Platform Considerations

From game design constraints:

- JVM/Web battle orientation is landscape.
- Mobile can rotate to portrait-oriented battle presentation.

Agents should avoid hard-coded assumptions that break orientation adaptability or target-specific runtime behavior.

## Pre-Edit Checklist for Kubriko Tasks

1. Is this a simulation change, rendering change, loading change, or audio change?
2. Could this be affected by recomposition/lifecycle timing?
3. Does this belong in game-core/feature runtime code rather than UI layer?
4. Are platform/orientation constraints still respected?
5. What is the rollback strategy if runtime behavior regresses?
