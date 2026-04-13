package jez.lastfleetprotocol.prototype.components.game.actors

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.Actor
import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.actor.traits.Dynamic
import com.pandulapeter.kubriko.helpers.extensions.get
import com.pandulapeter.kubriko.helpers.extensions.length
import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.manager.ViewportManager
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import jez.lastfleetprotocol.prototype.components.game.ai.AIModule
import jez.lastfleetprotocol.prototype.components.game.navigation.ShipNavigator
import jez.lastfleetprotocol.prototype.components.game.physics.ShipPhysics
import jez.lastfleetprotocol.prototype.components.game.systems.ShipSystems
import kotlin.math.max
import kotlin.math.min

/**
 * A ship in the game world. Can be player-controlled or AI-controlled depending
 * on the [aiModules] provided. Team membership is identified by [teamId], which
 * is propagated to projectiles to prevent friendly fire.
 *
 * Collision detection is handled by [HullCollider] child actors — one per hull
 * piece. Ship itself does not participate in collision; bullets hit hull colliders
 * and route damage back through [shipSystems].
 *
 * Rendered as polygon outlines (hull silhouette + nose marker), not sprites.
 */
class Ship(
    internal val spec: ShipSpec,
    initialPosition: SceneOffset,
    val teamId: String,
    val targetProvider: () -> List<Ship> = { emptyList() },
    private val aiModules: List<AIModule> = emptyList(),
    initialVelocity: SceneOffset = SceneOffset.Zero,
    private val turrets: List<Turret> = emptyList(),
    val shipSystems: ShipSystems = ShipSystems(emptyList()),
    private val drawOrder: Float = 0f,
) : Targetable, Dynamic, Parent {

    var isDestroyed: Boolean = false
        private set

    var onDestroyedCallback: ((Ship) -> Unit)? = null

    private lateinit var viewportManager: ViewportManager
    private lateinit var actorManager: ActorManager

    /** Hull colliders — one per hull piece. Created at construction, added as child actors. */
    internal val hullColliders: List<HullCollider> = spec.hulls.map { hull ->
        HullCollider(
            parentShip = this,
            hullDefinition = hull,
            initialPosition = initialPosition,
        )
    }

    override val actors: List<Actor> = hullColliders + turrets

    override var velocity: SceneOffset
        get() = physics.velocity
        set(value) {
            physics.velocity = value
        }

    /** Current movement destination, exposed for debug visualisation. */
    var destination: SceneOffset? = null
        private set

    internal val physics: ShipPhysics = ShipPhysics(
        mass = spec.totalMass,
        initialVelocity = initialVelocity,
    )

    private val navigator: ShipNavigator = ShipNavigator(
        hullRadius = computeHullRadius(),
    )

    override val body: BoxBody = BoxBody(
        initialPosition = initialPosition,
    )

    override val isAlwaysActive: Boolean = true
    override val drawingOrder: Float = drawOrder

    /** Team colour for hull fill — cyan-family for player, red-family for enemy. */
    private val teamFillColor: Color
        get() = if (teamId == TEAM_PLAYER) {
            Color(0f, 0.7f, 0.8f, 0.25f) // translucent cyan
        } else {
            Color(0.9f, 0.2f, 0.1f, 0.25f) // translucent red
        }

    private val teamStrokeColor: Color
        get() = if (teamId == TEAM_PLAYER) {
            Color(0f, 0.9f, 1f, 0.9f) // bright cyan
        } else {
            Color(1f, 0.3f, 0.2f, 0.9f) // bright red
        }

    /**
     * Pre-computed hull vertices in local space (relative to body pivot).
     * Each hull is a list of (x, y) pairs for drawing.
     * For single-hull ships (all current defaults) this is one entry.
     */
    private val hullLocalVertices: List<List<Offset>> by lazy {
        val centreOffset = hullBoundsSize.center.raw

        spec.hulls.map { hull ->
            hull.vertices.map { Offset(it.x.raw + centreOffset.x, it.y.raw + centreOffset.y) }
        }
    }

    /** Pre-computed AABB size from all hull vertices, used for body.size */
    private val hullBoundsSize: SceneSize by lazy {
        val allVerts = spec.hulls.flatMap { hull ->
            hull.vertices.map { Offset(it.x.raw, it.y.raw) }
        }
        if (allVerts.isEmpty()) return@lazy SceneSize(10.sceneUnit, 10.sceneUnit)
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        for (v in allVerts) {
            minX = min(minX, v.x)
            maxX = max(maxX, v.x)
            minY = min(minY, v.y)
            maxY = max(maxY, v.y)
        }
        SceneSize((maxX - minX).sceneUnit, (maxY - minY).sceneUnit)
    }

    /** Index of the forward-most vertex (+X in ship-local space) for the nose marker. */
    private val noseVertexIndex: Int by lazy {
        val allVerts = hullLocalVertices.flatten()
        if (allVerts.isEmpty()) 0
        else allVerts.indices.maxByOrNull { allVerts[it].x } ?: 0
    }

    override fun DrawScope.draw() {
        // Draw each hull polygon
        for (verts in hullLocalVertices) {
            if (verts.size < 3) continue

            val path = Path()
            for (i in verts.indices) {
                val rx = verts[i].x
                val ry = verts[i].y
                if (i == 0) path.moveTo(rx, ry) else path.lineTo(rx, ry)
            }
            path.close()

            // Fill with translucent team colour
            drawPath(path, teamFillColor, style = Fill)
            // Stroke with opaque team colour
            drawPath(path, teamStrokeColor, style = Stroke(width = 2f))
        }

        // Nose marker: small triangle at the forward-most vertex, pointing forward
        val allVerts = hullLocalVertices.flatten()
        if (allVerts.isNotEmpty() && noseVertexIndex < allVerts.size) {
            val nose = allVerts[noseVertexIndex]
            val markerSize = 6f
            // Triangle pointing in the +X direction (forward) from the nose vertex
            val tipX = nose.x + markerSize
            val tipY = nose.y
            val baseLeftX = nose.x - markerSize * 0.3f
            val baseLeftY = nose.y - markerSize * 0.6f
            val baseRightX = nose.x - markerSize * 0.3f
            val baseRightY = nose.y + markerSize * 0.6f

            val nosePath = Path()
            nosePath.moveTo(tipX, tipY)
            nosePath.lineTo(baseLeftX, baseLeftY)
            nosePath.lineTo(baseRightX, baseRightY)
            nosePath.close()

            drawPath(nosePath, Color.White, style = Stroke(width = 2f))
        }
    }

    override fun onAdded(kubriko: Kubriko) {
        viewportManager = kubriko.get()
        actorManager = kubriko.get()
        body.size = hullBoundsSize
        body.pivot = body.size.center
    }

    override fun update(deltaTimeInMilliseconds: Int) {
        // Run AI modules
        for (ai in aiModules) {
            ai.update(this, deltaTimeInMilliseconds)
        }

        destination = navigator.navigate(
            movementConfig = spec.movementConfig,
            physics = physics,
            body = body,
            combatTarget = combatTarget,
            destination = destination,
            deltaMs = deltaTimeInMilliseconds,
        )

        val result = physics.integrate(deltaTimeInMilliseconds)
        body.position += result.positionDelta
        body.rotation += result.rotationDelta.rad

        checkDestruction()
    }

    override fun isValidTarget(): Boolean {
        return !isDestroyed
    }

    fun checkDestruction() {
        if (shipSystems.isReactorDestroyed() && !isDestroyed) {
            isDestroyed = true
            onDestroyedCallback?.invoke(this)
            actorManager.remove(this)
        }
    }

    /** The ship's current combat target, used for facing between manoeuvres. */
    var combatTarget: Targetable? = null
        private set

    fun setTarget(mobile: Targetable?) {
        combatTarget = mobile
        for (turret in turrets) {
            turret.target = mobile
        }
    }

    fun moveTo(destination: SceneOffset) {
        this.destination = destination
    }

    /** Test whether a scene-space point is inside any of this ship's hull polygons. */
    fun isPointInHull(point: SceneOffset): Boolean =
        hullColliders.any { it.collisionMask.isSceneOffsetInside(point) }

    /**
     * Compute the average vertex distance across all hulls, used as a
     * bounding radius for the navigator's arrival/braking calculations.
     */
    private fun computeHullRadius(): Float {
        val allVertices = spec.hulls.flatMap { it.vertices }
        return if (allVertices.isNotEmpty()) {
            allVertices.map { it.length().raw }.average().toFloat()
        } else {
            5f // fallback matching ARRIVAL_THRESHOLD
        }
    }

    companion object {
        const val TEAM_PLAYER = "player"
        const val TEAM_ENEMY = "enemy"
    }
}
