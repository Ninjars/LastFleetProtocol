package jez.lastfleetprotocol.prototype.components.game.physics

import com.pandulapeter.kubriko.helpers.extensions.length
import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.data.MovementConfig
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class ShipPhysicsTest {

    private fun makePhysics(
        mass: Float = 10f,
        initialVelocity: SceneOffset = SceneOffset.Zero,
    ) = ShipPhysics(mass = mass, initialVelocity = initialVelocity)

    private val localForward = SceneOffset(1f.sceneUnit, 0f.sceneUnit)

    private val defaultMovementConfig = MovementConfig(
        forwardThrust = 1200f,
        lateralThrust = 500f,
        reverseThrust = 500f,
        angularThrust = 300f,
        forwardDragCoeff = 1.0f,
        lateralDragCoeff = 2.0f,
        reverseDragCoeff = 2.0f,
    )

    // --- Zero-state tests ---

    @Test
    fun zeroVelocity_noForces_remainsStationary() {
        val physics = makePhysics()
        val result = physics.integrate(16)

        assertTrue(
            abs(result.positionDelta.x.raw) < 0.001f,
            "X position delta should be ~0, was ${result.positionDelta.x.raw}"
        )
        assertTrue(
            abs(result.positionDelta.y.raw) < 0.001f,
            "Y position delta should be ~0, was ${result.positionDelta.y.raw}"
        )
    }

    // --- Forward thrust test ---

    @Test
    fun forwardThrust_acceleratesInForwardDirection() {
        val physics = makePhysics(mass = 10f)
        val facing = 0f.rad

        repeat(10) {
            physics.applyThrust(localForward, 100f, facing)
            physics.integrate(16)
        }

        assertTrue(
            physics.velocity.x.raw > 0.1f,
            "Should have positive X velocity (forward), was ${physics.velocity.x.raw}"
        )
    }

    // --- Mass affects acceleration ---

    @Test
    fun higherMass_acceleratesSlower_givenSameThrust() {
        val lightShip = makePhysics(mass = 10f)
        val heavyShip = makePhysics(mass = 40f)
        val facing = 0f.rad

        repeat(10) {
            lightShip.applyThrust(localForward, 100f, facing)
            lightShip.integrate(16)

            heavyShip.applyThrust(localForward, 100f, facing)
            heavyShip.integrate(16)
        }

        val lightSpeed = lightShip.speed().raw
        val heavySpeed = heavyShip.speed().raw

        assertTrue(
            lightSpeed > heavySpeed,
            "Light ship speed ($lightSpeed) should exceed heavy ship speed ($heavySpeed)"
        )
    }

    // --- Drag tests ---

    @Test
    fun drag_deceleratesMovingShip() {
        val initialVelocity = SceneOffset(5f.sceneUnit, 0f.sceneUnit)
        val physics = makePhysics(mass = 1f, initialVelocity = initialVelocity)
        val facing = 0f.rad
        val dragConfig = MovementConfig(
            forwardThrust = 0f, lateralThrust = 0f,
            reverseThrust = 0f, angularThrust = 0f,
            forwardDragCoeff = 0.5f,
            lateralDragCoeff = 0.5f,
            reverseDragCoeff = 0.5f,
        )

        val speedBefore = physics.speed().raw

        // Under quadratic drag (v² law), deceleration slows as v drops.
        // With mass=1, dragCoeff=0.5: v(t) = 1/(1/v₀ + k*t/m) → asymptotic decay.
        // After ~600 frames (10s): v ≈ 1/(0.2 + 5) ≈ 0.19
        repeat(600) {
            physics.applyDrag(dragConfig, facing)
            physics.integrate(16)
        }

        val speed = physics.speed().raw
        assertTrue(
            speed < speedBefore,
            "Ship should have slowed down, was $speed (started at $speedBefore)"
        )
        assertTrue(
            speed < 1.0f,
            "Ship should be mostly stopped after sustained drag, speed was $speed"
        )
    }

    @Test
    fun drag_zeroVelocity_noDragApplied() {
        val physics = makePhysics(mass = 10f)
        val facing = 0f.rad

        // Should not crash or produce NaN
        physics.applyDrag(defaultMovementConfig, facing)
        val result = physics.integrate(16)

        assertTrue(
            abs(result.positionDelta.x.raw) < 0.001f,
            "No movement expected at zero velocity with drag"
        )
    }

    @Test
    fun drag_zeroDragCoeffs_noDragApplied() {
        val initialVelocity = SceneOffset(5f.sceneUnit, 0f.sceneUnit)
        val physics = makePhysics(mass = 10f, initialVelocity = initialVelocity)
        val facing = 0f.rad
        val noDragConfig = MovementConfig(
            forwardThrust = 1200f, lateralThrust = 500f,
            reverseThrust = 500f, angularThrust = 300f,
            // All drag coefficients zero — frictionless
        )

        val speedBefore = physics.speed().raw
        physics.applyDrag(noDragConfig, facing)
        physics.integrate(16)
        val speedAfter = physics.speed().raw

        // Speed should be unchanged (within floating-point tolerance)
        assertTrue(
            abs(speedBefore - speedAfter) < 0.01f,
            "Speed should be unchanged with zero drag: before=$speedBefore, after=$speedAfter"
        )
    }

    @Test
    fun thrust_plus_drag_reachesTerminalVelocity() {
        val physics = makePhysics(mass = 10f)
        val facing = 0f.rad
        val config = MovementConfig(
            forwardThrust = 100f, lateralThrust = 0f,
            reverseThrust = 0f, angularThrust = 0f,
            forwardDragCoeff = 1.0f,
        )
        // Terminal velocity = sqrt(100 / 1.0) = 10
        val expectedTerminalVel = 10f

        repeat(500) {
            physics.applyThrust(localForward, 100f, facing)
            physics.applyDrag(config, facing)
            physics.integrate(16)
        }

        val speed = physics.speed().raw
        assertTrue(
            abs(speed - expectedTerminalVel) < 1.0f,
            "Speed should converge to terminal velocity ~$expectedTerminalVel, was $speed"
        )
    }

    @Test
    fun diagonal_drag_isNormalized_notPenalized() {
        // Ship facing 0 (forward = +X), moving at 45 degrees
        val diagonalVelocity = SceneOffset(5f.sceneUnit, 5f.sceneUnit)
        val forwardVelocity = SceneOffset(7.07f.sceneUnit, 0f.sceneUnit) // same magnitude

        val physDiag = makePhysics(mass = 10f, initialVelocity = diagonalVelocity)
        val physFwd = makePhysics(mass = 10f, initialVelocity = forwardVelocity)
        val facing = 0f.rad

        val symmetricConfig = MovementConfig(
            forwardThrust = 0f, lateralThrust = 0f,
            reverseThrust = 0f, angularThrust = 0f,
            forwardDragCoeff = 1.0f,
            lateralDragCoeff = 1.0f,
            reverseDragCoeff = 1.0f,
        )

        // Apply drag once to both
        physDiag.applyDrag(symmetricConfig, facing)
        physFwd.applyDrag(symmetricConfig, facing)
        physDiag.integrate(16)
        physFwd.integrate(16)

        val diagSpeed = physDiag.speed().raw
        val fwdSpeed = physFwd.speed().raw

        // With identical coefficients and normalization, drag effect should be similar
        // (not 41% higher for diagonal)
        assertTrue(
            abs(diagSpeed - fwdSpeed) < 1.0f,
            "Diagonal and forward drag should be similar with equal coefficients: diag=$diagSpeed, fwd=$fwdSpeed"
        )
    }

    // --- Integration clears forces ---

    @Test
    fun integrate_clearsAccumulatedForces() {
        val physics = makePhysics(mass = 10f)

        physics.applyThrust(localForward, 1000f, 0f.rad)
        physics.integrate(16)

        val v1 = physics.velocity
        physics.integrate(16)
        val v2 = physics.velocity

        // Velocity should be unchanged (no new forces)
        assertTrue(
            abs(v1.x.raw - v2.x.raw) < 0.001f && abs(v1.y.raw - v2.y.raw) < 0.001f,
            "Velocity should not change without new forces"
        )
    }

    // --- Thrust direction respects facing ---

    @Test
    fun thrust_rotated90Degrees_acceleratesInCorrectDirection() {
        val physics = makePhysics(mass = 10f)
        val facing = (kotlin.math.PI / 2.0).toFloat().rad

        repeat(10) {
            physics.applyThrust(localForward, 100f, facing)
            physics.integrate(16)
        }

        assertTrue(
            physics.velocity.y.raw > 0.1f,
            "Should have positive Y velocity when facing 90 degrees, was ${physics.velocity.y.raw}"
        )
    }
}
