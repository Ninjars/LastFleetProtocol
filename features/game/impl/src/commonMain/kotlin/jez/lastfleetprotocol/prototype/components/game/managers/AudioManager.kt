package jez.lastfleetprotocol.prototype.components.game.managers

import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.audioPlayback.MusicManager
import com.pandulapeter.kubriko.audioPlayback.SoundManager
import com.pandulapeter.kubriko.manager.Manager
import com.pandulapeter.kubriko.manager.StateManager
import jez.lastfleetprotocol.prototype.components.game.audio.MusicTrack
import jez.lastfleetprotocol.prototype.components.game.audio.SoundEffect
import jez.lastfleetprotocol.prototype.di.Singleton
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import lastfleetprotocol.components.design.generated.resources.Res
import me.tatarka.inject.annotations.Inject

private data class AudioState(
    val isFocused: Boolean,
    val isMusicEnabled: Boolean,
    val shouldStopMusic: Boolean,
    val activeMusicTrack: MusicTrack?,
)

@Singleton
@Inject
class AudioManager(
    private val stateManager: StateManager,
    private val userPreferencesManager: UserPreferencesManager,
    private val musicManager: MusicManager,
    private val soundManager: SoundManager,
) : Manager() {
    private val soundsToPlay = mutableSetOf<SoundEffect>()
    private val shouldStopMusic = MutableStateFlow(false)
    private val activeMusicTrack = MutableStateFlow<MusicTrack?>(null)

    @OptIn(FlowPreview::class)
    override fun onInitialize(kubriko: Kubriko) {
        combine(
            stateManager.isFocused.debounce(100),
            userPreferencesManager.isMusicEnabled,
            shouldStopMusic,
            activeMusicTrack,
        ) { isFocused, isMusicEnabled, shouldStopMusic, track ->
            AudioState(isFocused, isMusicEnabled, shouldStopMusic, track)
        }.distinctUntilChanged()
            .onEach { state ->
                if (state.activeMusicTrack == null) return@onEach

                if (state.isMusicEnabled && state.isFocused && !state.shouldStopMusic) {
                    musicManager.play(
                        uri = Res.getUri(state.activeMusicTrack.uri),
                        shouldLoop = true,
                    )
                } else {
                    musicManager.pause(Res.getUri(state.activeMusicTrack.uri))
                }
            }.launchIn(scope)
        stateManager.isFocused
            .filter { it }
            .onEach { shouldStopMusic.update { false } }
            .launchIn(scope)
    }

    override fun onUpdate(deltaTimeInMilliseconds: Int) {
        soundsToPlay.forEach { soundManager.play(Res.getUri(it.uri)) }
        soundsToPlay.clear()
    }

    fun stopMusic() = shouldStopMusic.update { true }

    fun setMusicTrack(track: MusicTrack) = {
        shouldStopMusic.update { true }
        activeMusicTrack.update { track }
        shouldStopMusic.update { false }
    }

    fun playSoundEffect(effect: SoundEffect) {
        if (userPreferencesManager.areSoundEffectsEnabled.value) {
            soundsToPlay.add(effect)
        }
    }

    companion object {
        fun getMusicUrisToPreload() = MusicTrack.entries.map { Res.getUri(it.uri) }

        fun getSoundUrisToPreload() = SoundEffect.entries.map { Res.getUri(it.uri) }
    }
}