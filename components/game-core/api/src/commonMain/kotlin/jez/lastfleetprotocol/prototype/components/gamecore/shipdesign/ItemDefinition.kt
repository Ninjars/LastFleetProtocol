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
        }
    }
}

@Serializable
enum class ItemType { HULL, MODULE, TURRET }

@Serializable
sealed interface ItemAttributes {
    @Serializable
    @SerialName("hull")
    data class HullAttributes(
        val armour: SerializableArmourStats,
        val sizeCategory: String,
        val mass: Float,
    ) : ItemAttributes

    @Serializable
    @SerialName("module")
    data class ModuleAttributes(
        val systemType: String,
        val maxHp: Float,
        val density: Float,
        val mass: Float,
        val forwardThrust: Float = 0f,
        val lateralThrust: Float = 0f,
        val reverseThrust: Float = 0f,
        val angularThrust: Float = 0f,
    ) : ItemAttributes

    @Serializable
    @SerialName("turret")
    data class TurretAttributes(
        val sizeCategory: String,
        val isFixed: Boolean = false,
        val defaultFacing: Float = 0f,
        val isLimitedRotation: Boolean = false,
        val minAngle: Float = 0f,
        val maxAngle: Float = 0f,
    ) : ItemAttributes
}
