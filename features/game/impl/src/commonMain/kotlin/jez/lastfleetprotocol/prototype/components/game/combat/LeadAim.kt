package jez.lastfleetprotocol.prototype.components.game.combat

import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Lead-aim solver: returns the world-space point a turret should aim at so a
 * bullet (which inherits the shooter's velocity at spawn) intercepts a moving
 * target.
 *
 * **Derivation.** Working in the shooter's frame of reference, the target moves
 * at `relVel = targetVel - shooterVel`, and the bullet leaves the muzzle at
 * speed [muzzleSpeed] in its aim direction. Setting bullet position equal to
 * target position at impact time `t`:
 *
 * ```
 * aimDir * D(t) = relPos + relVel * t
 * ```
 *
 * where `relPos = targetPos - turretPos` and `D(t)` is the bullet's travelled
 * distance in the shooter's frame:
 * - `D(t) = muzzleSpeed * t`                              (no drag)
 * - `D(t) = (muzzleSpeed / k) * (1 - exp(-k * t))`        (exponential drag)
 *
 * The drag-aware inverse — given the distance the bullet must cover in shooter
 * frame, the time-of-flight — is `t = -ln(1 - distance * k / muzzleSpeed) / k`,
 * defined only while `distance < muzzleSpeed / k` (the asymptotic shooter-frame
 * reach). Beyond that the target is unreachable; we saturate to the maximum
 * meaningful flight time so the caller can still compute a finite aim point
 * (the existing per-turret range gate handles the actual fire suppression).
 *
 * The world-space aim point we return — `targetPos + relVel * t` — already
 * accounts for the shooter's own motion, so the turret can pass it directly to
 * `angleTowards()` to get the muzzle angle.
 */
object LeadAim {
    private const val ITERATIONS = 4
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

        var t = sqrt(relPosX * relPosX + relPosY * relPosY) / muzzleSpeed
        repeat(ITERATIONS) {
            val futX = relPosX + relVelX * t
            val futY = relPosY + relVelY * t
            val distNeeded = sqrt(futX * futX + futY * futY)
            t = if (dragK > 0f) {
                val ratio = (distNeeded * dragK / muzzleSpeed).coerceAtMost(MAX_DRAG_RATIO)
                -ln(1f - ratio) / dragK
            } else {
                distNeeded / muzzleSpeed
            }
        }

        return SceneOffset(
            (targetPos.x.raw + relVelX * t).sceneUnit,
            (targetPos.y.raw + relVelY * t).sceneUnit,
        )
    }
}
