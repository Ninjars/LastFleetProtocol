package jez.lastfleetprotocol.prototype.components.preferences.internal

import com.pandulapeter.kubriko.manager.Manager
import com.pandulapeter.kubriko.persistence.PersistenceManager
import jez.lastfleetprotocol.prototype.components.preferences.UserPreferences
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.tatarka.inject.annotations.Inject

@Inject
class UserPreferencesManager(
    persistenceManager: PersistenceManager,
) : Manager(), UserPreferences {
    private val _areSoundEffectsEnabled = persistenceManager.boolean("areSoundEffectsEnabled", true)
    override val areSoundEffectsEnabled = _areSoundEffectsEnabled.asStateFlow()

    private val _isMusicEnabled = persistenceManager.boolean("isMusicEnabled", true)
    override val isMusicEnabled = _isMusicEnabled.asStateFlow()

    fun setSoundEffectsEnabled(enabled: Boolean) {
        _areSoundEffectsEnabled.update { enabled }
    }

    fun setMusicEnabled(enabled: Boolean) {
        _isMusicEnabled.update { enabled }
    }
}
