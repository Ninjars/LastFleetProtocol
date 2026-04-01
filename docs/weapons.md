# Weapon Systems

Weapons come in a number of base types, corresponding to the type of attack they can perform.

- Kinetic: machine guns, autocannons, railguns, penetrator missiles. These weapons track
  ammunition, may need to reload, and fire unguided kinetic projectiles. Kinetic weapons tend to
  have some measure of inaccuracy, meaning the projectile may deviate from the direction the
  weapon is facing.
- Concussive: flack cannons, explosive missiles. Similar to Kinetic weapons, but use proximity
  detonation to deal damage across an area, improving their chance of hitting a target whilst
  having lower armour penetration.
- Beam: lasers, ion cannons. A type of energy weapon. These weapons generate a lot of heat on
  the firing ship to draw a line of damage directly, accurately and immediately to their target.
  Energy weapons interact differently to armour than other weapon types.
- Thermal: plasma cannons, flame throwers, incendiary missiles. These weapons tend to be shorter
  range due to the nature of their projectiles. Rather than trying to defeat enemy armour they
  focus on delivering thermal energy to the target to overload internal systems.

#### Kinetic projectiles

Fired from cannons, machine guns and similar weapons, or at point blank range by a penetrating
missile, kinetic projectiles inherit the velocity of the ship firing them and travel in simple
trajectories.
The trajectory is based on the firing weapon's facing, with an amount of inaccuracy determined by
the weapon's accuracy stat.
When a kinetic projectile collides with a valid target target (a ship on the opposite team) a
sequence of checks are resolved.

- Check if the projectile hits: random variable + projectile "to hit" modifier vs target's "evasion"
  modifier.
    - If miss, the check sequence terminates and projectile continues on and may impact other ships.
- If hit, check for a ricochet by checking the angle of incidence of the impact, factoring in the
  armour segment's slope and hardness attributes, and a small random variable.
    - if angle is too shallow given the armour's hardness the projectile ricochets and disintegrates
      harmlessly, terminating the check sequence.
- If the projectile catches, check to see if the round penetrates, by comparing the projectile's
  armour piercing attribute to the armour's hardness attribute, and a small random variable.
    - If the projectile fails to penetrate the armour it disintegrates harmlessly, terminating the
      check sequence.
- If the round penetrates a "penetrating hit" is resolved, using the projectile's damage and armour
  piercing attributes, to find the damage inflicted on the target's internal systems.
- See "Penetrating hits" section.

#### Concussive projectiles

Fired from flack cannons, point defence guns, or detonated at short range by explosive missiles,
concussive projectiles largely behave similarly to Kinetic projectiles, with the important
distinction that they detonate when close to a target, improving their chance to hit by trading
against having less armour piercing.

Concussive projectiles and missiles have a cone-shaped trigger zone they project in their direction
of travel. When this collides with a valid target (which might vary depending on the weapon type,
with some projectiles detonating on missiles in addition to ships).

When a concussive projectile is triggered, it creates an explosion visual effect that follows the
impact zone, modelled as a rapidly expanding circle that inherits the projectile velocity. The
trigger cone dimensions roughly match the velocity and explosion expansion rate.

The explosion circle then tests for hits against valid targets, similarly to the kinetic projectile.
A key difference is there's no check for ricochet, only penetration, and if the blast penetrates it
affects systems based on the explosion's radius.

- Check if the explosion hits: random variable + projectile "to hit" modifier vs target's "evasion"
  modifier.
    - If miss, the check sequence terminates and explosion continues on and may impact other ships.
- If the explosion hits, check to see if the blast penetrates, by comparing the explosion's
  armour piercing attribute to the armour's hardness attribute, and a small random variable.
    - If the explosion fails to penetrate the armour terminate the
      check sequence. The explosion may continue on to affect other ships.
- If the explosion penetrates a "penetrating blast" is resolved, using the explosion's damage
  attribute, to find the damage inflicted on the target's internal systems.
- See "Penetrating blasts" section.

#### Thermal projectiles

TODO

#### Beam attacks

Beam weapons check their angle of incidence with the surface of the armour and the armour's
slope to see how much of their beam or projectile's energy is transferred to the target, then
compare that to the armour's thermal resistance to see how much damage is dealt to that armour
section.

#### Penetrating hits

- Inflicted by kinetic projectiles that defeat the armour of a target.
- A line is drawn from the point of impact in the direction of the projectile's vector within the
  bounds of the target ship, with a small random deviation to represent the projectile scattering on
  impact.
- Each internal system the line crosses will receive a portion of the projectile's damage, in the
  order in which they are encountered.
- The first non-destroyed system receives damage as a proportion of the projectile impact's damage
  value based on the system's hardness value compared to the projectile's penetration value
- The damage dealt to the first system is subtracted from the remaining impact damage, then the next
  non-destroyed system performs the same check with the damage dealt being based on the remaining
  impact damage.
- If the line reaches the exterior of the ship with leftover impact damage over a minimum
  threshold (threshold based on the terminating armour segment's hardness), the ship suffers an
  "overpenetration hit" event, causing further complications.

#### Penetrating blasts

- inflicted by concussive explosions that defeat the armour of a target.
- check for internal systems that overlap with the explosion's circle, and inflict the explosion's
  damage to each of the colliding systems.