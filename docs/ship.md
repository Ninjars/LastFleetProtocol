# Ships

- A Ship consists of collection of internal systems, weapon slots with mounted weapons, a perimeter
  of armour with multiple segments, a crew with experience points distributed across the different
  internal systems (providing efficiency buffs), and a captain providing a morale attribute and
  potentially a special ability.
- The ship is modelled as a collection of connected convex hulls, facilitating 2d collision
  detection.
- Each hull is a convex polygon, made from a number of vertices.
- Each line segment of the hull polygon is classified as either armoured or unarmoured. Generally,
- lines which overlap or touch other hull polygons will be unarmoured.
- The external perimeter of the hull is generally armoured, with each segment of the perimeter able
  to be assigned a different collection of armour attributes.

# Movement

- A ship's movement is physically simulated, with mass and momentum affecting movement and rotation.
- Ships may only accelerate at their maximum rate in the direction they are facing, with much
  reduced acceleration/deceleration in other directions.
  This makes positioning important, and allows for fast ships to meaningfully out-manoeuvre larger,
  slower ones.
- The ship's engine and aux engines provide acceleration force values in each cardinal direction,
  with the main engine mostly providing acceleration forwards.
- The engines also provide angular forces, used to rotate the ship.
- The ship's hull, armour, and internal systems are used to calculate the ship's total mass.
- When moving, the ship's acceleration in the vector relative to its current facing and its total
  mass are used to find its actual acceleration.
- Ships move towards a target position. This position may be relative to an object with velocity.
  The ship calculates its relative velocity to the target position and, if appropriate, object,
  checks the distance, and calculates its rate of acceleration and deceleration in that vector,
  given its current heading. The ship should determine if it would be faster to rotate to a
  different facing to get a better rate of acceleration in the target vector, or whether it would
  take less time to maintain its current facing. This is used to determine the ship's direction of
  acceleration so that it will come to rest at the target position, or maintain its position
  relative to the target object.

# Internal Systems

Ships have a number of internal systems, modelled as collision boxes within the ship's bounds.
Internal systems include:

- Reactor (if destroyed the ship explodes. If disabled the ship loses power.)
- Damage Control (facilitates self-repair)
- Magazine (required to provide ammunition to ammunition-based weapons. Causes collateral damage
  when destroyed.)
- Engineering (required to manage ship heat)
- Ablative Armour (high-hardness system that exists purely to soak up penetrating damage)
- Heat Sink (increases the ship's heat capacity)
- Emergency Battery (provides a short period of power to the ship if the Reactor is disabled)
- Aux Reactor (increases the number of beam weapons that can be installed. Causes collateral damage
  when destroyed.)

Each system has:

- a density attribute, which affects how much of a penetrating hit's damage it absorbs when hit
- a number of hit points, to determine at what point the system is considered destroyed
- a mass attribute, used to calculate the ship's total mass
- a restart time, used when transitioning from disabled to active
- a repair priority, used to choose which system to repair first

Additionally, internal systems have a number of features:

- immune to being disabled. Eg, Emergency Battery and Ablative Armour.

## Disabling Systems

Internal systems can be disabled by:

- receiving damage equal to 2/3 of their maximum hit points
- the ship losing power (eg reactor disabled)
- the ship entering "heat excess" state
- a special weapon effect

A disabled system will begin to restart once the condition disabling it is cleared. Eg, power
restored, or hit points repaired.

## Control Systems

These are Internal Systems with special rules that affect the ship's behaviour. If there are
multiple control systems for a particular function then the ship can continue with that behaviour if
one of those systems is rendered inactive.

### Bridge

The Bridge is a multi-functional control system.

Increases the threshold for the ship to retreat.

### Command

Shared with bridge:

- Update movement targets
- Receive player commands
- Trigger special abilities

Increases the threshold for the ship to retreat.

### Fire Control

Selects targets for weapon slots. Provides accuracy improvements in calculating aiming positions.

### Signals

Provides electronic warfare capabilities. Reduces aiming position accuracy of enemy ships.
Can cause missiles to become disabled and veer off course.

## External Systems

These systems have to be placed in contact with a section of external hull. They may override the
armour value of the hull where their bounds contact the hull, modelling weak points in the armour.

- Main Engine (required to accelerate forward)
- Aux Engine (may be multiple of this system around the ship to represent manoeuvring drives;
  required to accelerate/decelerate in directions not covered by main engine)
- Radiator (increases the rate at which stored heat is vented, once deployed)

## Weapon Slots

- Weapon slots are a sub-type of system, and can be hit and disabled like a normal system.
- Weapon slots also are armoured, in the same way as the external hull is, inheriting the ship's
  base armour stats and allowing individual systems to override that. This is to allow weapons
  placed along the exterior of the hull to be treated as extensions of the hull.
- Weapon slots have a number of sub-types:
    - Small turret
    - Medium turret
    - Large turret
    - Small fixed
    - Medium fixed
    - Large fixed
    - Super-heavy fixed
- Turret slots have a range of motion (minimum and maximum rotation), a maximum rate of rotation,
  and a default facing within that range of motion. The range of motion is relative to the ship's
  facing.
- Fixed slots have a fixed facing relative to the ship that the mounted weapon is aligned to. This
  might be a "spinal" mounted weapon trading off arc of fire for carrying heavier firepower on a
  smaller ship, or could be torpedo launch tubes, or other similar situations.
- More information on the actual weapon systems that can be mounted on a weapon slot see
  `weapons.md`

# Heat Management

Ships have a "heat vent" attribute and a "heat capacity" attribute. A ship may have heat up to its
heat capacity with no issue, and heat is removed from the heat capacity continuously at the "heat
vent" rate.

- Some internal systems have a rate at which they generate heat whilst active,
  such as the reactor and engines.
- There may be a different heat generation rate depending on the system being "active" (ie, not
  disabled, destroyed, or deactivated) vs being "used"; for instance, the reactor generates heat
  whilst active, but engines generate heat whilst accelerating and weapon systems generate heat
  whilst firing.
- The Heat Sink internal system increases the ship's heat capacity (whilst it's not destroyed)
- The Radiator internal system increases the rate at which stored heat is vented, if deployed and
  not destroyed.
    - Deploying the radiator disables adjacent armour sections, making the ship more vulnerable.
    - The radiator is automatically triggered when the ship's heat capacity is about to be exceeded.
- Some weapons, particularly the Beam weapons, generate a lot of heat when they fire.
- Some projectiles, particularly Thermal projectiles, inflict heat on the target when they impact.
- When the ship is at full heat capacity and the incoming heat exceeds the heat vent rate, the ship
  enters an "excess heat" state
- When in excess heat state systems which generate heat become disabled, starting with weapon
  systems, then engines, then the reactor.
- If the excess heat reaches a threshold 50% above the heat capacity the ship's reactor explodes.
- Once stored heat drops below the heat capacity disabled systems begin coming back online. Weapon
  systems come back online quickly, engines more slowly, the reactor taking the longest to restart.

# Armour

- Ship armour is modelled as a perimeter of armour sections
- A ship will have a "default armour" stat, that is applied to each external armour section as the
  baseline, then individual sections may have an optional overriding stat block on an individual
  basis.
- An armour section has a collections of stats, used primarily for resolving weapon impacts:
    - hardness: increases chance of deflecting impacts
    - density: increases the amount of damage absorbed by the armour by impacts, and also how much
      mass the armour section adds to the ship
    - thermal resistance: decreases the amount of heat received from thermal weapon strikes
