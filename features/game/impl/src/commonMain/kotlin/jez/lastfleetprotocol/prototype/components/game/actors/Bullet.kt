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
import jez.lastfleetprotocol.prototype.components.game.combat.ArcDamageRouter
import jez.lastfleetprotocol.prototype.components.game.combat.ImpactOutcome
import jez.lastfleetprotocol.prototype.components.game.combat.KineticImpactResolver
import jez.lastfleetprotocol.prototype.components.game.data.DrawOrder
import jez.lastfleetprotocol.prototype.components.game.data.ProjectileStats
import jez.lastfleetprotocol.prototype.components.game.managers.AudioManager
import org.jetbrains.compose.resources.DrawableResource
import kotlin.math.sqrt
import kotlin.reflect.KClass

data class BulletData(
    val drawable: DrawableResource,
)

internal class Bullet(
    initialPosition: SceneOffset,
    initialRotation: AngleRadians,
    private val velocity: SceneOffset,
    private val bulletData: BulletData,
    val projectileStats: ProjectileStats,
    override val collidableTypes: List<KClass<out Collidable>>,
) : Visible, Dynamic, CollisionDetector {
    override val isAlwaysActive: Boolean = true
    private var remainingLifetimeMs: Int = projectileStats.lifetimeMs
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
            remainingLifetimeMs -= deltaTimeInMilliseconds
            if (remainingLifetimeMs <= 0) {
                actorManager.remove(this)
                return
            }
            body.position += velocity * 0.001f * deltaTimeInMilliseconds
            collisionMask.position = body.position
        }
    }

    override fun onCollisionDetected(collidables: List<Collidable>) {
        for (collidable in collidables) {
            val ship = collidable as? Ship ?: continue
            if (!ship.isValidTarget()) continue

            // Approximate contact normal: direction from bullet to ship centre, inverted
            // (pointing outward from ship hull toward bullet).
            val dx = body.position.x.raw - ship.body.position.x.raw
            val dy = body.position.y.raw - ship.body.position.y.raw
            val dist = sqrt(dx * dx + dy * dy)
            val contactNormal = if (dist > 1e-6f) {
                SceneOffset((dx / dist).sceneUnit, (dy / dist).sceneUnit)
            } else {
                SceneOffset(0f.sceneUnit, (-1f).sceneUnit)
            }

            val contactPoint = body.position

            val outcome = KineticImpactResolver.resolve(
                projectile = projectileStats,
                velocity = velocity,
                contactNormal = contactNormal,
                armour = ship.spec.hull.armour,
                combatStats = ship.spec.combatStats,
            )

            when (outcome) {
                is ImpactOutcome.Miss -> {
                    // Bullet continues — do nothing
                }

                is ImpactOutcome.Ricochet,
                is ImpactOutcome.Deflect -> {
                    actorManager.remove(this)
                    return
                }

                is ImpactOutcome.Penetrate -> {
                    ArcDamageRouter.routeDamage(
                        impactWorld = contactPoint,
                        shipPosition = ship.body.position,
                        shipRotation = ship.body.rotation,
                        shipSystems = ship.shipSystems,
                        damage = outcome.damage,
                        armourPiercing = outcome.armourPiercing,
                    )
                    actorManager.remove(this)
                    return
                }
            }
        }
    }

    override fun DrawScope.draw() {
        drawImage(sprite)
    }
}