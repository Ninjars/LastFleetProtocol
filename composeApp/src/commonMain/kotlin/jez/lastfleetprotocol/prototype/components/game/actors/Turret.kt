package jez.lastfleetprotocol.prototype.components.game.actors

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.helpers.extensions.get
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.sprites.SpriteManager
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import jez.lastfleetprotocol.prototype.components.game.data.DrawOrder
import lastfleetprotocol.composeapp.generated.resources.Res
import lastfleetprotocol.composeapp.generated.resources.turret_simple_1

class Turret(
    private val parent: BoxBody,
    offsetFromParentPivot: SceneOffset,
    private val pivot: SceneOffset,
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
        body.rotation += currentRotation
    }

    override val drawingOrder: Float = DrawOrder.PLAYER_TURRET
}