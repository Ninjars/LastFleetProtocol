package jez.lastfleetprotocol.prototype.di

import com.pandulapeter.kubriko.audioPlayback.MusicManager
import com.pandulapeter.kubriko.audioPlayback.SoundManager
import com.pandulapeter.kubriko.manager.StateManager
import com.pandulapeter.kubriko.persistence.PersistenceManager
import com.pandulapeter.kubriko.sprites.SpriteManager
import jez.lastfleetprotocol.prototype.components.landingscreen.ui.LandingScreen
import jez.lastfleetprotocol.prototype.ui.navigation.LFNavHost
import jez.lastfleetprotocol.prototype.utils.Constants
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.annotations.Scope


@Scope
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class Singleton

@Singleton
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

    @Provides
    protected fun soundManager(): SoundManager = SoundManager.newInstance(
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )

    @Provides
    protected fun musicManager(): MusicManager = MusicManager.newInstance(
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )

    @Provides
    protected fun spriteManager(): SpriteManager = SpriteManager.newInstance(
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )
}