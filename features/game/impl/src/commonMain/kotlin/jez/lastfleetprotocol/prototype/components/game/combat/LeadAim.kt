package jez.lastfleetprotocol.prototype.components.game.combat

import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Lead-aim solver: returns the world-space point a turret should aim at so a
 * bullet — which inherits the shooter's velocity at spawn and then loses
 * velocity under exponential drag — intercepts an accelerating target.
 *
 * **Bullet kinematics.** In the absolute frame the bullet leaves the muzzle
 * with `v0 = aimDir·muzzleSpeed + shooterVel`. Drag scales the **whole**
 * velocity vector by `exp(-k·t)`, so absolute bullet position is `v0·f(t)`
 * where
 *
 * ```
 * f(t) = (1 - exp(-k·t)) / k        (drag enabled)
 * f(t) = t                          (k = 0)
 * ```
 *
 * **Target kinematics.** The constant-acceleration model
 * `pos(t) = targetPos + targetVel·t + 0.5·targetAcc·t²` covers the dominant
 * source of residual lag at cruiser scale: ships under sustained AI thrust
 * spend multi-second flight times accelerating *somewhere*, and a
 * constant-velocity prediction under-leads them. With `targetAcc = 0` the
 * trailing term vanishes and the prediction collapses to the linear case.
 *
 * **Intercept equation.** Transforming to the shooter's frame (translating
 * by `-shooterVel·t`) and equating bullet shooter-frame position to target
 * shooter-frame position gives
 *
 * ```
 * aimDir·muzzleSpeed·f(t) = relPos + relVel·t + 0.5·targetAcc·t²
 *                         + shooterVel·(t - f(t))
 * ```
 *
 * The trailing `shooterVel·(t - f(t))` captures the shooter-frame back-drift
 * caused by drag bleeding the inherited shooter velocity out of the bullet.
 * With `k = 0` it vanishes (`f(t) = t`); with `shooterVel = 0` it vanishes
 * too. Each term has a regression path that collapses cleanly when the
 * corresponding rig is inertial.
 *
 * **Time-of-flight inversion.** Given the shooter-frame distance the bullet
 * must cover, `t = -ln(1 - distance·k / muzzleSpeed) / k`, defined only while
 * `distance < muzzleSpeed / k` (the asymptotic shooter-frame reach). Beyond
 * that we saturate; the per-turret range gate handles actual fire suppression.
 *
 * **Aim point.** `targetPos + targetVel·t + 0.5·targetAcc·t² - shooterVel·f(t)`
 * is the world-space point the turret should aim at; the absolute bullet
 * velocity from that aim direction (plus inherited shooter velocity)
 * intercepts the predicted target position.
 */
object LeadAim {
    private const val ITERATIONS = 5
    private const val MAX_DRAG_RATIO = 0.999f

    fun computeAimPoint(
        turretPos: SceneOffset,
        shooterVelocity: SceneOffset,
        targetPos: SceneOffset,
        targetVelocity: SceneOffset,
        targetAcceleration: SceneOffset = SceneOffset.Zero,
        muzzleSpeed: Float,
        dragK: Float,
    ): SceneOffset {
        if (muzzleSpeed <= 0f) return targetPos

        val relPosX = targetPos.x.raw - turretPos.x.raw
        val relPosY = targetPos.y.raw - turretPos.y.raw
        val relVelX = targetVelocity.x.raw - shooterVelocity.x.raw
        val relVelY = targetVelocity.y.raw - shooterVelocity.y.raw
        val accX = targetAcceleration.x.raw
        val accY = targetAcceleration.y.raw
        val shootX = shooterVelocity.x.raw
        val shootY = shooterVelocity.y.raw

        var t = sqrt(relPosX * relPosX + relPosY * relPosY) / muzzleSpeed
        repeat(ITERATIONS) {
            val fT = if (dragK > 0f) (1f - exp(-dragK * t)) / dragK else t
            val gT = t - fT // shooter-velocity decay correction factor
            val halfTSquared = 0.5f * t * t
            val ax = relPosX + relVelX * t + accX * halfTSquared + shootX * gT
            val ay = relPosY + relVelY * t + accY * halfTSquared + shootY * gT
            val distNeeded = sqrt(ax * ax + ay * ay)
            t = if (dragK > 0f) {
                val ratio = (distNeeded * dragK / muzzleSpeed).coerceAtMost(MAX_DRAG_RATIO)
                -ln(1f - ratio) / dragK
            } else {
                distNeeded / muzzleSpeed
            }
        }

        val finalFT = if (dragK > 0f) (1f - exp(-dragK * t)) / dragK else t
        val finalHalfTSquared = 0.5f * t * t
        return SceneOffset(
            (targetPos.x.raw + targetVelocity.x.raw * t + accX * finalHalfTSquared - shooterVelocity.x.raw * finalFT).sceneUnit,
            (targetPos.y.raw + targetVelocity.y.raw * t + accY * finalHalfTSquared - shooterVelocity.y.raw * finalFT).sceneUnit,
        )
    }
}
