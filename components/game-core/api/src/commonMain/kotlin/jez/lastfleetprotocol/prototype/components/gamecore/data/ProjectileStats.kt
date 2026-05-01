package jez.lastfleetprotocol.prototype.components.gamecore.data

import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.ln

/**
 * Stats for a kinetic projectile, used for impact resolution.
 *
 * **Drag model (item C — battle-feel pass).** When [dragK] > 0, the projectile's
 * velocity decays each frame as `velocity *= exp(-dragK * dt)` (`dt` in seconds).
 * When the current speed drops below [expirationVelocityFraction] * [speed], the
 * projectile expires — replacing [lifetimeMs] as the *primary* expiration mechanism.
 * [lifetimeMs] is retained as a safety cap for legacy projectiles and as a
 * defensive ceiling for drag-aware projectiles that never quite reach the
 * velocity threshold.
 *
 * Drag is intentionally first-order linear in velocity, not the physically-correct
 * quadratic `F = ½ρv²C_dA` — simplification chosen for the prototype. The integrated
 * effective range follows from `v(t) = v0·exp(-k·t)`:
 *
 * ```
 * effectiveRange = ∫₀^T v(t) dt where v(T) = expirationFraction * v0
 *                = (v0 / k) * (1 − expirationFraction)
 * ```
 *
 * Default [dragK] = 0 and [expirationVelocityFraction] = 0 preserve legacy
 * behaviour: no drag, no velocity-based expiration; range governed by [lifetimeMs].
 *
 * The brainstorm refers to the base penetration value as `basePenetration`; in this
 * codebase that quantity is [armourPiercing] — the names refer to the same field.
 */
@Serializable
data class ProjectileStats(
    val damage: Float,
    val armourPiercing: Float,
    val toHitModifier: Float,
    val speed: Float,
    val lifetimeMs: Int,
    val dragK: Float = 0f,
    val expirationVelocityFraction: Float = 0f,
)

/**
 * Effective range in metres, computed from drag tuning when both [ProjectileStats.dragK]
 * and [ProjectileStats.expirationVelocityFraction] are positive — uses the integrated
 * distance from `v(t) = v0·exp(-k·t)`. Falls back to the legacy lifetime-based range
 * (`speed * lifetimeMs / 1000`) when drag is unconfigured, so existing projectile
 * configurations keep their current effective range.
 */
fun ProjectileStats.effectiveRangeM(): Float =
    if (dragK > 0f && expirationVelocityFraction > 0f) {
        (speed / dragK) * (1f - expirationVelocityFraction)
    } else {
        speed * lifetimeMs / 1000f
    }

/**
 * Current speed at time [tSeconds] under exponential drag, given [ProjectileStats.speed]
 * as the muzzle velocity. Closed-form helper for tests and tuning calculations;
 * the in-flight `Bullet` integrates per-frame via `velocity *= exp(-dragK * dt)`
 * directly rather than calling this. Returns [ProjectileStats.speed] unchanged
 * when [ProjectileStats.dragK] is zero.
 */
fun ProjectileStats.speedAt(tSeconds: Float): Float =
    if (dragK > 0f) speed * exp(-dragK * tSeconds) else speed

/**
 * Time in seconds until the projectile's velocity decays to [ProjectileStats.expirationVelocityFraction]
 * of the muzzle speed. Closed-form tuning helper — useful when picking drag
 * coefficients against a target effective-range time-of-flight. Not called from
 * `Bullet.update`, which checks the current-velocity threshold each frame.
 * Returns `Float.POSITIVE_INFINITY` when drag is unconfigured.
 */
fun ProjectileStats.timeToExpirationVelocity(): Float =
    if (dragK > 0f && expirationVelocityFraction > 0f) {
        -ln(expirationVelocityFraction) / dragK
    } else {
        Float.POSITIVE_INFINITY
    }
