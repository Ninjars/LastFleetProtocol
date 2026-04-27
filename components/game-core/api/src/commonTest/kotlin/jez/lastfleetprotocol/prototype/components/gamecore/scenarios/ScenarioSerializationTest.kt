package jez.lastfleetprotocol.prototype.components.gamecore.scenarios

import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ScenarioSerializationTest {

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    @Test
    fun roundTripsAnEmptyScenario() {
        val original = Scenario(name = "empty")
        val encoded = json.encodeToString(Scenario.serializer(), original)
        val decoded = json.decodeFromString(Scenario.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun roundTripsAScenarioWithSlots() {
        val original = Scenario(
            name = "two-vs-two",
            slots = listOf(
                SpawnSlotConfig(
                    designName = "player_ship",
                    position = SceneOffset((-100f).sceneUnit, 0f.sceneUnit),
                    teamId = "player",
                    withAI = false,
                    drawOrder = 10f,
                ),
                SpawnSlotConfig(
                    designName = "enemy_heavy",
                    position = SceneOffset(200f.sceneUnit, 50f.sceneUnit),
                    teamId = "enemy",
                    withAI = true,
                    drawOrder = 20f,
                ),
            ),
        )
        val encoded = json.encodeToString(Scenario.serializer(), original)
        val decoded = json.decodeFromString(Scenario.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun currentVersionIsOne() {
        assertEquals(1, Scenario.CURRENT_VERSION)
    }
}
