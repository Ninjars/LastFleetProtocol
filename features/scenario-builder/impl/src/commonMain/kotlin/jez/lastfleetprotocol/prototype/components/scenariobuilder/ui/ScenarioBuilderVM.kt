package jez.lastfleetprotocol.prototype.components.scenariobuilder.ui

import androidx.lifecycle.viewModelScope
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.scenarios.DemoScenarioPreset
import jez.lastfleetprotocol.prototype.components.gamecore.scenarios.PendingScenario
import jez.lastfleetprotocol.prototype.components.gamecore.scenarios.Scenario
import jez.lastfleetprotocol.prototype.components.gamecore.scenarios.ScenarioRepository
import jez.lastfleetprotocol.prototype.components.gamecore.scenarios.SpawnSlotConfig
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.DefaultShipDesignLoader
import jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.entities.ScenarioBuilderIntent
import jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.entities.ScenarioBuilderSideEffect
import jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.entities.ScenarioBuilderState
import jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.entities.ScenarioTeam
import jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.entities.SlotEntry
import jez.lastfleetprotocol.prototype.ui.common.ViewModelContract
import jez.lastfleetprotocol.prototype.utils.sanitizeFilenameStem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

/**
 * MVI core of the scenario builder. Holds the in-progress scenario as
 * [SlotEntry] rows; intents drive edits and persistence; side effects route
 * navigation + toasts to the screen.
 *
 * Library load is async (suspend `loadAll()`); `state.libraryReady` flips
 * true once the bundle has been read, at which point broken-slot detection
 * (a derived property on state) becomes meaningful. This avoids the race
 * where a `LoadScenario` arriving before the library resolves would
 * mis-flag every slot as broken.
 *
 * Launch validation is proactive (R9): the Launch button is bound to
 * `state.canLaunch`, so `LaunchClicked` only fires when both teams have
 * at least one non-broken slot.
 */
@Inject
class ScenarioBuilderVM(
    private val scenarioRepository: ScenarioRepository,
    private val shipDesignLoader: DefaultShipDesignLoader,
    private val pendingScenario: PendingScenario,
) : ViewModelContract<ScenarioBuilderIntent, ScenarioBuilderState, ScenarioBuilderSideEffect>() {

    private val _state = MutableStateFlow(ScenarioBuilderState())
    override val state: StateFlow<ScenarioBuilderState> = _state

    private var nextSlotId = 0

    init {
        val saved = scenarioRepository.listAll()
        _state.update {
            it.copy(
                designName = "Scenario ${saved.size + 1}",
                savedScenarios = saved,
                canShowLoadDialog = saved.isNotEmpty(),
            )
        }
        viewModelScope.launch {
            val library = try {
                shipDesignLoader.loadAll().keys.toList()
            } catch (e: Exception) {
                emptyList()
            }
            _state.update {
                it.copy(libraryShipNames = library, libraryReady = true)
            }
        }
    }

    override fun accept(intent: ScenarioBuilderIntent) {
        when (intent) {
            is ScenarioBuilderIntent.AddSlot -> handleAddSlot(intent.team)
            is ScenarioBuilderIntent.RemoveSlot -> handleRemoveSlot(intent.slotId)
            is ScenarioBuilderIntent.UpdateSlotShip ->
                updateSlot(intent.slotId) { it.copy(designName = intent.designName) }
            is ScenarioBuilderIntent.UpdateSlotPosition ->
                updateSlot(intent.slotId) { it.copy(x = intent.x, y = intent.y) }
            is ScenarioBuilderIntent.ToggleSlotAi ->
                updateSlot(intent.slotId) { it.copy(withAI = !it.withAI) }
            is ScenarioBuilderIntent.UseDemoDefaults -> handleUseDemoDefaults(intent.team)
            is ScenarioBuilderIntent.RenameScenario -> _state.update { it.copy(designName = intent.name) }
            ScenarioBuilderIntent.SaveClicked -> handleSave()
            ScenarioBuilderIntent.LoadDialogOpenClicked -> _state.update { it.copy(showLoadDialog = true) }
            ScenarioBuilderIntent.LoadDialogDismissed -> _state.update { it.copy(showLoadDialog = false) }
            is ScenarioBuilderIntent.LoadScenario -> handleLoadScenario(intent.name)
            ScenarioBuilderIntent.LaunchClicked -> handleLaunchClicked()
            ScenarioBuilderIntent.BackClicked -> emitSideEffect(ScenarioBuilderSideEffect.NavigateBack)
            ScenarioBuilderIntent.ConfirmOverwrite -> handleConfirmOverwrite()
            ScenarioBuilderIntent.CancelOverwrite -> _state.update { it.copy(showOverwriteConfirm = null) }
        }
    }

    private fun handleAddSlot(team: ScenarioTeam) {
        val newSlot = SlotEntry(
            id = nextId(),
            team = team,
            designName = _state.value.libraryShipNames.firstOrNull().orEmpty(),
            x = 0f,
            y = 0f,
            withAI = team == ScenarioTeam.ENEMY,
        )
        _state.update { it.copy(slots = it.slots + newSlot) }
    }

    private fun handleRemoveSlot(slotId: String) {
        _state.update { it.copy(slots = it.slots.filterNot { slot -> slot.id == slotId }) }
    }

    private fun updateSlot(slotId: String, transform: (SlotEntry) -> SlotEntry) {
        _state.update { state ->
            state.copy(
                slots = state.slots.map { if (it.id == slotId) transform(it) else it },
            )
        }
    }

    private fun handleUseDemoDefaults(team: ScenarioTeam) {
        val demoForTeam = DemoScenarioPreset.SLOTS
            .filter { it.teamId == team.id }
            .map { it.toSlotEntry(team, nextId()) }
        _state.update { state ->
            val others = state.slots.filterNot { it.team == team }
            state.copy(slots = others + demoForTeam)
        }
    }

    private fun handleSave() {
        val state = _state.value
        val name = state.designName.trim()
        if (name.isEmpty()) {
            emitSideEffect(ScenarioBuilderSideEffect.ShowToast("Name cannot be empty"))
            return
        }
        val pending = state.toScenario(name)
        val sanitised = sanitizeFilenameStem(name)
        if (state.savedScenarios.contains(sanitised)) {
            _state.update { it.copy(showOverwriteConfirm = pending) }
        } else {
            persistScenario(pending)
        }
    }

    private fun handleConfirmOverwrite() {
        val pending = _state.value.showOverwriteConfirm ?: return
        _state.update { it.copy(showOverwriteConfirm = null) }
        persistScenario(pending)
    }

    private fun persistScenario(scenario: Scenario) {
        scenarioRepository.save(scenario)
        val saved = scenarioRepository.listAll()
        _state.update { it.copy(savedScenarios = saved, canShowLoadDialog = saved.isNotEmpty()) }
        emitSideEffect(ScenarioBuilderSideEffect.ShowToast("Saved scenario \"${scenario.name}\""))
    }

    private fun handleLoadScenario(name: String) {
        val loaded = scenarioRepository.load(name)
        if (loaded == null) {
            emitSideEffect(ScenarioBuilderSideEffect.ShowToast("Failed to load \"$name\""))
            return
        }
        _state.update { state ->
            state.copy(
                designName = loaded.name,
                slots = loaded.slots.map { it.toSlotEntry(it.toScenarioTeam(), nextId()) },
                showLoadDialog = false,
            )
        }
    }

    private fun handleLaunchClicked() {
        val state = _state.value
        if (!state.canLaunch) return
        val launchSlots = state.slots
            .filterNot { it.id in state.brokenSlotIds }
            .map { it.toSpawnSlotConfig() }
        pendingScenario.slots = launchSlots
        emitSideEffect(ScenarioBuilderSideEffect.LaunchScenario)
    }

    private fun emitSideEffect(effect: ScenarioBuilderSideEffect) {
        viewModelScope.launch { sendSideEffect(effect) }
    }

    private fun nextId(): String = "slot-${nextSlotId++}"

    private fun ScenarioBuilderState.toScenario(name: String): Scenario =
        Scenario(name = name, slots = slots.map { it.toSpawnSlotConfig() })

    private fun SlotEntry.toSpawnSlotConfig(): SpawnSlotConfig =
        SpawnSlotConfig(
            designName = designName,
            position = SceneOffset(x.sceneUnit, y.sceneUnit),
            teamId = team.id,
            withAI = withAI,
            drawOrder = if (team == ScenarioTeam.PLAYER) PLAYER_DRAW_ORDER else ENEMY_DRAW_ORDER,
        )

    private fun SpawnSlotConfig.toSlotEntry(team: ScenarioTeam, id: String): SlotEntry =
        SlotEntry(
            id = id,
            team = team,
            designName = designName,
            x = position.x.raw,
            y = position.y.raw,
            withAI = withAI,
        )

    private fun SpawnSlotConfig.toScenarioTeam(): ScenarioTeam =
        if (teamId == ScenarioTeam.ENEMY.id) ScenarioTeam.ENEMY else ScenarioTeam.PLAYER

    private companion object {
        // Mirror DrawOrder.PLAYER_SHIP / ENEMY_SHIP — pinned by Unit 7's parity test.
        const val PLAYER_DRAW_ORDER = 10f
        const val ENEMY_DRAW_ORDER = 20f
    }
}
