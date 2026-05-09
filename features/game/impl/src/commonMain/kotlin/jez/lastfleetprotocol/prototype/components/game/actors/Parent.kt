package jez.lastfleetprotocol.prototype.components.game.actors

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.actor.traits.Group
import com.pandulapeter.kubriko.types.SceneOffset

interface Parent : Group {
    abstract val body: BoxBody

    /**
     * Linear velocity of the parent rig in scene-units per second. Read by
     * children for shooter-relative computations — bullet inheritance at spawn
     * (Gun.spawnBullet) and lead-aim solving (Turret.update). Stationary
     * platforms can return [SceneOffset.Zero].
     */
    val velocity: SceneOffset
}