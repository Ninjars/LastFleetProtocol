package jez.lastfleetprotocol.prototype.components.game.actors

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.actor.traits.Dynamic
import com.pandulapeter.kubriko.actor.traits.Visible
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.CollisionDetector
import com.pandulapeter.kubriko.collision.mask.CircleCollisionMask
import com.pandulapeter.kubriko.helpers.extensions.get
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.manager.StateManager
import com.pandulapeter.kubriko.manager.ViewportManager
import com.pandulapeter.kubriko.sprites.SpriteManager
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import jez.lastfleetprotocol.prototype.components.game.data.DrawOrder
import jez.lastfleetprotocol.prototype.components.game.managers.AudioManager
import org.jetbrains.compose.resources.DrawableResource
import kotlin.reflect.KClass

data class BulletData(
    val drawable: DrawableResource,
)

internal class Bullet(
    initialPosition: SceneOffset,
    initialRotation: AngleRadians,
    private val velocity: SceneOffset,
    private val bulletData: BulletData,
    override val collidableTypes: List<KClass<out Collidable>>,
) : Visible, Dynamic, CollisionDetector {
    override val body = BoxBody(
        initialPosition = initialPosition,
        initialRotation = initialRotation,
    )
    private val radius = body.size.width / 2f
    override val collisionMask = CircleCollisionMask(
        initialRadius = radius,
        initialPosition = body.position,
    )
    protected lateinit var actorManager: ActorManager
    protected lateinit var audioManager: AudioManager
    private lateinit var stateManager: StateManager
    private lateinit var viewportManager: ViewportManager
    private lateinit var sprite: ImageBitmap

    override val drawingOrder = DrawOrder.BULLET

    override fun onAdded(kubriko: Kubriko) {
        actorManager = kubriko.get()
        audioManager = kubriko.get()
        stateManager = kubriko.get()
        viewportManager = kubriko.get()

        sprite = kubriko.get<SpriteManager>().get(bulletData.drawable) ?: throw RuntimeException()
        body.size = SceneSize(
            width = sprite.width.sceneUnit,
            height = sprite.height.sceneUnit,
        )
    }

    override fun update(deltaTimeInMilliseconds: Int) {
        if (stateManager.isRunning.value) {
            body.position += velocity * 0.001f * deltaTimeInMilliseconds
            collisionMask.position = body.position
            // TODO: limit bullet lifespan
        }
    }

    override fun onCollisionDetected(collidables: List<Collidable>) {
        // TODO("Not yet implemented")
    }

    override fun DrawScope.draw() {
        drawImage(sprite)
    }
}