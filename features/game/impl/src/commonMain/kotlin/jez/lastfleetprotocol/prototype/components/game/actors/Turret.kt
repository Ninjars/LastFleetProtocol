package jez.lastfleetprotocol.prototype.components.game.actors

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.helpers.extensions.angleTowards
import com.pandulapeter.kubriko.helpers.extensions.deg
import com.pandulapeter.kubriko.helpers.extensions.get
import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.rotateTowards
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import jez.lastfleetprotocol.prototype.components.game.data.DrawOrder
import jez.lastfleetprotocol.prototype.components.game.data.Gun
import jez.lastfleetprotocol.prototype.components.gamecore.data.GunData
import kotlin.math.cos
import kotlin.math.sin

/**
 * Turret size in scene units (isoceles triangle half-dimensions).
 * Length = apex to base centre; width = half the base width.
 */
private const val TURRET_HALF_LENGTH = 16f
private const val TURRET_HALF_WIDTH = 8f

class Turret(
    parent: Parent,
    offsetFromParentPivot: SceneOffset,
    private val pivot: SceneOffset,
    private val gunData: GunData,
    val teamId: String,
    private val rotationSpeed: AngleRadians = 10f.deg.rad,
) : Child(
    parent = parent,
    offsetFromParentPivot = offsetFromParentPivot,
) {
    private lateinit var actorManager: ActorManager

    private var currentRotation: AngleRadians = AngleRadians.Zero

    override val body = BoxBody()

    var target: Targetable? = null

    private val gun: Gun by lazy {
        Gun(
            turretBody = body,
            muzzleOffset = SceneOffset(TURRET_HALF_LENGTH.sceneUnit, 0f.sceneUnit),
            gunData = gunData,
            teamId = teamId,
        )
    }

    /** Turret outline colour — light grey, distinct from both team colours. */
    private val turretColor = Color(0.8f, 0.8f, 0.8f, 0.9f)
    private val turretFillColor = Color(0.5f, 0.5f, 0.5f, 0.4f)

    override fun onAdded(kubriko: Kubriko) {
        super.onAdded(kubriko)
        actorManager = kubriko.get()
        body.size = SceneSize(
            (TURRET_HALF_LENGTH * 2).sceneUnit,
            (TURRET_HALF_WIDTH * 2).sceneUnit,
        )
        body.pivot = pivot
    }

    override fun DrawScope.draw() {
        // Draw an isoceles triangle pointing along the barrel direction (+X local)
        val rotation = currentRotation.normalized
        val cos = cos(rotation)
        val sin = sin(rotation)

        // Triangle vertices in local space relative to body centre
        val tipX = TURRET_HALF_LENGTH
        val tipY = 0f
        val baseLeftX = -TURRET_HALF_LENGTH * 0.5f
        val baseLeftY = -TURRET_HALF_WIDTH
        val baseRightX = -TURRET_HALF_LENGTH * 0.5f
        val baseRightY = TURRET_HALF_WIDTH

        val path = Path()
        path.moveTo(tipX * cos - tipY * sin, tipX * sin + tipY * cos)
        path.lineTo(baseLeftX * cos - baseLeftY * sin, baseLeftX * sin + baseLeftY * cos)
        path.lineTo(baseRightX * cos - baseRightY * sin, baseRightX * sin + baseRightY * cos)
        path.close()

        drawPath(path, turretFillColor, style = Fill)
        drawPath(path, turretColor, style = Stroke(width = 1.5f))
    }

    override fun update(deltaTimeInMilliseconds: Int) {
        super.update(deltaTimeInMilliseconds)
        target?.let {
            if (!it.isValidTarget()) {
                target = null
                return
            }

            // TODO: create aim point based on target velocity, projectile velocity, and distance
            val angleToTarget = body.position.angleTowards(it.body.position)

            val targetRotation = angleToTarget - body.rotation
            currentRotation = currentRotation.rotateTowards(
                targetRotation,
                rotationSpeed / deltaTimeInMilliseconds
            )
            gun.angleToTarget = angleToTarget - body.rotation - currentRotation
        }

        if (target == null) {
            gun.angleToTarget = null
            currentRotation =
                currentRotation.rotateTowards(0.rad, rotationSpeed / deltaTimeInMilliseconds)
        }

        body.rotation += currentRotation
        gun.update(deltaTimeInMilliseconds, actorManager)
    }

    override val drawingOrder: Float = DrawOrder.PLAYER_TURRET
}
