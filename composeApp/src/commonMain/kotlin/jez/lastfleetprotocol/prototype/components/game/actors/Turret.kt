package jez.lastfleetprotocol.prototype.components.game.actors

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.body.BoxBody
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
import lastfleetprotocol.composeapp.generated.resources.Res
import lastfleetprotocol.composeapp.generated.resources.turret_simple_1
import kotlin.math.atan2

class Turret(
    parent: Parent,
    offsetFromParentPivot: SceneOffset,
    private val pivot: SceneOffset,
    private val gunData: GunData,
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
        if (target?.isValidTarget()?.not() ?: false) {
            target = null
        }
        body.rotation += currentRotation

        gun.angleToTarget = target?.let {
            // TODO: create aim point based on target velocity, projectile velocity, and distance
            body.angleTo(it.body.position)
        }
    }

    private fun BoxBody.angleTo(point: SceneOffset): AngleRadians =
        atan2((point.y - position.y).raw, (point.x - position.x).raw).rad

    private fun BoxBody.relativeAngleTo(point: SceneOffset): AngleRadians =
        (relativeAngleTo(point) - rotation).normalized.rad

    override val drawingOrder: Float = DrawOrder.PLAYER_TURRET
}