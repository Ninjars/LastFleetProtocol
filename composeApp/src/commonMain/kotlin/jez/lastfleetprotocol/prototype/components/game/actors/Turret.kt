package jez.lastfleetprotocol.prototype.components.game.actors

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.helpers.extensions.deg
import com.pandulapeter.kubriko.helpers.extensions.get
import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.sprites.SpriteManager
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import jez.lastfleetprotocol.prototype.components.game.data.DrawOrder
import jez.lastfleetprotocol.prototype.components.game.data.Gun
import jez.lastfleetprotocol.prototype.components.game.data.GunData
import jez.lastfleetprotocol.prototype.components.game.utils.rotateTowards
import lastfleetprotocol.composeapp.generated.resources.Res
import lastfleetprotocol.composeapp.generated.resources.turret_simple_1
import kotlin.math.atan2

class Turret(
    parent: Parent,
    offsetFromParentPivot: SceneOffset,
    private val pivot: SceneOffset,
    private val gunData: GunData,
    private val rotationSpeed: AngleRadians = 0.01f.deg.rad,
) : Child(
    parent = parent,
    offsetFromParentPivot = offsetFromParentPivot,
) {
    private lateinit var spriteManager: SpriteManager
    private val sprite: ImageBitmap by lazy {
        spriteManager.get(Res.drawable.turret_simple_1) ?: throw RuntimeException("unable to load asset for Turret")
    }

    private var currentRotation: AngleRadians = AngleRadians.Zero

    override val body = BoxBody()

    var target: Targetable? = null

    private val gun: Gun by lazy {
        Gun(
            turretBody = body,
            muzzleOffset = SceneOffset(Offset(0f, -pivot.y.raw)),
            gunData = gunData
        )
    }

    override fun onAdded(kubriko: Kubriko) {
        super.onAdded(kubriko)
        spriteManager = kubriko.get()
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
            val angleToTarget = angleOfLine(body.position, it.body.position)

            currentRotation = currentRotation.rotateTowards(angleToTarget, rotationSpeed * deltaTimeInMilliseconds)
            gun.angleToTarget = angleToTarget - body.rotation - currentRotation
        }

        // TODO: return to default facing if no target
        gun.angleToTarget = null

        body.rotation = currentRotation
    }

    private fun angleOfLine(from: SceneOffset, to: SceneOffset): AngleRadians =
        atan2((to.y - from.y).raw, (to.x - from.x).raw).rad

    override val drawingOrder: Float = DrawOrder.PLAYER_TURRET
}