package jez.lastfleetprotocol.prototype.components.shared.usecases

import jez.lastfleetprotocol.prototype.components.game.audio.SoundEffect
import jez.lastfleetprotocol.prototype.components.game.managers.AudioManager
import jez.lastfleetprotocol.prototype.components.game.managers.UserPreferencesManager
import me.tatarka.inject.annotations.Inject

@Inject
class SetSoundEffectsEnabledUseCase(
    private val audioManager: AudioManager,
    private val userPreferencesManager: UserPreferencesManager,
) {
    operator fun invoke(isEnabled: Boolean) {
        userPreferencesManager.setSoundEffectsEnabled(isEnabled)
        audioManager.playSoundEffect(SoundEffect.ButtonToggle)
    }
}
