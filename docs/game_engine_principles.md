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
