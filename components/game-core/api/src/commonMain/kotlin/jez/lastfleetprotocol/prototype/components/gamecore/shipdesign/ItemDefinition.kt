package jez.lastfleetprotocol.prototype.components.gamecore.shipdesign

import com.pandulapeter.kubriko.types.SceneOffset
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ItemDefinition(
    val id: String,
    val name: String,
    val vertices: List<@Serializable(with = SceneOffsetSerializer::class) SceneOffset>,
    val attributes: ItemAttributes,
) {
    val itemType: ItemType by lazy {
        when (attributes) {
            is ItemAttributes.HullAttributes -> ItemType.HULL
            is ItemAttributes.ModuleAttributes -> ItemType.MODULE
            is ItemAttributes.TurretAttributes -> ItemType.TURRET
            is ItemAttributes.KeelAttributes -> ItemType.KEEL
        }
    }
}

@Serializable
enum class ItemType { HULL, MODULE, TURRET, KEEL }

/**
 * Shared contract for item attributes whose pieces form part of the ship's outer
 * silhouette — i.e., contribute to hull polygon geometry, collision, and per-axis
 * drag aggregation. Implemented by [ItemAttributes.HullAttributes] and
 * [ItemAttributes.KeelAttributes]. Sits orthogonally to the [ItemAttributes] sealed
 * hierarchy so drag aggregation can iterate one combined list without cast-fallthrough.
 * Future exterior part types (wings, stabilisers) can implement this without a
 * cascading refactor of drag consumers.
 */
interface ExternalPartAttributes {
    val armour: SerializableArmourStats
    val forwardDragModifier: Float
    val lateralDragModifier: Float
    val reverseDragModifier: Float
}

@Serializable
sealed interface ItemAttributes {
    val mass: Float

    @Serializable
    @SerialName("hull")
    data class HullAttributes(
        override val armour: SerializableArmourStats,
        val sizeCategory: String,
        override val mass: Float,
        override val forwardDragModifier: Float = 1.0f,
        override val lateralDragModifier: Float = 1.0f,
        override val reverseDragModifier: Float = 1.0f,
    ) : ItemAttributes, ExternalPartAttributes

    @Serializable
    @SerialName("module")
    data class ModuleAttributes(
        val systemType: String,
        val maxHp: Float,
        val density: Float,
        override val mass: Float,
        val forwardThrust: Float = 0f,
        val lateralThrust: Float = 0f,
        val reverseThrust: Float = 0f,
        val angularThrust: Float = 0f,
    ) : ItemAttributes

    @Serializable
    @SerialName("turret")
    data class TurretAttributes(
        override val mass: Float,
        val sizeCategory: String,
        val isFixed: Boolean = false,
        val defaultFacing: Float = 0f,
        val isLimitedRotation: Boolean = false,
        val minAngle: Float = 0f,
        val maxAngle: Float = 0f,
    ) : ItemAttributes

    /**
     * Attributes for a Keel — the mandatory one-per-ship hull piece that defines
     * the ship's class and provides its lift budget. Structurally a hull piece
     * (implements [ExternalPartAttributes]) plus three Keel-specific fields:
     * [lift] (the ship's lift capacity), [shipClass] (fighter/frigate/cruiser/etc.,
     * free-form label in Slice B), and [maxHp] (HP of the KEEL internal system,
     * read by the converter when emitting the KEEL [InternalSystemSpec]).
     */
    @Serializable
    @SerialName("keel")
    data class KeelAttributes(
        override val armour: SerializableArmourStats,
        val sizeCategory: String,
        override val mass: Float,
        override val forwardDragModifier: Float = 1.0f,
        override val lateralDragModifier: Float = 1.0f,
        override val reverseDragModifier: Float = 1.0f,
        val maxHp: Float = 100f,
        val lift: Float = 0f,
        val shipClass: String = "",
    ) : ItemAttributes, ExternalPartAttributes
}
