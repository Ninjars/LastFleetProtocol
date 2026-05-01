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

## Projectile drag (item C — battle-feel pass)

Kinetic projectiles slow down in flight under exponential drag and expire when their velocity drops below a configurable fraction of the muzzle speed. Drag is configured per weapon in `turret_guns.json` via two new optional fields on `ProjectileStats`:

| Field | Type | Default | Meaning |
|---|---|---|---|
| `dragK` | Float | `0f` | Drag coefficient. Per-frame: `velocity *= exp(-dragK * dt_seconds)`. `0f` disables drag (legacy behaviour — projectile range governed by `lifetimeMs` only). |
| `expirationVelocityFraction` | Float | `0f` | Fraction of muzzle speed at which the projectile expires. `0.3f` = expire when current speed drops to 30 % of muzzle. `0f` disables velocity-based expiration. |

Both fields must be > 0 for drag-aware expiration to kick in; otherwise the projectile falls back to the legacy `lifetimeMs` countdown. `lifetimeMs` is retained as a safety cap regardless of drag — the projectile expires on whichever fires first.

### Effective range — closed form

Integrated distance from `v(t) = v0·exp(-k·t)`, between `t = 0` and `t = -ln(expirationFraction) / k`:

```
effectiveRangeM = (muzzleSpeed / dragK) * (1 - expirationVelocityFraction)
```

Inverse — picking `dragK` for a target effective range:

```
dragK = muzzleSpeed * (1 - expirationVelocityFraction) / desiredRange
```

Worked example: `muzzleSpeed = 600 m/s`, `expirationVelocityFraction = 0.3`, `desiredRange = 3500 m` → `dragK = 600 * 0.7 / 3500 ≈ 0.120`. The bullet reaches its target at ~30 % muzzle speed, then expires shortly after if it overshoots.

The same calculation drives `ProjectileStats.effectiveRangeM()`, used by AI to decide when to fire (per-turret range gate) and to set orbit-engagement distance (`Ship.maxTurretEffectiveRangeM()` × `BasicAI.ORBIT_RANGE_FRACTION`).

### Velocity-aware penetration

`Bullet` computes `currentPenetration = armourPiercing * (currentSpeed / muzzleSpeed)` each frame and threads it into `KineticImpactResolver.resolve(...)`. At the muzzle, penetration equals the weapon's stated `armourPiercing`; at long range, it has decayed proportionally to current velocity. The penetration formula is linear; item E may revise the curve as a coordinated C+E change.
