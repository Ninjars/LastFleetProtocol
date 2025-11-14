package jez.lastfleetprotocol.prototype.components.shared.usecases

import jez.lastfleetprotocol.prototype.components.game.audio.SoundEffect
import jez.lastfleetprotocol.prototype.components.game.managers.AudioManager
import jez.lastfleetprotocol.prototype.components.game.managers.UserPreferencesManager
import me.tatarka.inject.annotations.Inject

@Inject
class SetMusicEnabledUseCase(
    private val audioManager: AudioManager,
    private val userPreferencesManager: UserPreferencesManager,
) {
    operator fun invoke(isEnabled: Boolean) {
        userPreferencesManager.setMusicEnabled(isEnabled)
        audioManager.playSoundEffect(SoundEffect.ButtonToggle)
    }
}
