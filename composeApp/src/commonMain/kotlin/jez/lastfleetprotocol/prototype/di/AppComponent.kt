package jez.lastfleetprotocol.prototype.di

import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.audioPlayback.MusicManager
import com.pandulapeter.kubriko.audioPlayback.SoundManager
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.manager.StateManager
import com.pandulapeter.kubriko.manager.ViewportManager
import com.pandulapeter.kubriko.persistence.PersistenceManager
import com.pandulapeter.kubriko.sprites.SpriteManager
import jez.lastfleetprotocol.prototype.components.game.GameStateHolder
import jez.lastfleetprotocol.prototype.components.game.managers.*
import jez.lastfleetprotocol.prototype.components.landingscreen.ui.LandingScreen
import jez.lastfleetprotocol.prototype.components.splashscreen.ui.SplashScreen
import jez.lastfleetprotocol.prototype.di.DependencyName.KUBRIKO_BACKGROUND
import jez.lastfleetprotocol.prototype.di.DependencyName.KUBRIKO_GAME
import jez.lastfleetprotocol.prototype.ui.navigation.LFNavHost
import jez.lastfleetprotocol.prototype.utils.Constants
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.annotations.Qualifier
import me.tatarka.inject.annotations.Scope


@Scope
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class Singleton

@Qualifier
@Target(
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE
)
annotation class Named(val value: String)

@Singleton
@Component
abstract class AppComponent(
    private val enableLogging: Boolean,
) {
    abstract val gameStateHolder: GameStateHolder

    abstract val navHost: LFNavHost

    abstract val splashScreen: SplashScreen

    abstract val landingScreen: LandingScreen


    @Singleton
    @Provides
    protected fun stateManager(): StateManager = StateManager.newInstance(
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )

    @Singleton
    @Provides
    protected fun persistenceManager(): PersistenceManager = PersistenceManager.newInstance(
        fileName = "lastfleetprotocol_persistence",
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )

    @Singleton
    @Provides
    protected fun soundManager(): SoundManager = SoundManager.newInstance(
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )

    @Singleton
    @Provides
    protected fun musicManager(): MusicManager = MusicManager.newInstance(
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )

    @Singleton
    @Provides
    protected fun spriteManager(): SpriteManager = SpriteManager.newInstance(
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )

    @Singleton
    @Provides
    protected fun ActorManager(): ActorManager = ActorManager.newInstance(
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )

    @Singleton
    @Provides
    protected fun ViewportManager(): ViewportManager = ViewportManager.newInstance(
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )

    @Singleton
    @Provides
    protected fun backgroundKubriko(
        stateManager: StateManager,
        musicManager: MusicManager,
        spriteManager: SpriteManager,
        soundManager: SoundManager,
        loadingManager: LoadingManager,
    ): @Named(KUBRIKO_BACKGROUND) Kubriko = Kubriko.newInstance(
        stateManager,
        musicManager,
        spriteManager,
        soundManager,
        loadingManager,
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )

    @Singleton
    @Provides
    protected fun gameKubriko(
        stateManager: StateManager,
        persistenceManager: PersistenceManager,
        musicManager: MusicManager,
        spriteManager: SpriteManager,
        soundManager: SoundManager,
        loadingManager: LoadingManager,
        audioManager: AudioManager,
        actorManager: ActorManager,
        uiManager: UiManager,
        gameStateManager: GameStateManager,
        shipsManager: ShipsManager,
        viewportManager: ViewportManager,
    ): @Named(KUBRIKO_GAME) Kubriko = Kubriko.newInstance(
        stateManager,
        persistenceManager,
        musicManager,
        spriteManager,
        soundManager,
        audioManager,
        loadingManager,
        actorManager,
        uiManager,
        gameStateManager,
        shipsManager,
        viewportManager,
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )
}

object DependencyName {
    const val KUBRIKO_GAME = "game"
    const val KUBRIKO_BACKGROUND = "background"
}