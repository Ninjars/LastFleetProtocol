package jez.lastfleetprotocol.prototype.components.game.managers

import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.audioPlayback.MusicManager
import com.pandulapeter.kubriko.audioPlayback.SoundManager
import com.pandulapeter.kubriko.manager.Manager
import com.pandulapeter.kubriko.manager.StateManager
import jez.lastfleetprotocol.prototype.components.game.audio.MusicTrack
import jez.lastfleetprotocol.prototype.components.game.audio.SoundEffect
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import lastfleetprotocol.composeapp.generated.resources.Res

private data class AudioState(
    val isFocused: Boolean,
    val isMusicEnabled: Boolean,
    val shouldStopMusic: Boolean,
    val activeMusicTrack: MusicTrack?,
)

internal class AudioManager(
    private val stateManager: StateManager,
    private val userPreferencesManager: UserPreferencesManager,
) : Manager() {
    private val musicManager by manager<MusicManager>()
    private val soundManager by manager<SoundManager>()
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