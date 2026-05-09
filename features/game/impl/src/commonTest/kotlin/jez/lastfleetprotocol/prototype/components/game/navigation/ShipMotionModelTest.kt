package jez.lastfleetprotocol.prototype.components.game.navigation

import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.data.MovementConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShipMotionModelTest {

    private val model = ShipMotionModel()

    private val movementConfig = MovementConfig(
        forwardThrust = 60_000f,
        lateralThrust = 12_000f,
        reverseThrust = 25_000f,
        angularThrust = 5_000f,
        forwardDragCoeff = 0.25f,
        lateralDragCoeff = 1.25f,
        reverseDragCoeff = 0.4f,
    )

    @Test
    fun effectiveDrag_usesLateralCoefficientWhenBroadsideToVelocity() {
        val velocity = SceneOffset(30f.sceneUnit, 0f.sceneUnit)

        val noseIntoVelocity = model.effectiveDrag(
            velocity = velocity,
            heading = 0f.rad,
            movementConfig = movementConfig,
        )
        val broadsideToVelocity = model.effectiveDrag(
            velocity = velocity,
            heading = (PI / 2.0).toFloat().rad,
            movementConfig = movementConfig,
        )

        assertEquals(movementConfig.forwardDragCoeff, noseIntoVelocity, 0.001f)
        assertEquals(movementConfig.lateralDragCoeff, broadsideToVelocity, 0.001f)
        assertTrue(
            broadsideToVelocity > noseIntoVelocity * 3f,
            "Broadside heading should produce much stronger drag: nose=$noseIntoVelocity broadside=$broadsideToVelocity",
        )
    }

    @Test
    fun dragAcceleration_opposesVelocityAndScalesWithHeadingDrag() {
        val velocity = SceneOffset(30f.sceneUnit, 0f.sceneUnit)
        val mass = 50_000f

        val forwardDragAccel = model.dragAcceleration(
            velocity = velocity,
            heading = 0f.rad,
            movementConfig = movementConfig,
            mass = mass,
        )
        val broadsideDragAccel = model.dragAcceleration(
            velocity = velocity,
            heading = (PI / 2.0).toFloat().rad,
            movementConfig = movementConfig,
            mass = mass,
        )

        assertTrue(forwardDragAccel.x.raw < 0f, "Drag should oppose +X velocity")
        assertTrue(abs(forwardDragAccel.y.raw) < 0.001f, "Drag should not add lateral acceleration")
        assertTrue(
            abs(broadsideDragAccel.x.raw) > abs(forwardDragAccel.x.raw) * 3f,
            "Broadside drag acceleration should be stronger: forward=$forwardDragAccel broadside=$broadsideDragAccel",
        )
    }

    @Test
    fun velocityPlan_countersLateralVelocityToPreventOrbiting() {
        val plan = model.velocityPlan(
            position = SceneOffset.Zero,
            destination = SceneOffset(1_000f.sceneUnit, 0f.sceneUnit),
            velocity = SceneOffset(20f.sceneUnit, 15f.sceneUnit),
            movementConfig = movementConfig,
            mass = 50_000f,
        )

        assertTrue(plan.desiredVelocity.x.raw > 0f, "Desired velocity should still close on the destination")
        assertTrue(
            plan.desiredVelocity.y.raw < 0f,
            "Desired velocity should counter existing +Y lateral drift: ${plan.desiredVelocity}",
        )
        assertTrue(
            plan.lateralVelocity.y.raw > 0f,
            "Plan should identify the +Y velocity as lateral to the target path: ${plan.lateralVelocity}",
        )
    }

    @Test
    fun usefulThrustForHeading_capsLateralCorrectionAtTwentyPercent() {
        val acceleration = model.usefulThrustForHeading(
            heading = 0f.rad,
            velocity = SceneOffset.Zero,
            desiredVelocity = SceneOffset(0f.sceneUnit, 10f.sceneUnit),
            movementConfig = movementConfig,
            mass = 1_000f,
        )

        val expectedLateralAcceleration =
            movementConfig.lateralThrust * ShipMotionModel.LATERAL_CORRECTION_THRUST_FRACTION / 1_000f

        assertEquals(0f, acceleration.x.raw, 0.001f)
        assertEquals(expectedLateralAcceleration, acceleration.y.raw, 0.001f)
        assertTrue(
            expectedLateralAcceleration < movementConfig.forwardThrust / 1_000f,
            "Fine lateral correction should stay below primary navigation thrust",
        )
    }

    @Test
    fun scoreHeading_prefersBroadsideWhenStoppingUnderStrongLateralDrag() {
        val dragBiasedConfig = movementConfig.copy(
            lateralThrust = 0f,
            reverseThrust = 1_000f,
            lateralDragCoeff = 4.0f,
        )
        val velocity = SceneOffset(35f.sceneUnit, 0f.sceneUnit)
        val desiredVelocity = SceneOffset.Zero
        val targetDirection = SceneOffset((-1f).sceneUnit, 0f.sceneUnit)
        val lateralVelocity = SceneOffset.Zero
        val mass = 50_000f

        val forwardScore = model.scoreHeading(
            heading = 0f.rad,
            currentHeading = 0f.rad,
            velocity = velocity,
            desiredVelocity = desiredVelocity,
            targetDirection = targetDirection,
            lateralVelocity = lateralVelocity,
            movementConfig = dragBiasedConfig,
            mass = mass,
            dt = 1f,
        )
        val broadsideScore = model.scoreHeading(
            heading = (PI / 2.0).toFloat().rad,
            currentHeading = 0f.rad,
            velocity = velocity,
            desiredVelocity = desiredVelocity,
            targetDirection = targetDirection,
            lateralVelocity = lateralVelocity,
            movementConfig = dragBiasedConfig,
            mass = mass,
            dt = 1f,
        )

        assertTrue(
            broadsideScore > forwardScore,
            "Heading scoring should value broadside drag when stopping: forward=$forwardScore broadside=$broadsideScore",
        )
    }

    @Test
    fun scoreHeading_stationaryShipPrefersNoseTowardDesiredVelocityOverBroadside() {
        val velocity = SceneOffset.Zero
        val desiredVelocity = SceneOffset(20f.sceneUnit, 0f.sceneUnit)
        val targetDirection = SceneOffset(1f.sceneUnit, 0f.sceneUnit)
        val mass = 50_000f

        val noseScore = model.scoreHeading(
            heading = 0f.rad,
            currentHeading = (PI / 2.0).toFloat().rad,
            velocity = velocity,
            desiredVelocity = desiredVelocity,
            targetDirection = targetDirection,
            lateralVelocity = SceneOffset.Zero,
            movementConfig = movementConfig,
            mass = mass,
            dt = 1f,
        )
        val broadsideScore = model.scoreHeading(
            heading = (PI / 2.0).toFloat().rad,
            currentHeading = (PI / 2.0).toFloat().rad,
            velocity = velocity,
            desiredVelocity = desiredVelocity,
            targetDirection = targetDirection,
            lateralVelocity = SceneOffset.Zero,
            movementConfig = movementConfig,
            mass = mass,
            dt = 1f,
        )

        assertTrue(
            noseScore > broadsideScore,
            "Stationary acceleration should prefer nose-to-target over broadside hold: nose=$noseScore broadside=$broadsideScore",
        )
    }

    @Test
    fun scoreHeading_alignedVelocityNeedingAccelerationDoesNotRewardBroadsideDrag() {
        val velocity = SceneOffset(10f.sceneUnit, 0f.sceneUnit)
        val desiredVelocity = SceneOffset(40f.sceneUnit, 0f.sceneUnit)
        val targetDirection = SceneOffset(1f.sceneUnit, 0f.sceneUnit)
        val mass = 50_000f

        val noseScore = model.scoreHeading(
            heading = 0f.rad,
            currentHeading = (PI / 2.0).toFloat().rad,
            velocity = velocity,
            desiredVelocity = desiredVelocity,
            targetDirection = targetDirection,
            lateralVelocity = SceneOffset.Zero,
            movementConfig = movementConfig,
            mass = mass,
            dt = 1f,
        )
        val broadsideScore = model.scoreHeading(
            heading = (PI / 2.0).toFloat().rad,
            currentHeading = (PI / 2.0).toFloat().rad,
            velocity = velocity,
            desiredVelocity = desiredVelocity,
            targetDirection = targetDirection,
            lateralVelocity = SceneOffset.Zero,
            movementConfig = movementConfig,
            mass = mass,
            dt = 1f,
        )

        assertTrue(
            noseScore > broadsideScore,
            "Aligned acceleration should not treat broadside drag as useful: nose=$noseScore broadside=$broadsideScore",
        )
    }

    @Test
    fun candidateHeadings_doNotOfferVelocityBroadsideWhenAlreadyAlignedAndAccelerating() {
        val headings = model.candidateHeadings(
            currentHeading = 0f.rad,
            targetDirection = SceneOffset(1f.sceneUnit, 0f.sceneUnit),
            velocity = SceneOffset(10f.sceneUnit, 0f.sceneUnit),
            desiredVelocity = SceneOffset(40f.sceneUnit, 0f.sceneUnit),
        )

        assertTrue(
            headings.none { abs(model.signedAngleDelta(0f.rad, it) - (PI / 2.0).toFloat()) < 0.01f },
            "Velocity-broadside heading should not be offered when the ship needs more speed along its current path: $headings",
        )
    }

    @Test
    fun candidateHeadings_offerVelocityBroadsideWhenStopping() {
        val headings = model.candidateHeadings(
            currentHeading = 0f.rad,
            targetDirection = SceneOffset((-1f).sceneUnit, 0f.sceneUnit),
            velocity = SceneOffset(35f.sceneUnit, 0f.sceneUnit),
            desiredVelocity = SceneOffset.Zero,
        )

        assertTrue(
            headings.any { abs(model.signedAngleDelta(0f.rad, it) - (PI / 2.0).toFloat()) < 0.01f },
            "Velocity-broadside heading should be offered when the ship needs to shed speed: $headings",
        )
    }
}
