package jez.lastfleetprotocol.prototype.components.game.navigation

import com.pandulapeter.kubriko.helpers.extensions.cos
import com.pandulapeter.kubriko.helpers.extensions.length
import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.helpers.extensions.sin
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.game.utils.rotate
import jez.lastfleetprotocol.prototype.components.gamecore.data.MovementConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure prediction helpers for ship navigation under the runtime thrust + drag model.
 *
 * [ShipPhysics] remains the mutable integrator. This class mirrors the same force
 * shape without side effects so [ShipNavigator] can score candidate headings before
 * it chooses where to rotate and which thrusters to fire.
 */
class ShipMotionModel {
    fun terminalVelocity(
        thrust: Float,
        dragCoeff: Float,
    ): Float {
        if (dragCoeff <= DRAG_EPSILON) return Float.MAX_VALUE
        if (thrust <= 0f) return 0f
        return sqrt(thrust / dragCoeff)
    }

    fun effectiveDrag(
        velocity: SceneOffset,
        heading: AngleRadians,
        movementConfig: MovementConfig,
        lateralDragMultiplier: Float = 1f,
    ): Float {
        val speed = velocity.length().raw
        if (speed <= SPEED_EPSILON) return 0f

        val velocityAngle = atan2(velocity.y.raw, velocity.x.raw)
        val theta = velocityAngle - heading.normalized
        val forwardWeight = max(0f, cos(theta))
        val reverseWeight = max(0f, -cos(theta))
        val lateralWeight = abs(sin(theta))
        val totalWeight = forwardWeight + reverseWeight + lateralWeight

        return if (totalWeight > WEIGHT_EPSILON) {
            (
                movementConfig.forwardDragCoeff * forwardWeight +
                    movementConfig.reverseDragCoeff * reverseWeight +
                    movementConfig.lateralDragCoeff * lateralDragMultiplier * lateralWeight
            ) / totalWeight
        } else {
            movementConfig.forwardDragCoeff
        }
    }

    fun dragAcceleration(
        velocity: SceneOffset,
        heading: AngleRadians,
        movementConfig: MovementConfig,
        mass: Float,
        lateralDragMultiplier: Float = 1f,
    ): SceneOffset {
        val speed = velocity.length().raw
        if (speed <= SPEED_EPSILON || mass <= 0f) return SceneOffset.Zero

        val dragCoeff = effectiveDrag(
            velocity = velocity,
            heading = heading,
            movementConfig = movementConfig,
            lateralDragMultiplier = lateralDragMultiplier,
        )
        if (dragCoeff <= DRAG_EPSILON) return SceneOffset.Zero

        val dragMagnitude = dragCoeff * speed * speed
        return SceneOffset(
            x = ((-velocity.x.raw / speed) * dragMagnitude / mass).sceneUnit,
            y = ((-velocity.y.raw / speed) * dragMagnitude / mass).sceneUnit,
        )
    }

    fun thrustAcceleration(
        localDirection: SceneOffset,
        magnitude: Float,
        heading: AngleRadians,
        mass: Float,
    ): SceneOffset {
        if (magnitude <= 0f || mass <= 0f) return SceneOffset.Zero
        return localDirection.rotate(heading) * (magnitude / mass)
    }

    fun accelerationForHeading(
        heading: AngleRadians,
        velocity: SceneOffset,
        desiredVelocity: SceneOffset,
        movementConfig: MovementConfig,
        mass: Float,
        lateralDragMultiplier: Float = 1f,
    ): SceneOffset {
        val thrust = usefulThrustForHeading(
            heading = heading,
            velocity = velocity,
            desiredVelocity = desiredVelocity,
            movementConfig = movementConfig,
            mass = mass,
        )
        return thrust +
            dragAcceleration(
                velocity = velocity,
                heading = heading,
                movementConfig = movementConfig,
                mass = mass,
                lateralDragMultiplier = lateralDragMultiplier,
            )
    }

    fun usefulThrustForHeading(
        heading: AngleRadians,
        velocity: SceneOffset,
        desiredVelocity: SceneOffset,
        movementConfig: MovementConfig,
        mass: Float,
    ): SceneOffset {
        if (mass <= 0f) return SceneOffset.Zero

        val velocityError = desiredVelocity - velocity
        val errorSpeed = velocityError.length().raw
        if (errorSpeed <= SPEED_EPSILON) return SceneOffset.Zero

        val desiredAccelX = velocityError.x.raw / errorSpeed
        val desiredAccelY = velocityError.y.raw / errorSpeed
        val forwardX = heading.cos
        val forwardY = heading.sin
        val leftX = -heading.sin
        val leftY = heading.cos

        val forwardDot = desiredAccelX * forwardX + desiredAccelY * forwardY
        val lateralDot = desiredAccelX * leftX + desiredAccelY * leftY

        var acceleration = SceneOffset.Zero
        if (forwardDot > THRUST_DEADBAND) {
            acceleration += thrustAcceleration(
                localDirection = LOCAL_FORWARD,
                magnitude = movementConfig.forwardThrust * forwardDot,
                heading = heading,
                mass = mass,
            )
        } else if (forwardDot < -THRUST_DEADBAND) {
            acceleration += thrustAcceleration(
                localDirection = LOCAL_REVERSE,
                magnitude = movementConfig.reverseThrust * (-forwardDot),
                heading = heading,
                mass = mass,
            )
        }

        if (abs(lateralDot) > LATERAL_THRUST_DEADBAND && movementConfig.lateralThrust > 0f) {
            acceleration += thrustAcceleration(
                localDirection = if (lateralDot > 0f) LOCAL_LEFT else LOCAL_RIGHT,
                magnitude = movementConfig.lateralThrust * LATERAL_CORRECTION_THRUST_FRACTION * abs(lateralDot),
                heading = heading,
                mass = mass,
            )
        }

        return acceleration
    }

    fun velocityPlan(
        position: SceneOffset,
        destination: SceneOffset,
        velocity: SceneOffset,
        movementConfig: MovementConfig,
        mass: Float,
    ): VelocityPlan {
        val toTarget = destination - position
        val distance = toTarget.length().raw
        if (distance <= POSITION_EPSILON || mass <= 0f) {
            return VelocityPlan(
                desiredVelocity = SceneOffset.Zero,
                targetDirection = SceneOffset.Zero,
                distance = distance,
                lateralVelocity = SceneOffset.Zero,
            )
        }

        val targetDir = toTarget * (1f / distance)
        val radialSpeed = dot(velocity, targetDir)
        val radialVelocity = targetDir * radialSpeed
        val lateralVelocity = velocity - radialVelocity
        val correctionThrust = movementConfig.lateralThrust * LATERAL_CORRECTION_THRUST_FRACTION
        val brakeAccel = movementConfig.reverseThrust.coerceAtLeast(correctionThrust) / mass
        val brakingSpeed = if (brakeAccel > 0f) {
            sqrt(max(0f, 2f * brakeAccel * max(0f, distance - ARRIVAL_RADIUS)))
        } else {
            0f
        }
        val terminalCap = terminalVelocity(
            movementConfig.forwardThrust,
            movementConfig.forwardDragCoeff,
        ).let { if (it == Float.MAX_VALUE) MAX_UNCAPPED_APPROACH_SPEED else it }

        val approachSpeed = min(terminalCap * APPROACH_TERMINAL_FRACTION, brakingSpeed)
        val lateralCorrection = lateralVelocity * LATERAL_VELOCITY_CORRECTION
        val overspeedCorrection = min(0f, approachSpeed - radialSpeed)
        val desiredVelocity = targetDir * (approachSpeed + overspeedCorrection * RADIAL_OVERSPEED_CORRECTION) -
            lateralCorrection

        return VelocityPlan(
            desiredVelocity = desiredVelocity,
            targetDirection = targetDir,
            distance = distance,
            lateralVelocity = lateralVelocity,
        )
    }

    fun scoreHeading(
        heading: AngleRadians,
        currentHeading: AngleRadians,
        velocity: SceneOffset,
        desiredVelocity: SceneOffset,
        targetDirection: SceneOffset,
        lateralVelocity: SceneOffset,
        movementConfig: MovementConfig,
        mass: Float,
        dt: Float,
    ): Float {
        val acceleration = accelerationForHeading(
            heading = heading,
            velocity = velocity,
            desiredVelocity = desiredVelocity,
            movementConfig = movementConfig,
            mass = mass,
            lateralDragMultiplier = NAVIGATION_LATERAL_DRAG_BIAS,
        )
        val predictedVelocity = velocity + acceleration * dt
        val currentError = (desiredVelocity - velocity).length().raw
        val predictedError = (desiredVelocity - predictedVelocity).length().raw
        val errorReduction = currentError - predictedError
        val closure = dot(predictedVelocity, targetDirection) - dot(velocity, targetDirection)
        val lateralSpeed = lateralVelocity.length().raw
        val predictedLateralSpeed = lateralComponent(predictedVelocity, targetDirection).length().raw
        val lateralReduction = lateralSpeed - predictedLateralSpeed
        val rawBroadsideDrag = effectiveDrag(
            velocity = velocity,
            heading = heading,
            movementConfig = movementConfig,
            lateralDragMultiplier = NAVIGATION_LATERAL_DRAG_BIAS,
        ) - movementConfig.forwardDragCoeff
        val broadsideDrag = signedBroadsideDrag(
            rawBroadsideDrag = rawBroadsideDrag,
            velocity = velocity,
            desiredVelocity = desiredVelocity,
        )
        val lateralTravelPenaltyValue = lateralTravelPenalty(
            heading = heading,
            velocity = velocity,
            desiredVelocity = desiredVelocity,
        )
        val turnCost = abs(signedAngleDelta(currentHeading, heading))

        return errorReduction * ERROR_REDUCTION_WEIGHT +
            closure * CLOSURE_WEIGHT +
            lateralReduction * LATERAL_REDUCTION_WEIGHT +
            broadsideDrag * BROADSIDE_DRAG_WEIGHT -
            lateralTravelPenaltyValue * LATERAL_TRAVEL_PENALTY_WEIGHT -
            turnCost * TURN_COST_WEIGHT
    }

    private fun signedBroadsideDrag(
        rawBroadsideDrag: Float,
        velocity: SceneOffset,
        desiredVelocity: SceneOffset,
    ): Float {
        if (rawBroadsideDrag <= 0f) return rawBroadsideDrag

        val usefulness = dragUsefulness(velocity, desiredVelocity)
        if (usefulness <= 0f) return -rawBroadsideDrag
        return rawBroadsideDrag * (usefulness * 2f - 1f)
    }

    private fun dragUsefulness(
        velocity: SceneOffset,
        desiredVelocity: SceneOffset,
    ): Float {
        val speedSquared = dot(velocity, velocity)
        if (speedSquared <= SPEED_EPSILON * SPEED_EPSILON) return 0f

        // Drag is useful when the desired velocity is lower, sideways, or opposite
        // to current velocity. It is harmful when the ship is already moving in
        // the desired direction and still needs to accelerate.
        val desiredProjection = dot(velocity, desiredVelocity)
        return ((speedSquared - desiredProjection) / speedSquared).coerceIn(0f, 1f)
    }

    private fun lateralTravelPenalty(
        heading: AngleRadians,
        velocity: SceneOffset,
        desiredVelocity: SceneOffset,
    ): Float {
        val desiredSpeed = desiredVelocity.length().raw
        if (desiredSpeed <= SPEED_EPSILON) return 0f

        val desiredDirection = desiredVelocity * (1f / desiredSpeed)
        val accelerationNeed = (desiredSpeed - dot(velocity, desiredDirection)).coerceAtLeast(0f)
        if (accelerationNeed <= SPEED_EPSILON) return 0f

        val lateralReliance = abs(-desiredDirection.x.raw * heading.sin + desiredDirection.y.raw * heading.cos)
        return accelerationNeed * lateralReliance
    }

    fun candidateHeadings(
        currentHeading: AngleRadians,
        targetDirection: SceneOffset,
        velocity: SceneOffset,
        desiredVelocity: SceneOffset,
    ): List<AngleRadians> {
        val headings = mutableListOf(currentHeading)
        addIfNonZero(headings, targetDirection)
        addIfNonZero(headings, velocity)
        addIfNonZero(headings, velocity * -1f)
        addIfNonZero(headings, desiredVelocity)

        val velocitySpeed = velocity.length().raw
        if (velocitySpeed > SPEED_EPSILON &&
            dragUsefulness(velocity, desiredVelocity) > DRAG_CANDIDATE_USEFULNESS_THRESHOLD
        ) {
            val velocityAngle = atan2(velocity.y.raw, velocity.x.raw)
            headings += (velocityAngle + HALF_PI).rad
            headings += (velocityAngle - HALF_PI).rad
        }

        val desiredSpeed = desiredVelocity.length().raw
        if (desiredSpeed > SPEED_EPSILON) {
            val desiredAngle = atan2(desiredVelocity.y.raw, desiredVelocity.x.raw)
            headings += (desiredAngle + QUARTER_PI).rad
            headings += (desiredAngle - QUARTER_PI).rad
        }

        return headings.distinctBy { (it.normalized * 1000f).toInt() }
    }

    fun signedAngleDelta(
        from: AngleRadians,
        to: AngleRadians,
    ): Float {
        var delta = (to - from).normalized
        if (delta > PI.toFloat()) delta -= TWO_PI
        return delta
    }

    private fun addIfNonZero(
        headings: MutableList<AngleRadians>,
        vector: SceneOffset,
    ) {
        if (vector.length().raw > SPEED_EPSILON) {
            headings += atan2(vector.y.raw, vector.x.raw).rad
        }
    }

    private fun lateralComponent(
        velocity: SceneOffset,
        targetDirection: SceneOffset,
    ): SceneOffset {
        if (targetDirection.length().raw <= SPEED_EPSILON) return velocity
        return velocity - targetDirection * dot(velocity, targetDirection)
    }

    fun dot(
        a: SceneOffset,
        b: SceneOffset,
    ): Float = a.x.raw * b.x.raw + a.y.raw * b.y.raw

    data class VelocityPlan(
        val desiredVelocity: SceneOffset,
        val targetDirection: SceneOffset,
        val distance: Float,
        val lateralVelocity: SceneOffset,
    )

    companion object {
        const val NAVIGATION_LATERAL_DRAG_BIAS = 1.6f
        const val LATERAL_CORRECTION_THRUST_FRACTION = 0.2f

        private val LOCAL_FORWARD = SceneOffset(1f.sceneUnit, 0f.sceneUnit)
        private val LOCAL_REVERSE = SceneOffset((-1f).sceneUnit, 0f.sceneUnit)
        private val LOCAL_LEFT = SceneOffset(0f.sceneUnit, 1f.sceneUnit)
        private val LOCAL_RIGHT = SceneOffset(0f.sceneUnit, (-1f).sceneUnit)

        private const val SPEED_EPSILON = 0.01f
        private const val POSITION_EPSILON = 0.01f
        private const val DRAG_EPSILON = 0.0001f
        private const val WEIGHT_EPSILON = 0.001f
        private const val THRUST_DEADBAND = 0.08f
        private const val LATERAL_THRUST_DEADBAND = 0.22f
        private const val ARRIVAL_RADIUS = 50f
        private const val APPROACH_TERMINAL_FRACTION = 0.85f
        private const val MAX_UNCAPPED_APPROACH_SPEED = 120f
        private const val LATERAL_VELOCITY_CORRECTION = 1.15f
        private const val RADIAL_OVERSPEED_CORRECTION = 0.8f

        private const val ERROR_REDUCTION_WEIGHT = 4f
        private const val CLOSURE_WEIGHT = 0.45f
        private const val LATERAL_REDUCTION_WEIGHT = 2.5f
        private const val BROADSIDE_DRAG_WEIGHT = 0.15f
        private const val LATERAL_TRAVEL_PENALTY_WEIGHT = 0.12f
        private const val DRAG_CANDIDATE_USEFULNESS_THRESHOLD = 0.25f
        private const val TURN_COST_WEIGHT = 0.1f

        private const val HALF_PI = (PI / 2.0).toFloat()
        private const val QUARTER_PI = (PI / 4.0).toFloat()
        private const val TWO_PI = (PI * 2.0).toFloat()
    }
}
