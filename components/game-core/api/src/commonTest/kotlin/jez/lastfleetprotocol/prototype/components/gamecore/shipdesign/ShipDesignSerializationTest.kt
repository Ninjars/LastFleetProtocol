package jez.lastfleetprotocol.prototype.components.gamecore.shipdesign

import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShipDesignSerializationTest {

    private val json = Json { prettyPrint = false }

    @Test
    fun emptyShipDesignRoundTrips() {
        val design = ShipDesign(
            name = "empty",
            itemDefinitions = emptyList(),
            placedHulls = emptyList(),
            placedModules = emptyList(),
            placedTurrets = emptyList(),
        )

        val encoded = json.encodeToString(ShipDesign.serializer(), design)
        val decoded = json.decodeFromString(ShipDesign.serializer(), encoded)

        assertEquals(design.name, decoded.name)
        assertEquals(2, decoded.formatVersion)
        assertTrue(decoded.itemDefinitions.isEmpty())
        assertTrue(decoded.placedHulls.isEmpty())
        assertTrue(decoded.placedModules.isEmpty())
        assertTrue(decoded.placedTurrets.isEmpty())
    }

    @Test
    fun fullShipDesignRoundTrips() {
        val design = ShipDesign(
            name = "test-ship",
            itemDefinitions = listOf(
                ItemDefinition(
                    id = "hull-1",
                    name = "Test Hull",
                    vertices = listOf(
                        SceneOffset(10f.sceneUnit, 0f.sceneUnit),
                        SceneOffset(-5f.sceneUnit, 8f.sceneUnit),
                        SceneOffset(-5f.sceneUnit, (-8f).sceneUnit),
                    ),
                    attributes = ItemAttributes.HullAttributes(
                        armour = SerializableArmourStats(hardness = 1.5f, density = 2.0f),
                        sizeCategory = "medium",
                        mass = 100f,
                    ),
                ),
                ItemDefinition(
                    id = "module-def-1",
                    name = "Reactor",
                    vertices = emptyList(),
                    attributes = ItemAttributes.ModuleAttributes(
                        systemType = "REACTOR",
                        maxHp = 100f,
                        density = 8f,
                        mass = 20f,
                    ),
                ),
                ItemDefinition(
                    id = "turret-def-1",
                    name = "Standard Turret",
                    vertices = emptyList(),
                    attributes = ItemAttributes.TurretAttributes(
                        sizeCategory = "medium",
                    ),
                ),
            ),
            placedHulls = listOf(
                PlacedHullPiece(
                    id = "placed-hull-1",
                    itemDefinitionId = "hull-1",
                    position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
                    rotation = 0f.rad,
                ),
            ),
            placedModules = listOf(
                PlacedModule(
                    id = "module-1",
                    itemDefinitionId = "module-def-1",
                    systemType = "REACTOR",
                    position = SceneOffset(1f.sceneUnit, 2f.sceneUnit),
                    rotation = 1.5f.rad,
                    parentHullId = "placed-hull-1",
                ),
            ),
            placedTurrets = listOf(
                PlacedTurret(
                    id = "turret-1",
                    itemDefinitionId = "turret-def-1",
                    turretConfigId = "standard",
                    position = SceneOffset(5f.sceneUnit, 0f.sceneUnit),
                    rotation = 0f.rad,
                    parentHullId = "placed-hull-1",
                ),
            ),
        )

        val encoded = json.encodeToString(ShipDesign.serializer(), design)
        val decoded = json.decodeFromString(ShipDesign.serializer(), encoded)

        assertEquals(design.name, decoded.name)
        assertEquals(design.itemDefinitions.size, decoded.itemDefinitions.size)
        assertEquals(design.placedHulls.size, decoded.placedHulls.size)
        assertEquals(design.placedModules.size, decoded.placedModules.size)
        assertEquals(design.placedTurrets.size, decoded.placedTurrets.size)

        assertEquals("hull-1", decoded.itemDefinitions[0].id)
        assertEquals(ItemType.HULL, decoded.itemDefinitions[0].itemType)
        assertEquals("placed-hull-1", decoded.placedHulls[0].id)
        assertEquals("hull-1", decoded.placedHulls[0].itemDefinitionId)
        assertEquals("module-1", decoded.placedModules[0].id)
        assertEquals("placed-hull-1", decoded.placedModules[0].parentHullId)
        assertEquals("turret-1", decoded.placedTurrets[0].id)
        assertEquals("placed-hull-1", decoded.placedTurrets[0].parentHullId)
    }

    @Test
    fun sceneOffsetSerializesAsXY() {
        val piece = PlacedHullPiece(
            id = "test",
            itemDefinitionId = "hull",
            position = SceneOffset(3.5f.sceneUnit, (-7.2f).sceneUnit),
            rotation = 0f.rad,
        )

        val encoded = json.encodeToString(PlacedHullPiece.serializer(), piece)
        val jsonElement = json.parseToJsonElement(encoded).jsonObject
        val positionObj = jsonElement["position"]!!.jsonObject

        assertNotNull(positionObj["x"])
        assertNotNull(positionObj["y"])
        assertEquals(3.5f, positionObj["x"]!!.jsonPrimitive.float)
        assertEquals(-7.2f, positionObj["y"]!!.jsonPrimitive.float)
    }

    @Test
    fun angleRadiansSerializesAsFloat() {
        val piece = PlacedHullPiece(
            id = "test",
            itemDefinitionId = "hull",
            position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
            rotation = 1.5f.rad,
        )

        val encoded = json.encodeToString(PlacedHullPiece.serializer(), piece)
        val jsonElement = json.parseToJsonElement(encoded).jsonObject
        val rotationValue = jsonElement["rotation"]!!.jsonPrimitive.float

        // 1.5 rad is already in [0, 2PI) so normalized should be 1.5
        assertEquals(1.5f, rotationValue)
    }

    @Test
    fun idsMatchAfterRoundTrip() {
        val design = ShipDesign(
            name = "id-test",
            itemDefinitions = listOf(
                ItemDefinition(
                    id = "unique-hull-def-42",
                    name = "Test Hull",
                    vertices = emptyList(),
                    attributes = ItemAttributes.HullAttributes(
                        armour = SerializableArmourStats(hardness = 1f, density = 1f),
                        sizeCategory = "small",
                        mass = 50f,
                    ),
                ),
                ItemDefinition(
                    id = "engine-def-1",
                    name = "Engine",
                    vertices = emptyList(),
                    attributes = ItemAttributes.ModuleAttributes(
                        systemType = "MAIN_ENGINE",
                        maxHp = 80f,
                        density = 4f,
                        mass = 15f,
                        forwardThrust = 1200f,
                    ),
                ),
            ),
            placedHulls = listOf(
                PlacedHullPiece(
                    id = "instance-99",
                    itemDefinitionId = "unique-hull-def-42",
                    position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
                    rotation = 0f.rad,
                ),
            ),
            placedModules = listOf(
                PlacedModule(
                    id = "mod-alpha",
                    itemDefinitionId = "engine-def-1",
                    systemType = "MAIN_ENGINE",
                    position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
                    rotation = 0f.rad,
                    parentHullId = "instance-99",
                ),
            ),
            placedTurrets = emptyList(),
        )

        val encoded = json.encodeToString(ShipDesign.serializer(), design)
        val decoded = json.decodeFromString(ShipDesign.serializer(), encoded)

        assertEquals("unique-hull-def-42", decoded.itemDefinitions[0].id)
        assertEquals("instance-99", decoded.placedHulls[0].id)
        assertEquals("unique-hull-def-42", decoded.placedHulls[0].itemDefinitionId)
        assertEquals("mod-alpha", decoded.placedModules[0].id)
        assertEquals("instance-99", decoded.placedModules[0].parentHullId)
    }

    @Test
    fun hullAttributesRoundTrips() {
        val itemDef = ItemDefinition(
            id = "hull-attrs-test",
            name = "Hull Test",
            vertices = listOf(
                SceneOffset(10f.sceneUnit, 0f.sceneUnit),
                SceneOffset(-5f.sceneUnit, 5f.sceneUnit),
                SceneOffset(-5f.sceneUnit, (-5f).sceneUnit),
            ),
            attributes = ItemAttributes.HullAttributes(
                armour = SerializableArmourStats(hardness = 5f, density = 2f),
                sizeCategory = "heavy",
                mass = 100f,
            ),
        )

        val encoded = json.encodeToString(ItemDefinition.serializer(), itemDef)
        val decoded = json.decodeFromString(ItemDefinition.serializer(), encoded)

        assertEquals(ItemType.HULL, decoded.itemType)
        val attrs = decoded.attributes as ItemAttributes.HullAttributes
        assertEquals(5f, attrs.armour.hardness)
        assertEquals(2f, attrs.armour.density)
        assertEquals("heavy", attrs.sizeCategory)
        assertEquals(100f, attrs.mass)
    }

    @Test
    fun moduleAttributesRoundTrips() {
        val itemDef = ItemDefinition(
            id = "module-attrs-test",
            name = "Engine Test",
            vertices = emptyList(),
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
        )

        val encoded = json.encodeToString(ItemDefinition.serializer(), itemDef)
        val decoded = json.decodeFromString(ItemDefinition.serializer(), encoded)

        assertEquals(ItemType.MODULE, decoded.itemType)
        val attrs = decoded.attributes as ItemAttributes.ModuleAttributes
        assertEquals("MAIN_ENGINE", attrs.systemType)
        assertEquals(80f, attrs.maxHp)
        assertEquals(4f, attrs.density)
        assertEquals(15f, attrs.mass)
        assertEquals(1200f, attrs.forwardThrust)
        assertEquals(500f, attrs.lateralThrust)
        assertEquals(500f, attrs.reverseThrust)
        assertEquals(300f, attrs.angularThrust)
    }

    @Test
    fun turretAttributesRoundTrips() {
        val itemDef = ItemDefinition(
            id = "turret-attrs-test",
            name = "Turret Test",
            vertices = emptyList(),
            attributes = ItemAttributes.TurretAttributes(
                sizeCategory = "medium",
                isFixed = true,
                defaultFacing = 1.57f,
                isLimitedRotation = true,
                minAngle = -0.5f,
                maxAngle = 0.5f,
            ),
        )

        val encoded = json.encodeToString(ItemDefinition.serializer(), itemDef)
        val decoded = json.decodeFromString(ItemDefinition.serializer(), encoded)

        assertEquals(ItemType.TURRET, decoded.itemType)
        val attrs = decoded.attributes as ItemAttributes.TurretAttributes
        assertEquals("medium", attrs.sizeCategory)
        assertEquals(true, attrs.isFixed)
        assertEquals(1.57f, attrs.defaultFacing)
        assertEquals(true, attrs.isLimitedRotation)
        assertEquals(-0.5f, attrs.minAngle)
        assertEquals(0.5f, attrs.maxAngle)
    }

    @Test
    fun itemTypeEnumSerializesCorrectly() {
        for (type in ItemType.entries) {
            val itemDef = ItemDefinition(
                id = "type-test-${type.name}",
                name = "Type Test",
                vertices = emptyList(),
                attributes = when (type) {
                    ItemType.HULL -> ItemAttributes.HullAttributes(
                        armour = SerializableArmourStats(hardness = 1f, density = 1f),
                        sizeCategory = "small",
                        mass = 10f,
                    )
                    ItemType.MODULE -> ItemAttributes.ModuleAttributes(
                        systemType = "REACTOR",
                        maxHp = 100f,
                        density = 8f,
                        mass = 20f,
                    )
                    ItemType.TURRET -> ItemAttributes.TurretAttributes(
                        sizeCategory = "light",
                    )
                },
            )

            val encoded = json.encodeToString(ItemDefinition.serializer(), itemDef)
            val decoded = json.decodeFromString(ItemDefinition.serializer(), encoded)
            assertEquals(type, decoded.itemType)
        }
    }
}
