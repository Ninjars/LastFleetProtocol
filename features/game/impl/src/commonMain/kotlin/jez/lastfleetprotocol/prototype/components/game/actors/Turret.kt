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

/**
 * Turret size in scene units (isoceles triangle half-dimensions).
 * Length = apex to base centre; width = half the base width.
 */
private const val TURRET_HALF_LENGTH = 16f
private const val TURRET_HALF_WIDTH = 8f

class Turret(
    parent: Parent,
    offsetFromParentPivot: SceneOffset,
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

    private val turretPath by lazy {
        Path().apply {
            lineTo(
                body.size.width.raw,
                body.size.height.raw / 2f,
            )
            lineTo(
                0f,
                body.size.height.raw,
            )
            close()
        }
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
        body.pivot = SceneOffset(0f.sceneUnit, body.size.height / 2f)
    }

    override fun DrawScope.draw() {
        drawPath(turretPath, turretColor, style = Stroke(width = 1.5f))
        drawPath(turretPath, turretFillColor, style = Fill)
    }

    override fun update(deltaTimeInMilliseconds: Int) {
        super.update(deltaTimeInMilliseconds)
        target?.let {
            if (!it.isValidTarget()) {
                target = null
                return@let
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
