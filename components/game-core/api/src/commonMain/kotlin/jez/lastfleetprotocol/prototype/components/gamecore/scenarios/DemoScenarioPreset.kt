package jez.lastfleetprotocol.prototype.components.gamecore.scenarios

import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset

/**
 * Single source of truth for the canonical demo combat scenario: 2 player
 * ships vs 3 enemies. Both `GameStateManager.startDemoScene` and the scenario
 * builder's "Use demo defaults" button read from [SLOTS]; `Unit 7`'s parity
 * test pins the exact shape so drift fails CI loudly.
 *
 * `teamId` values are inlined string literals because the `Ship.TEAM_PLAYER`
 * / `TEAM_ENEMY` constants live in `:features:game:impl` (downstream of this
 * module). The parity test validates the literals match.
 *
 * `drawOrder` values mirror `DrawOrder.PLAYER_SHIP` (10f) and
 * `DrawOrder.ENEMY_SHIP` (20f) for the same reason.
 */
object DemoScenarioPreset {

    val SLOTS: List<SpawnSlotConfig> = listOf(
        SpawnSlotConfig(
            designName = "player_ship",
            position = SceneOffset((-300f).sceneUnit, (-50f).sceneUnit),
            teamId = "player",
            withAI = false,
            drawOrder = 10f,
        ),
        SpawnSlotConfig(
            designName = "player_ship",
            position = SceneOffset((-300f).sceneUnit, 50f.sceneUnit),
            teamId = "player",
            withAI = false,
            drawOrder = 10f,
        ),
        SpawnSlotConfig(
            designName = "enemy_light",
            position = SceneOffset(300f.sceneUnit, (-120f).sceneUnit),
            teamId = "enemy",
            withAI = true,
            drawOrder = 20f,
        ),
        SpawnSlotConfig(
            designName = "enemy_medium",
            position = SceneOffset(350f.sceneUnit, 0f.sceneUnit),
            teamId = "enemy",
            withAI = true,
            drawOrder = 20f,
        ),
        SpawnSlotConfig(
            designName = "enemy_heavy",
            position = SceneOffset(300f.sceneUnit, 120f.sceneUnit),
            teamId = "enemy",
            withAI = true,
            drawOrder = 20f,
        ),
    )
}
