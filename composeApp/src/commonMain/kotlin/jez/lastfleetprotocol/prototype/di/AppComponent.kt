package jez.lastfleetprotocol.prototype.di

import com.pandulapeter.kubriko.manager.StateManager
import com.pandulapeter.kubriko.persistence.PersistenceManager
import jez.lastfleetprotocol.prototype.components.landingscreen.ui.LandingScreen
import jez.lastfleetprotocol.prototype.ui.navigation.LFNavHost
import jez.lastfleetprotocol.prototype.utils.Constants
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides

@Component
abstract class AppComponent(
    private val enableLogging: Boolean,
) {
    abstract val navHost: LFNavHost

    abstract val landingScreen: LandingScreen

    @Provides
    protected fun stateManager(): StateManager = StateManager.newInstance(
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )

    @Provides
    protected fun persistenceManager(): PersistenceManager = PersistenceManager.newInstance(
        fileName = "lastfleetprotocol_persistence",
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )
}