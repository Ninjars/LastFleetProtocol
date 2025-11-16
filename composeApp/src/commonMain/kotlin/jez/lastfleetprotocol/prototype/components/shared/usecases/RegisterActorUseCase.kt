package jez.lastfleetprotocol.prototype.components.shared.usecases

import com.pandulapeter.kubriko.actor.Actor
import com.pandulapeter.kubriko.manager.ActorManager
import me.tatarka.inject.annotations.Inject

@Inject
class RegisterActorUseCase(
    private val actorManager: ActorManager,
) {
    operator fun invoke(actor: Actor) {
        actorManager.add(actor)
    }
}