package jez.lastfleetprotocol.prototype.components.preferences.usecases

import jez.lastfleetprotocol.prototype.components.preferences.SetMusicEnabled
import jez.lastfleetprotocol.prototype.components.preferences.internal.UserPreferencesManager
import me.tatarka.inject.annotations.Inject

@Inject
class SetMusicEnabledUseCase(
    private val userPreferencesManager: UserPreferencesManager,
) : SetMusicEnabled {
    override fun invoke(isEnabled: Boolean) {
        userPreferencesManager.setMusicEnabled(isEnabled)
    }
}
