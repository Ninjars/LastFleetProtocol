package jez.lastfleetprotocol.prototype.components.gamecore.shipdesign

import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
            hullPieces = emptyList(),
            placedHulls = emptyList(),
            placedModules = emptyList(),
            placedTurrets = emptyList(),
        )

        val encoded = json.encodeToString(ShipDesign.serializer(), design)
        val decoded = json.decodeFromString(ShipDesign.serializer(), encoded)

        assertEquals(design.name, decoded.name)
        assertTrue(decoded.hullPieces.isEmpty())
        assertTrue(decoded.placedHulls.isEmpty())
        assertTrue(decoded.placedModules.isEmpty())
        assertTrue(decoded.placedTurrets.isEmpty())
    }

    @Test
    fun fullShipDesignRoundTrips() {
        val design = ShipDesign(
            name = "test-ship",
            hullPieces = listOf(
                HullPieceDefinition(
                    id = "hull-1",
                    vertices = listOf(
                        SceneOffset(10f.sceneUnit, 0f.sceneUnit),
                        SceneOffset(-5f.sceneUnit, 8f.sceneUnit),
                        SceneOffset(-5f.sceneUnit, (-8f).sceneUnit),
                    ),
                    armour = SerializableArmourStats(hardness = 1.5f, density = 2.0f),
                    sizeCategory = "medium",
                    mass = 100f,
                ),
            ),
            placedHulls = listOf(
                PlacedHullPiece(
                    id = "placed-hull-1",
                    hullPieceId = "hull-1",
                    position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
                    rotation = 0f.rad,
                ),
            ),
            placedModules = listOf(
                PlacedModule(
                    id = "module-1",
                    systemType = "Reactor",
                    position = SceneOffset(1f.sceneUnit, 2f.sceneUnit),
                    rotation = 1.5f.rad,
                    parentHullId = "placed-hull-1",
                ),
            ),
            placedTurrets = listOf(
                PlacedTurret(
                    id = "turret-1",
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
        assertEquals(design.hullPieces.size, decoded.hullPieces.size)
        assertEquals(design.placedHulls.size, decoded.placedHulls.size)
        assertEquals(design.placedModules.size, decoded.placedModules.size)
        assertEquals(design.placedTurrets.size, decoded.placedTurrets.size)

        assertEquals("hull-1", decoded.hullPieces[0].id)
        assertEquals("placed-hull-1", decoded.placedHulls[0].id)
        assertEquals("hull-1", decoded.placedHulls[0].hullPieceId)
        assertEquals("module-1", decoded.placedModules[0].id)
        assertEquals("placed-hull-1", decoded.placedModules[0].parentHullId)
        assertEquals("turret-1", decoded.placedTurrets[0].id)
        assertEquals("placed-hull-1", decoded.placedTurrets[0].parentHullId)
    }

    @Test
    fun sceneOffsetSerializesAsXY() {
        val piece = PlacedHullPiece(
            id = "test",
            hullPieceId = "hull",
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
            hullPieceId = "hull",
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
            hullPieces = listOf(
                HullPieceDefinition(
                    id = "unique-hull-def-42",
                    vertices = emptyList(),
                    armour = SerializableArmourStats(hardness = 1f, density = 1f),
                    sizeCategory = "small",
                    mass = 50f,
                ),
            ),
            placedHulls = listOf(
                PlacedHullPiece(
                    id = "instance-99",
                    hullPieceId = "unique-hull-def-42",
                    position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
                    rotation = 0f.rad,
                ),
            ),
            placedModules = listOf(
                PlacedModule(
                    id = "mod-alpha",
                    systemType = "Engine",
                    position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
                    rotation = 0f.rad,
                    parentHullId = "instance-99",
                ),
            ),
            placedTurrets = emptyList(),
        )

        val encoded = json.encodeToString(ShipDesign.serializer(), design)
        val decoded = json.decodeFromString(ShipDesign.serializer(), encoded)

        assertEquals("unique-hull-def-42", decoded.hullPieces[0].id)
        assertEquals("instance-99", decoded.placedHulls[0].id)
        assertEquals("unique-hull-def-42", decoded.placedHulls[0].hullPieceId)
        assertEquals("mod-alpha", decoded.placedModules[0].id)
        assertEquals("instance-99", decoded.placedModules[0].parentHullId)
    }
}
