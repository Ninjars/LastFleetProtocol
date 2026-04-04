# Game Engine Principles

General baseline principles to guide implementing functionality within the game feature or
game-core.

The game engine should be designed to have modular systems that interact with each other via
interfaces that follow a presenter pattern, ie that expose public state and present a limited number
of functions to interact with their internal state.

The aim is to make it easy to add additional functionality by adding an additional system to an
existing entity, or by adding new effects that interact with an existing system.

Examples of such systems might be the Heat system. Ship modules are able to generate and vent Heat.
The ship has a Heat system, which tracks the current value and has thresholds at which consequences
take place, affecting ship modules. The implementation for the consequences is handled the Heat
System, not by each module, isolating that complexity.

Another example might be the Projectile Impact system. Piercing Kinetic projectiles have a
particular sequence of interactions with the ship and its modules that differ to a Concussive
impact, and differ again to an Explosive Kinetic projectile impact. The ship and its modules and
armour segments do not know about these different sort of impacts, they just present values that the
impact system can use to determine the outcome, and present function calls to update them with the
results of the outcome. This allow additional projectile types to be implemented later with minimal
impact on the broader codebase.

Game entities such as ships, modules, weapons and projectiles should avoid hard-coding or providing
default values for game-related numerical, presentation or text properties. All game-affecting
values should be provided as parameter data classes, with the aim being to have a single collection
of data files defining the stats for all the game entities to make it easier to make consistent
adjustments without there being unexpected values defined elsewhere.

Where assumptions are being baked into the implementation these should be commented clearly in the
code as well as a reference appended explaining the trade-off in `docs/tech-debt.md` in a section
headed `Assumptions`.

# Coordinate System and Orientation

Kubriko uses a standard 2D coordinate system with atan2 conventions:

- **+X axis is "forward"** (rotation = 0 radians). This is the direction a ship faces at zero
  rotation.
- **+Y axis is "down/starboard"** in screen space (Y increases downward).
- **Rotation** is measured in radians using `atan2(dy, dx)` convention: 0 = right (+X),
  PI/2 = down (+Y), PI = left (-X), -PI/2 = up (-Y).
- **`angleTowards(target)`** returns `atan2(target.y - origin.y, target.x - origin.x)`, giving the
  angle from the origin to the target in this convention.
- **`SceneOffset.rotate(angle)`** performs standard 2D rotation:
  `x' = x*cos - y*sin, y' = x*sin + y*cos`.

When defining ship geometry, thrust directions, or arc calculations:

- **Forward thrust** in local space is `SceneOffset(1, 0)` (positive X).
- **Lateral thrust** in local space is `SceneOffset(0, ±1)` (positive/negative Y).
- **Hull vertices** should have the nose/prow at positive X, rear at negative X, and
  port/starboard along the Y axis.
- **Arc calculations** (e.g., for damage routing) use `atan2(localY, localX)` where angle 0
  is forward (+X).

Ship sprites may point in a different direction (e.g., upward in the image file). The sprite
orientation is handled by the rendering system's rotation — the logical forward direction is
always +X regardless of the sprite's native orientation.
