package jez.lastfleetprotocol.prototype.components.gamecore.scenarios

import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset

/**
 * Single source of truth for the canonical demo combat scenario: a 2v2
 * cruiser-class engagement at 3 km separation. Both
 * `GameStateManager.startDemoScene` and the scenario builder's "Use demo
 * defaults" button read from [SLOTS]; `DemoScenarioPresetParityTest` pins
 * the exact shape so drift fails CI loudly.
 *
 * Item C unit 9 rebuilt this from the original 2-player + 3-enemy mixed-class
 * layout into the cruiser-vs-cruiser engagement that matches the
 * cruiser-only narrowed scope of item C. At 1 SU = 1 m, ±1500 SU on X
 * places the teams 3 km apart — within the 3-5 km cruiser engagement band
 * from origin D2. The ±200 SU intra-team offset on Y produces a 400 m
 * spacing between the two cruisers per side.
 *
 * `teamId` values are inlined string literals because `Ship.TEAM_PLAYER` /
 * `TEAM_ENEMY` constants live in `:features:game:impl` (downstream of this
 * module). The parity test validates the literals match.
 *
 * `drawOrder` values mirror `DrawOrder.PLAYER_SHIP` (10f) and
 * `DrawOrder.ENEMY_SHIP` (20f) for the same reason.
 */
object DemoScenarioPreset {

    val SLOTS: List<SpawnSlotConfig> = listOf(
        SpawnSlotConfig(
            designName = "enemy_heavy",
            position = SceneOffset((-1500f).sceneUnit, (-200f).sceneUnit),
            teamId = "player",
            withAI = false,
            drawOrder = 10f,
        ),
        SpawnSlotConfig(
            designName = "enemy_heavy",
            position = SceneOffset((-1500f).sceneUnit, 200f.sceneUnit),
            teamId = "player",
            withAI = false,
            drawOrder = 10f,
        ),
        SpawnSlotConfig(
            designName = "enemy_heavy",
            position = SceneOffset(1500f.sceneUnit, (-200f).sceneUnit),
            teamId = "enemy",
            withAI = true,
            drawOrder = 20f,
        ),
        SpawnSlotConfig(
            designName = "enemy_heavy",
            position = SceneOffset(1500f.sceneUnit, 200f.sceneUnit),
            teamId = "enemy",
            withAI = true,
            drawOrder = 20f,
        ),
    )
}
