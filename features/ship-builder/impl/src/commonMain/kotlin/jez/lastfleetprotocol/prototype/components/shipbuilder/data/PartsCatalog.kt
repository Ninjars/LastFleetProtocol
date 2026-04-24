package jez.lastfleetprotocol.prototype.components.shipbuilder.data

import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemAttributes
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemType
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.SerializableArmourStats
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pre-defined parts available in the ship builder.
 * All entries are [ItemDefinition] instances grouped by [ItemType].
 */
object PartsCatalog {

    val hullItems: List<ItemDefinition> = listOf(
        ItemDefinition(
            id = "hull_player",
            name = "Player Hull",
            vertices = listOf(
                SceneOffset(56f.sceneUnit, 0f.sceneUnit),
                SceneOffset((-30f).sceneUnit, 37f.sceneUnit),
                SceneOffset((-56f).sceneUnit, 20f.sceneUnit),
                SceneOffset((-56f).sceneUnit, (-20f).sceneUnit),
                SceneOffset((-30f).sceneUnit, (-37f).sceneUnit),
            ),
            attributes = ItemAttributes.HullAttributes(
                armour = SerializableArmourStats(hardness = 5f, density = 2f),
                sizeCategory = "medium",
                mass = 50f,
            ),
        ),
        ItemDefinition(
            id = "hull_light",
            name = "Light Hull",
            vertices = listOf(
                SceneOffset(42f.sceneUnit, 0f.sceneUnit),
                SceneOffset((-20f).sceneUnit, 30f.sceneUnit),
                SceneOffset((-42f).sceneUnit, 15f.sceneUnit),
                SceneOffset((-42f).sceneUnit, (-15f).sceneUnit),
                SceneOffset((-20f).sceneUnit, (-30f).sceneUnit),
            ),
            attributes = ItemAttributes.HullAttributes(
                armour = SerializableArmourStats(hardness = 3f, density = 1f),
                sizeCategory = "light",
                mass = 30f,
            ),
        ),
        ItemDefinition(
            id = "hull_medium",
            name = "Medium Hull",
            vertices = listOf(
                SceneOffset(52f.sceneUnit, 0f.sceneUnit),
                SceneOffset((-25f).sceneUnit, 42f.sceneUnit),
                SceneOffset((-52f).sceneUnit, 25f.sceneUnit),
                SceneOffset((-52f).sceneUnit, (-25f).sceneUnit),
                SceneOffset((-25f).sceneUnit, (-42f).sceneUnit),
            ),
            attributes = ItemAttributes.HullAttributes(
                armour = SerializableArmourStats(hardness = 5f, density = 2f),
                sizeCategory = "medium",
                mass = 50f,
            ),
        ),
        ItemDefinition(
            id = "hull_heavy",
            name = "Heavy Hull",
            vertices = listOf(
                SceneOffset(52f.sceneUnit, 0f.sceneUnit),
                SceneOffset((-20f).sceneUnit, 50f.sceneUnit),
                SceneOffset((-52f).sceneUnit, 35f.sceneUnit),
                SceneOffset((-52f).sceneUnit, (-35f).sceneUnit),
                SceneOffset((-20f).sceneUnit, (-50f).sceneUnit),
            ),
            attributes = ItemAttributes.HullAttributes(
                armour = SerializableArmourStats(hardness = 8f, density = 4f),
                sizeCategory = "heavy",
                mass = 100f,
            ),
        ),
    )

    private val moduleSquareVertices: List<SceneOffset> = listOf(
        SceneOffset(7.5f.sceneUnit, (-7.5f).sceneUnit),
        SceneOffset(7.5f.sceneUnit, 7.5f.sceneUnit),
        SceneOffset((-7.5f).sceneUnit, 7.5f.sceneUnit),
        SceneOffset((-7.5f).sceneUnit, (-7.5f).sceneUnit),
    )

    private val turretOctagonVertices: List<SceneOffset> = buildList {
        val radius = 10f
        for (i in 0 until 8) {
            val angle = (2.0 * PI * i / 8).toFloat()
            add(SceneOffset((radius * cos(angle)).sceneUnit, (radius * sin(angle)).sceneUnit))
        }
    }

    val moduleItems: List<ItemDefinition> = listOf(
        ItemDefinition(
            id = "system_reactor",
            name = "Reactor",
            vertices = moduleSquareVertices,
            attributes = ItemAttributes.ModuleAttributes(
                systemType = "REACTOR",
                maxHp = 100f,
                density = 8f,
                mass = 20f,
            ),
        ),
        ItemDefinition(
            id = "system_engine",
            name = "Main Engine",
            vertices = moduleSquareVertices,
            attributes = ItemAttributes.ModuleAttributes(
                systemType = "MAIN_ENGINE",
                maxHp = 80f,
                density = 4f,
                mass = 15f,
                forwardThrust = 1200f,
                lateralThrust = 500f,
                reverseThrust = 500f,
                angularThrust = 300f,
            ),
        ),
        ItemDefinition(
            id = "system_bridge",
            name = "Bridge",
            vertices = moduleSquareVertices,
            attributes = ItemAttributes.ModuleAttributes(
                systemType = "BRIDGE",
                maxHp = 60f,
                density = 3f,
                mass = 10f,
            ),
        ),
    )

    val turretItems: List<ItemDefinition> = listOf(
        ItemDefinition(
            id = "turret_standard",
            name = "Standard Turret",
            vertices = turretOctagonVertices,
            attributes = ItemAttributes.TurretAttributes(
                sizeCategory = "medium",
                mass = 10f,
            ),
        ),
        ItemDefinition(
            id = "turret_light",
            name = "Light Turret",
            vertices = turretOctagonVertices,
            attributes = ItemAttributes.TurretAttributes(
                sizeCategory = "light",
                mass = 5f,
            ),
        ),
        ItemDefinition(
            id = "turret_heavy",
            name = "Heavy Turret",
            vertices = turretOctagonVertices,
            attributes = ItemAttributes.TurretAttributes(
                sizeCategory = "heavy",
                mass = 20f,
            ),
        ),
    )

    /**
     * Bundled Keel definitions, mirroring the four default ships' Keels. Slice B
     * users pick from this list (+ any custom Keels they authored) when starting a
     * new design. See Unit 5 for the picker flow. Geometry and drag modifiers
     * match the corresponding default ship so a fresh picker selection produces a
     * ship structurally similar to the demo scene's.
     */
    val keelItems: List<ItemDefinition> = listOf(
        ItemDefinition(
            id = "keel_fighter_player",
            name = "Player Fighter Keel",
            vertices = hullItems[0].vertices,
            attributes = ItemAttributes.KeelAttributes(
                armour = SerializableArmourStats(hardness = 5f, density = 2f),
                sizeCategory = "medium",
                mass = 50f,
                forwardDragModifier = 0.7f,
                lateralDragModifier = 1.2f,
                reverseDragModifier = 1.2f,
                maxHp = 150f,
                lift = 200f,
                shipClass = "fighter",
            ),
        ),
        ItemDefinition(
            id = "keel_fighter_light",
            name = "Light Fighter Keel",
            vertices = hullItems[1].vertices,
            attributes = ItemAttributes.KeelAttributes(
                armour = SerializableArmourStats(hardness = 3f, density = 1f),
                sizeCategory = "light",
                mass = 30f,
                forwardDragModifier = 0.8f,
                lateralDragModifier = 0.8f,
                reverseDragModifier = 0.8f,
                maxHp = 100f,
                lift = 120f,
                shipClass = "fighter",
            ),
        ),
        ItemDefinition(
            id = "keel_frigate",
            name = "Frigate Keel",
            vertices = hullItems[2].vertices,
            attributes = ItemAttributes.KeelAttributes(
                armour = SerializableArmourStats(hardness = 5f, density = 2f),
                sizeCategory = "medium",
                mass = 50f,
                forwardDragModifier = 1.0f,
                lateralDragModifier = 1.0f,
                reverseDragModifier = 1.0f,
                maxHp = 130f,
                lift = 180f,
                shipClass = "frigate",
            ),
        ),
        ItemDefinition(
            id = "keel_cruiser",
            name = "Cruiser Keel",
            vertices = hullItems[3].vertices,
            attributes = ItemAttributes.KeelAttributes(
                armour = SerializableArmourStats(hardness = 8f, density = 4f),
                sizeCategory = "heavy",
                mass = 100f,
                forwardDragModifier = 1.3f,
                lateralDragModifier = 1.8f,
                reverseDragModifier = 1.8f,
                maxHp = 200f,
                lift = 350f,
                shipClass = "cruiser",
            ),
        ),
    )

    val allItems: List<ItemDefinition> = hullItems + moduleItems + turretItems + keelItems
}
