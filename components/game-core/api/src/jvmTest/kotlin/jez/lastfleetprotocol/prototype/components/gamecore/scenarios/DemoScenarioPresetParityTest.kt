package jez.lastfleetprotocol.prototype.components.gamecore.scenarios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the canonical demo scenario's shape against the values the game
 * shipped with pre-Item-B, so the Unit 2a refactor and any future edit to
 * `DemoScenarioPreset.SLOTS` fails CI loudly if it drifts.
 *
 * `teamId` and `drawOrder` are inlined literals here because
 * `Ship.TEAM_PLAYER` / `TEAM_ENEMY` and `DrawOrder.PLAYER_SHIP` /
 * `ENEMY_SHIP` live in `:features:game:impl` (downstream of this module).
 * The values must match those constants — this test is the only thing
 * keeping them in sync.
 */
class DemoScenarioPresetParityTest {

    @Test
    fun slotCount_andTeamDistribution_matchCanonical() {
        assertEquals(5, DemoScenarioPreset.SLOTS.size)
        assertEquals(2, DemoScenarioPreset.SLOTS.count { it.teamId == "player" })
        assertEquals(3, DemoScenarioPreset.SLOTS.count { it.teamId == "enemy" })
    }

    @Test
    fun slotDesignNames_matchCanonical() {
        val expected = listOf(
            "player_ship",
            "player_ship",
            "enemy_light",
            "enemy_medium",
            "enemy_heavy",
        )
        assertEquals(expected, DemoScenarioPreset.SLOTS.map { it.designName })
    }

    @Test
    fun slotPositions_matchCanonical() {
        // Player ships at (-300, ±50)
        assertEquals(-300f, DemoScenarioPreset.SLOTS[0].position.x.raw)
        assertEquals(-50f, DemoScenarioPreset.SLOTS[0].position.y.raw)
        assertEquals(-300f, DemoScenarioPreset.SLOTS[1].position.x.raw)
        assertEquals(50f, DemoScenarioPreset.SLOTS[1].position.y.raw)

        // Enemy spread at (300, -120) / (350, 0) / (300, 120)
        assertEquals(300f, DemoScenarioPreset.SLOTS[2].position.x.raw)
        assertEquals(-120f, DemoScenarioPreset.SLOTS[2].position.y.raw)
        assertEquals(350f, DemoScenarioPreset.SLOTS[3].position.x.raw)
        assertEquals(0f, DemoScenarioPreset.SLOTS[3].position.y.raw)
        assertEquals(300f, DemoScenarioPreset.SLOTS[4].position.x.raw)
        assertEquals(120f, DemoScenarioPreset.SLOTS[4].position.y.raw)
    }

    @Test
    fun slotAiFlags_matchCanonical() {
        // Player ships: no AI (player-controlled).
        assertTrue(DemoScenarioPreset.SLOTS.filter { it.teamId == "player" }.none { it.withAI })
        // Enemy ships: AI on.
        assertTrue(DemoScenarioPreset.SLOTS.filter { it.teamId == "enemy" }.all { it.withAI })
    }

    @Test
    fun slotDrawOrders_matchCanonical() {
        // PLAYER_SHIP = 10f, ENEMY_SHIP = 20f (mirrored from DrawOrder).
        assertTrue(DemoScenarioPreset.SLOTS.filter { it.teamId == "player" }.all { it.drawOrder == 10f })
        assertTrue(DemoScenarioPreset.SLOTS.filter { it.teamId == "enemy" }.all { it.drawOrder == 20f })
    }
}
