package jez.lastfleetprotocol.prototype.components.preferences.usecases

import jez.lastfleetprotocol.prototype.components.preferences.SetSoundEffectsEnabled
import jez.lastfleetprotocol.prototype.components.preferences.internal.UserPreferencesManager
import me.tatarka.inject.annotations.Inject

@Inject
class SetSoundEffectsEnabledUseCase(
    private val userPreferencesManager: UserPreferencesManager,
) : SetSoundEffectsEnabled {
    override fun invoke(isEnabled: Boolean) {
        userPreferencesManager.setSoundEffectsEnabled(isEnabled)
    }
}
