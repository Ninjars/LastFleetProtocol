package jez.lastfleetprotocol.prototype.components.game.physics

import com.pandulapeter.kubriko.helpers.extensions.length
import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class ShipPhysicsTest {

    // --- Helpers ---

    private fun makePhysics(
        mass: Float = 10f,
        initialVelocity: SceneOffset = SceneOffset.Zero,
    ) = ShipPhysics(mass = mass, initialVelocity = initialVelocity)

    /** Forward in ship-local space: -Y (ship nose points up). */
    private val localForward = SceneOffset(0f.sceneUnit, (-1f).sceneUnit)

    /** Right in ship-local space: +X. */
    private val localRight = SceneOffset(1f.sceneUnit, 0f.sceneUnit)

    // --- Zero-state tests ---

    @Test
    fun zeroVelocity_noForces_remainsStationary() {
        val physics = makePhysics()
        val result = physics.integrate(16) // ~1 frame at 60fps

        assertTrue(
            abs(result.positionDelta.x.raw) < 0.001f,
            "X position delta should be ~0, was ${result.positionDelta.x.raw}"
        )
        assertTrue(
            abs(result.positionDelta.y.raw) < 0.001f,
            "Y position delta should be ~0, was ${result.positionDelta.y.raw}"
        )
        assertTrue(
            abs(result.rotationDelta) < 0.001f,
            "Rotation delta should be ~0, was ${result.rotationDelta}"
        )
    }

    // --- Forward thrust test ---

    @Test
    fun forwardThrust_facingUp_acceleratesInNegativeY() {
        val physics = makePhysics(mass = 10f)
        val facing = 0f.rad // Facing up (default)

        // Apply forward thrust for several frames
        repeat(10) {
            physics.applyThrust(localForward, 100f, facing)
            physics.integrate(16)
        }

        // Ship should have moved in -Y direction (forward)
        assertTrue(
            physics.velocity.y.raw < -0.1f,
            "Should have negative Y velocity (moving forward/up), was ${physics.velocity.y.raw}"
        )
        assertTrue(
            abs(physics.velocity.x.raw) < 0.01f,
            "Should have minimal X velocity, was ${physics.velocity.x.raw}"
        )
    }

    // --- Directional thrust asymmetry ---

    @Test
    fun forwardThrust_strongerThanLateral_givenSameFrames() {
        val forwardPhysics = makePhysics(mass = 10f)
        val lateralPhysics = makePhysics(mass = 10f)
        val facing = 0f.rad

        val forwardForce = 400f
        val lateralForce = 100f // 4:1 ratio as per design

        repeat(10) {
            forwardPhysics.applyThrust(localForward, forwardForce, facing)
            forwardPhysics.integrate(16)

            lateralPhysics.applyThrust(localRight, lateralForce, facing)
            lateralPhysics.integrate(16)
        }

        val forwardSpeed = forwardPhysics.speed().raw
        val lateralSpeed = lateralPhysics.speed().raw

        assertTrue(
            forwardSpeed > lateralSpeed,
            "Forward speed ($forwardSpeed) should exceed lateral speed ($lateralSpeed)"
        )
    }

    // --- Mass affects acceleration ---

    @Test
    fun higherMass_acceleratesSlower_givenSameThrust() {
        val lightShip = makePhysics(mass = 10f)
        val heavyShip = makePhysics(mass = 40f)
        val facing = 0f.rad
        val thrustMagnitude = 100f

        repeat(10) {
            lightShip.applyThrust(localForward, thrustMagnitude, facing)
            lightShip.integrate(16)

            heavyShip.applyThrust(localForward, thrustMagnitude, facing)
            heavyShip.integrate(16)
        }

        val lightSpeed = lightShip.speed().raw
        val heavySpeed = heavyShip.speed().raw

        assertTrue(
            lightSpeed > heavySpeed,
            "Light ship speed ($lightSpeed) should exceed heavy ship speed ($heavySpeed)"
        )
        // With 4x mass, speed should be ~4x lower
        val ratio = lightSpeed / heavySpeed
        assertTrue(
            ratio > 3.5f && ratio < 4.5f,
            "Speed ratio should be ~4.0 (was $ratio)"
        )
    }

    // --- Deceleration ---

    @Test
    fun deceleration_bringsShipToRest() {
        val initialVelocity = SceneOffset(0f.sceneUnit, (-5f).sceneUnit)
        val physics = makePhysics(mass = 10f, initialVelocity = initialVelocity)

        // Apply deceleration for many frames
        repeat(200) {
            physics.decelerate(100f, 16)
            physics.integrate(16)
        }

        val speed = physics.speed().raw
        assertTrue(
            speed < 0.1f,
            "Ship should be nearly stopped after sustained deceleration, speed was $speed"
        )
    }

    // --- Angular force ---

    @Test
    fun angularForce_producesRotation() {
        val physics = makePhysics(mass = 10f)

        physics.applyAngularForce(50f)
        val result = physics.integrate(16)

        assertTrue(
            result.rotationDelta != 0f,
            "Rotation delta should be non-zero after applying angular force"
        )
        assertTrue(
            physics.angularVelocity > 0f,
            "Angular velocity should be positive after positive torque"
        )
    }

    // --- Integration clears forces ---

    @Test
    fun integrate_clearsAccumulatedForces() {
        val physics = makePhysics(mass = 10f)

        physics.applyThrust(localForward, 1000f, 0f.rad)
        physics.applyAngularForce(500f)
        physics.integrate(16)

        // Second integrate with no new forces: velocity should persist but no new acceleration
        val v1 = physics.velocity
        val av1 = physics.angularVelocity
        physics.integrate(16)
        val v2 = physics.velocity
        val av2 = physics.angularVelocity

        // Velocity should be unchanged (no new forces, just integration)
        assertTrue(
            abs(v1.x.raw - v2.x.raw) < 0.001f && abs(v1.y.raw - v2.y.raw) < 0.001f,
            "Velocity should not change without new forces"
        )
        assertTrue(
            abs(av1 - av2) < 0.001f,
            "Angular velocity should not change without new torque"
        )
    }

    // --- Thrust direction respects facing ---

    @Test
    fun thrust_rotated90Degrees_acceleratesInCorrectDirection() {
        val physics = makePhysics(mass = 10f)
        // Facing 90 degrees clockwise (PI/2 radians) - ship points right
        val facing = (kotlin.math.PI / 2.0).toFloat().rad

        repeat(10) {
            physics.applyThrust(localForward, 100f, facing)
            physics.integrate(16)
        }

        // Forward (-Y local) rotated 90 degrees CW should produce +X world velocity
        assertTrue(
            physics.velocity.x.raw > 0.1f,
            "Should have positive X velocity when facing right, was ${physics.velocity.x.raw}"
        )
    }
}
