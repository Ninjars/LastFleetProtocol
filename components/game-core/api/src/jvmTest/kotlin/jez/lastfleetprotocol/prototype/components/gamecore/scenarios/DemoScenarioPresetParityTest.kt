package jez.lastfleetprotocol.prototype.components.gamecore.scenarios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the canonical demo scenario's shape against the post-item-C values:
 * a 2v2 cruiser engagement at 3 km separation. Future edits to
 * `DemoScenarioPreset.SLOTS` fail CI loudly if they drift from this layout.
 *
 * Item C unit 9 re-baselined this test from the original 5-ship mixed-class
 * layout (2 player_ship + enemy_light/medium/heavy) to the cruiser-vs-cruiser
 * 4-ship layout, matching item C's narrowed cruiser-only scope.
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
        assertEquals(4, DemoScenarioPreset.SLOTS.size)
        assertEquals(2, DemoScenarioPreset.SLOTS.count { it.teamId == "player" })
        assertEquals(2, DemoScenarioPreset.SLOTS.count { it.teamId == "enemy" })
    }

    @Test
    fun slotDesignNames_matchCanonical() {
        val expected = listOf(
            "enemy_heavy",
            "enemy_heavy",
            "enemy_heavy",
            "enemy_heavy",
        )
        assertEquals(expected, DemoScenarioPreset.SLOTS.map { it.designName })
    }

    @Test
    fun slotPositions_matchCanonical() {
        // Player team at x = -1500m (3 km from enemy team), y = ±200m intra-team
        assertEquals(-1500f, DemoScenarioPreset.SLOTS[0].position.x.raw)
        assertEquals(-200f, DemoScenarioPreset.SLOTS[0].position.y.raw)
        assertEquals(-1500f, DemoScenarioPreset.SLOTS[1].position.x.raw)
        assertEquals(200f, DemoScenarioPreset.SLOTS[1].position.y.raw)

        // Enemy team at x = +1500m, y = ±200m intra-team
        assertEquals(1500f, DemoScenarioPreset.SLOTS[2].position.x.raw)
        assertEquals(-200f, DemoScenarioPreset.SLOTS[2].position.y.raw)
        assertEquals(1500f, DemoScenarioPreset.SLOTS[3].position.x.raw)
        assertEquals(200f, DemoScenarioPreset.SLOTS[3].position.y.raw)
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

    @Test
    fun teamSeparation_matchesCruiserEngagementBand() {
        // Item C origin D2: cruiser engagement range 3-5 km.
        // The demo's 3 km team separation lands exactly at the close end of
        // the band — cruisers should immediately be in firing range when the
        // scene starts. Drift detection: if positions ever change, the band
        // assumption needs to be re-validated.
        val playerXs = DemoScenarioPreset.SLOTS.filter { it.teamId == "player" }.map { it.position.x.raw }
        val enemyXs = DemoScenarioPreset.SLOTS.filter { it.teamId == "enemy" }.map { it.position.x.raw }
        val separation = (enemyXs.average() - playerXs.average()).toFloat()
        assertEquals(3000f, separation, "team separation should match the 3km cruiser-engagement-band target")
    }
}
