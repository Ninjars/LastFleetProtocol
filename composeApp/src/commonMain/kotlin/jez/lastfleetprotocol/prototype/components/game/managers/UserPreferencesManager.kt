package jez.lastfleetprotocol.prototype.components.game.managers

import com.pandulapeter.kubriko.manager.Manager
import com.pandulapeter.kubriko.persistence.PersistenceManager
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class UserPreferencesManager(
    persistenceManager: PersistenceManager,
) : Manager() {
    private val _areSoundEffectsEnabled = persistenceManager.boolean("areSoundEffectsEnabled", true)
    val areSoundEffectsEnabled = _areSoundEffectsEnabled.asStateFlow()

    private val _isMusicEnabled = persistenceManager.boolean("isMusicEnabled", true)
    val isMusicEnabled = _isMusicEnabled.asStateFlow()

    fun setSoundEffectsEnabled(enabled: Boolean) {
        _areSoundEffectsEnabled.update { enabled }
    }

    fun setMusicEnabled(enabled: Boolean) {
        _isMusicEnabled.update { enabled }
    }
}
