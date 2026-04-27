package jez.lastfleetprotocol.prototype.components.gamecore.scenarios

import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PendingScenarioTest {

    private val sampleSlots = listOf(
        SpawnSlotConfig(
            designName = "player_ship",
            position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
            teamId = "player",
            withAI = false,
            drawOrder = 10f,
        ),
    )

    @Test
    fun freshHolderHasNullSlots() {
        assertNull(PendingScenario().slots)
    }

    @Test
    fun consumeOnFreshHolderReturnsNull() {
        assertNull(PendingScenario().consume())
    }

    @Test
    fun consumeReturnsSetSlotsAndClears() {
        val pending = PendingScenario().apply { slots = sampleSlots }
        assertEquals(sampleSlots, pending.consume())
        assertNull(pending.slots, "consume must null slots so the next reader runs the demo path")
    }

    @Test
    fun consumeIsIdempotent_secondCallReturnsNull() {
        val pending = PendingScenario().apply { slots = sampleSlots }
        pending.consume()
        assertNull(pending.consume())
    }

    @Test
    fun setAfterConsumeWorks() {
        val pending = PendingScenario().apply { slots = sampleSlots }
        pending.consume()
        pending.slots = sampleSlots
        assertEquals(sampleSlots, pending.consume())
    }
}
