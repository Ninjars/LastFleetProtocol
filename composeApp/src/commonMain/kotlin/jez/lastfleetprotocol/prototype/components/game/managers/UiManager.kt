package jez.lastfleetprotocol.prototype.components.game.managers

import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.traits.Unique
import com.pandulapeter.kubriko.manager.Manager
import com.pandulapeter.kubriko.manager.StateManager
import jez.lastfleetprotocol.prototype.components.shared.usecases.PlaySoundEffectUseCase
import jez.lastfleetprotocol.prototype.components.shared.usecases.RegisterActorUseCase
import jez.lastfleetprotocol.prototype.components.shared.usecases.SetGamePausedUseCase
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.tatarka.inject.annotations.Inject

@Inject
class UiManager(
    private val stateManager: StateManager,
    private val registerActor: RegisterActorUseCase,
    private val playSoundEffect: PlaySoundEffectUseCase,
    private val setGamePaused: SetGamePausedUseCase,
) : Manager(), Unique {

    override fun onInitialize(kubriko: Kubriko) {
        registerActor(this)

        // Pause the game automatically when focus is lost.
        stateManager.isFocused
            .filterNot { it }
            .onEach { setGamePaused(true) }
            .launchIn(scope)
    }

}