package jez.lastfleetprotocol.prototype.components.game.combat

import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the lead-aim solver. The "intercept residual" helper recomputes
 * the bullet/target geometry the solver assumes and verifies the bullet would
 * actually meet the target at the implied time-of-flight — a stronger check
 * than asserting the returned aim point in isolation.
 */
class LeadAimTest {

    private fun aim(
        turret: Pair<Float, Float> = 0f to 0f,
        shooterVel: Pair<Float, Float> = 0f to 0f,
        target: Pair<Float, Float>,
        targetVel: Pair<Float, Float> = 0f to 0f,
        muzzleSpeed: Float = 100f,
        dragK: Float = 0f,
    ): SceneOffset = LeadAim.computeAimPoint(
        turretPos = SceneOffset(turret.first.sceneUnit, turret.second.sceneUnit),
        shooterVelocity = SceneOffset(shooterVel.first.sceneUnit, shooterVel.second.sceneUnit),
        targetPos = SceneOffset(target.first.sceneUnit, target.second.sceneUnit),
        targetVelocity = SceneOffset(targetVel.first.sceneUnit, targetVel.second.sceneUnit),
        muzzleSpeed = muzzleSpeed,
        dragK = dragK,
    )

    @Test
    fun stationaryTarget_aimsAtTarget() {
        val result = aim(target = 1000f to 0f)
        assertEquals(1000f, result.x.raw, absoluteTolerance = 0.5f)
        assertEquals(0f, result.y.raw, absoluteTolerance = 0.5f)
    }

    @Test
    fun stationaryShooter_targetCrossing_leadsAhead() {
        // Target at (1000, 0) moving +Y at 50. Bullet speed 100. Time-of-flight ≈
        // 1000/100 = 10s naively, then refined: aim leads ahead in +Y.
        val result = aim(
            target = 1000f to 0f,
            targetVel = 0f to 50f,
            muzzleSpeed = 100f,
        )
        // Aim should be ahead of target in +Y.
        assertTrue(result.y.raw > 100f, "expected positive lead in Y, got ${result.y.raw}")
        assertInterceptHolds(
            turret = 0f to 0f,
            shooterVel = 0f to 0f,
            target = 1000f to 0f,
            targetVel = 0f to 50f,
            muzzleSpeed = 100f,
            dragK = 0f,
            aim = result,
        )
    }

    @Test
    fun matchedShooterAndTarget_velocity_aimAtTarget() {
        // Shooter and target moving identically — bullet inherits shooter velocity,
        // so relative velocity is zero. Lead = 0; aim straight at target.
        val result = aim(
            shooterVel = 30f to 40f,
            target = 1000f to 0f,
            targetVel = 30f to 40f,
        )
        assertEquals(1000f, result.x.raw, absoluteTolerance = 0.5f)
        assertEquals(0f, result.y.raw, absoluteTolerance = 0.5f)
    }

    @Test
    fun shooterMoving_stationaryTarget_leadsBackward() {
        // Shooter moves +Y at 50, target stationary. Bullet inherits +Y motion,
        // so to hit a stationary target the turret must aim "below" it (in -Y).
        // Aim point = targetPos + relVel*t = targetPos + (0 - +50Y) * t.
        val result = aim(
            shooterVel = 0f to 50f,
            target = 1000f to 0f,
            targetVel = 0f to 0f,
            muzzleSpeed = 100f,
        )
        assertTrue(result.y.raw < -100f, "expected negative lead in Y, got ${result.y.raw}")
        assertInterceptHolds(
            turret = 0f to 0f,
            shooterVel = 0f to 50f,
            target = 1000f to 0f,
            targetVel = 0f to 0f,
            muzzleSpeed = 100f,
            dragK = 0f,
            aim = result,
        )
    }

    @Test
    fun dragAware_shooterMoving_intercept_matchesNumerically() {
        // Cruiser-class engagement: shooter moves +X at 80 m/s while target at
        // (3000, 0) crosses at +Y 50 m/s. Bullet inherits shooter velocity, then
        // drag scales the *full* vector — the older formulation (without the
        // shooterVel·(t - f(t)) correction) under-leads here. Intercept must
        // hold under the corrected math.
        val result = aim(
            shooterVel = 80f to 0f,
            target = 3000f to 0f,
            targetVel = 0f to 50f,
            muzzleSpeed = 600f,
            dragK = 0.12f,
        )
        assertInterceptHolds(
            turret = 0f to 0f,
            shooterVel = 80f to 0f,
            target = 3000f to 0f,
            targetVel = 0f to 50f,
            muzzleSpeed = 600f,
            dragK = 0.12f,
            aim = result,
        )
    }

    @Test
    fun dragAware_intercept_matchesNumerically() {
        // Drag-aware: muzzleSpeed=600, dragK=0.12 (standard cruiser turret).
        // Target at (2000, 0) moving +Y at 30. Verify intercept holds under
        // exponential drag kinematics.
        val result = aim(
            target = 2000f to 0f,
            targetVel = 0f to 30f,
            muzzleSpeed = 600f,
            dragK = 0.12f,
        )
        assertInterceptHolds(
            turret = 0f to 0f,
            shooterVel = 0f to 0f,
            target = 2000f to 0f,
            targetVel = 0f to 30f,
            muzzleSpeed = 600f,
            dragK = 0.12f,
            aim = result,
        )
    }

    @Test
    fun zeroMuzzleSpeed_returnsTargetPosition() {
        // Degenerate guard: no projectile speed → fall back to "aim at target".
        val result = aim(
            target = 500f to 700f,
            targetVel = 100f to 100f,
            muzzleSpeed = 0f,
        )
        assertEquals(500f, result.x.raw, absoluteTolerance = 0.01f)
        assertEquals(700f, result.y.raw, absoluteTolerance = 0.01f)
    }

    /**
     * Verify the geometric meaning of the aim point: starting from `turret`
     * with bullet absolute velocity `aimDir * muzzleSpeed + shooterVel`, the
     * bullet (under exponential drag if `dragK > 0`) actually meets the target
     * at some positive time `t*`. We sweep t over a fine grid, pick the t that
     * minimises |bullet(t) - target(t)|, and assert that minimum is small.
     */
    private fun assertInterceptHolds(
        turret: Pair<Float, Float>,
        shooterVel: Pair<Float, Float>,
        target: Pair<Float, Float>,
        targetVel: Pair<Float, Float>,
        muzzleSpeed: Float,
        dragK: Float,
        aim: SceneOffset,
        tolerance: Float = 5f,
    ) {
        val aimDx = aim.x.raw - turret.first
        val aimDy = aim.y.raw - turret.second
        val aimLen = sqrt(aimDx * aimDx + aimDy * aimDy)
        require(aimLen > 1e-6f) { "Aim vector degenerate" }
        val dirX = aimDx / aimLen
        val dirY = aimDy / aimLen

        // Bullet absolute velocity at t=0 (shooter-frame muzzle + inherited shooter velocity).
        val v0x = dirX * muzzleSpeed + shooterVel.first
        val v0y = dirY * muzzleSpeed + shooterVel.second

        var bestMiss = Float.MAX_VALUE
        var bestT = 0f
        var t = 0.01f
        while (t < 60f) {
            // Exponential-drag bullet position: x(t) = turret + v0/k * (1 - exp(-kt)).
            // With k=0 this collapses to v0*t in the limit; handle separately.
            val factor = if (dragK > 0f) (1f - kotlin.math.exp(-dragK * t)) / dragK else t
            val bx = turret.first + v0x * factor
            val by = turret.second + v0y * factor
            val tx = target.first + targetVel.first * t
            val ty = target.second + targetVel.second * t
            val miss = sqrt((bx - tx) * (bx - tx) + (by - ty) * (by - ty))
            if (miss < bestMiss) {
                bestMiss = miss
                bestT = t
            }
            t += 0.01f
        }
        assertTrue(
            bestMiss < tolerance,
            "Intercept residual ${bestMiss} m at t=${bestT}s exceeded tolerance ${tolerance}",
        )
        assertTrue(bestT > 0f, "Best intercept time non-positive: $bestT")
        // Sanity: best t should be on the same order as the implied flight time
        // — i.e., the solver isn't pointing at a trajectory that intercepts
        // accidentally at some far-future moment.
        assertTrue(abs(bestT) < 30f, "Intercept time ${bestT}s implausibly large")
    }
}
