package jez.lastfleetprotocol.prototype.components.game.ui

import androidx.lifecycle.viewModelScope
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.manager.ViewportManager
import jez.lastfleetprotocol.prototype.components.game.GameStateHolder
import jez.lastfleetprotocol.prototype.components.game.managers.GameStateManager
import jez.lastfleetprotocol.prototype.components.game.managers.GameStateManager.GameResult
import jez.lastfleetprotocol.prototype.components.gamecore.scenarios.PendingScenario
import jez.lastfleetprotocol.prototype.ui.common.ViewModelContract
import jez.lastfleetprotocol.prototype.utils.export.DevToolsGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

sealed interface GameIntent {
    data object OpenMenuClicked : GameIntent
    data object BackPressed : GameIntent
    data object RestartClicked : GameIntent
    data object ResumeClicked : GameIntent
    data object ExitClicked : GameIntent
}

data class GameState(
    val kubriko: Kubriko,
    val viewportManager: ViewportManager,
    val gameResult: GameResult? = null,
    val isPaused: Boolean = false,
    /**
     * Item C unit 8: gates the in-screen debug overlay. Frozen at VM init from
     * `DevToolsGate.isAvailable` — true on Desktop with `lfp.repo.root` resolved,
     * false on Android and packaged Desktop builds.
     */
    val canShowDebugOverlay: Boolean = false,
)

sealed interface GameSideEffect {
    data object NavigateBack : GameSideEffect
}

@Inject
class GameVM(
    gameStateHolder: GameStateHolder,
    private val gameStateManager: GameStateManager,
    pendingScenario: PendingScenario,
    private val viewportManager: ViewportManager,
    devToolsGate: DevToolsGate,
) : ViewModelContract<GameIntent, GameState, GameSideEffect>() {

    /**
     * Consumed at construction so the production Play-from-landing path that
     * follows a scenario-builder launch never replays stale slots. Read-and-
     * cleared in one step; persistence of "what is currently running" lives
     * in `GameStateManager.lastLaunched`, not here.
     */
    private val initialSlots = pendingScenario.consume()

    /**
     * Item C unit 8: gate snapshot taken once at construction. The gate's
     * underlying value (system property + filesystem) doesn't change at
     * runtime, so a one-shot read is sufficient — mirrors LandingVM's
     * `canShowDevTools` pattern from item B.
     */
    private val canShowDebugOverlay: Boolean = devToolsGate.isAvailable

    private val _isPaused = MutableStateFlow(false)

    override val state: StateFlow<GameState> = combine(
        gameStateManager.gameResult,
        _isPaused,
    ) { result, paused ->
        GameState(
            kubriko = gameStateHolder.gameKubriko,
            viewportManager = viewportManager,
            gameResult = result,
            isPaused = paused && result == null, // Don't show pause when game is over
            canShowDebugOverlay = canShowDebugOverlay,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        GameState(
            kubriko = gameStateHolder.gameKubriko,
            viewportManager = viewportManager,
            canShowDebugOverlay = canShowDebugOverlay,
        ),
    )

    override fun accept(intent: GameIntent) {
        when (intent) {
            GameIntent.OpenMenuClicked -> pause()
            GameIntent.BackPressed -> {
                if (_isPaused.value) {
                    exit()
                } else {
                    pause()
                }
            }

            GameIntent.ResumeClicked -> resume()
            GameIntent.RestartClicked -> {
                _isPaused.value = false
                viewModelScope.launch { gameStateManager.restartScene() }
            }

            GameIntent.ExitClicked -> exit()
        }
    }

    private fun pause() {
        _isPaused.value = true
        gameStateManager.setPaused(true)
    }

    private fun resume() {
        _isPaused.value = false
        gameStateManager.setPaused(false)
    }

    private fun exit() {
        _isPaused.value = false
        viewModelScope.launch {
            gameStateManager.clearScene()
            sendSideEffect(GameSideEffect.NavigateBack)
        }
    }

    fun onWindowFocusLost() {
        // Only auto-pause if the game is actively running (not already paused or finished)
        if (!_isPaused.value && state.value.gameResult == null) {
            pause()
        }
    }

    init {
        viewModelScope.launch {
            initialSlots
                ?.let { gameStateManager.startScene(it) }
                ?: gameStateManager.startDemoScene()
        }
    }
}
