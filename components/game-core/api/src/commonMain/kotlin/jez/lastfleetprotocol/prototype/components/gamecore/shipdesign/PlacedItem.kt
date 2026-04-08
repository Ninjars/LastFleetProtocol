package jez.lastfleetprotocol.prototype.components.gamecore.shipdesign

import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset

/**
 * Common interface for all placed items on the design canvas.
 * Each placed item is an instance of an [ItemDefinition] with its own
 * position, rotation, and mirror transforms.
 */
sealed interface PlacedItem {
    val id: String
    val itemDefinitionId: String
    val position: SceneOffset
    val rotation: AngleRadians
    val mirrorX: Boolean
    val mirrorY: Boolean
}
