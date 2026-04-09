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
            ),
        ),
        ItemDefinition(
            id = "turret_light",
            name = "Light Turret",
            vertices = turretOctagonVertices,
            attributes = ItemAttributes.TurretAttributes(
                sizeCategory = "light",
            ),
        ),
        ItemDefinition(
            id = "turret_heavy",
            name = "Heavy Turret",
            vertices = turretOctagonVertices,
            attributes = ItemAttributes.TurretAttributes(
                sizeCategory = "heavy",
            ),
        ),
    )

    val allItems: List<ItemDefinition> = hullItems + moduleItems + turretItems
}
