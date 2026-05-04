package jez.lastfleetprotocol.prototype.components.game.combat

import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Lead-aim solver: returns the world-space point a turret should aim at so a
 * bullet — which inherits the shooter's velocity at spawn and then loses
 * velocity under exponential drag — intercepts a moving target.
 *
 * **Derivation.** In the absolute frame the bullet leaves the muzzle with
 * `v0 = aimDir·muzzleSpeed + shooterVel`. Drag scales the **whole** velocity
 * vector by `exp(-k·t)`, so absolute bullet position is `v0·f(t)` where
 *
 * ```
 * f(t) = (1 - exp(-k·t)) / k        (drag enabled)
 * f(t) = t                          (k = 0)
 * ```
 *
 * Transforming to the shooter's frame (translating by `-shooterVel·t`) and
 * setting the bullet position equal to the target's shooter-frame position
 * `relPos + relVel·t` (with `relVel = targetVel - shooterVel`) gives
 *
 * ```
 * aimDir·muzzleSpeed·f(t) = relPos + relVel·t + shooterVel·(t - f(t))
 * ```
 *
 * The trailing `shooterVel·(t - f(t))` term — **missing from the original
 * formulation** — captures the shooter-frame back-drift caused by drag bleeding
 * the inherited shooter velocity out of the bullet. With `k = 0` the term
 * vanishes (`f(t) = t`); with `shooterVel = 0` it vanishes too. Both regression
 * paths reduce to the simpler classic intercept formula. With both nonzero —
 * a moving shooter firing a drag-aware projectile, the standard cruiser case —
 * the term is significant and shots without it consistently under-lead.
 *
 * The drag-aware inverse — given the shooter-frame distance the bullet must
 * cover, the time-of-flight — is `t = -ln(1 - distance·k / muzzleSpeed) / k`,
 * defined only while `distance < muzzleSpeed / k` (the asymptotic shooter-frame
 * reach). Beyond that the target is unreachable; we saturate to the maximum
 * meaningful flight time so the caller still gets a finite aim point (the
 * existing per-turret range gate handles fire suppression).
 *
 * The world-space aim point we return — `targetPos + targetVel·t - shooterVel·f(t)` —
 * is the point the turret should aim at; the absolute bullet velocity from
 * that aim direction (plus inherited shooter velocity) intercepts the target.
 */
object LeadAim {
    private const val ITERATIONS = 5
    private const val MAX_DRAG_RATIO = 0.999f

    fun computeAimPoint(
        turretPos: SceneOffset,
        shooterVelocity: SceneOffset,
        targetPos: SceneOffset,
        targetVelocity: SceneOffset,
        muzzleSpeed: Float,
        dragK: Float,
    ): SceneOffset {
        if (muzzleSpeed <= 0f) return targetPos

        val relPosX = targetPos.x.raw - turretPos.x.raw
        val relPosY = targetPos.y.raw - turretPos.y.raw
        val relVelX = targetVelocity.x.raw - shooterVelocity.x.raw
        val relVelY = targetVelocity.y.raw - shooterVelocity.y.raw
        val shootX = shooterVelocity.x.raw
        val shootY = shooterVelocity.y.raw

        var t = sqrt(relPosX * relPosX + relPosY * relPosY) / muzzleSpeed
        repeat(ITERATIONS) {
            val fT = if (dragK > 0f) (1f - exp(-dragK * t)) / dragK else t
            val gT = t - fT // shooter-velocity decay correction factor
            val ax = relPosX + relVelX * t + shootX * gT
            val ay = relPosY + relVelY * t + shootY * gT
            val distNeeded = sqrt(ax * ax + ay * ay)
            t = if (dragK > 0f) {
                val ratio = (distNeeded * dragK / muzzleSpeed).coerceAtMost(MAX_DRAG_RATIO)
                -ln(1f - ratio) / dragK
            } else {
                distNeeded / muzzleSpeed
            }
        }

        val finalFT = if (dragK > 0f) (1f - exp(-dragK * t)) / dragK else t
        return SceneOffset(
            (targetPos.x.raw + targetVelocity.x.raw * t - shooterVelocity.x.raw * finalFT).sceneUnit,
            (targetPos.y.raw + targetVelocity.y.raw * t - shooterVelocity.y.raw * finalFT).sceneUnit,
        )
    }
}
