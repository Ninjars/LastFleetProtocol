package jez.lastfleetprotocol.prototype.components.game.actors

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.helpers.extensions.angleTowards
import com.pandulapeter.kubriko.helpers.extensions.deg
import com.pandulapeter.kubriko.helpers.extensions.get
import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.rotateTowards
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.sprites.SpriteManager
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import jez.lastfleetprotocol.prototype.components.game.data.DrawOrder
import jez.lastfleetprotocol.prototype.components.game.data.Gun
import jez.lastfleetprotocol.prototype.components.game.data.GunData

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
    private lateinit var spriteManager: SpriteManager
    private val sprite: ImageBitmap by lazy {
        spriteManager.get(gunData.drawable)
            ?: throw RuntimeException("unable to load asset for Turret")
    }

    private var currentRotation: AngleRadians = AngleRadians.Zero

    override val body = BoxBody()

    var target: Targetable? = null

    private val gun: Gun by lazy {
        Gun(
            turretBody = body,
            muzzleOffset = SceneOffset(Offset(pivot.x.raw / 2f, 0f)),
            gunData = gunData,
            teamId = teamId,
        )
    }

    override fun onAdded(kubriko: Kubriko) {
        super.onAdded(kubriko)
        spriteManager = kubriko.get()
        actorManager = kubriko.get()
        body.size = SceneSize(sprite.width.sceneUnit, sprite.height.sceneUnit)
        body.pivot = pivot
    }

    override fun DrawScope.draw() {
        drawImage(sprite)
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