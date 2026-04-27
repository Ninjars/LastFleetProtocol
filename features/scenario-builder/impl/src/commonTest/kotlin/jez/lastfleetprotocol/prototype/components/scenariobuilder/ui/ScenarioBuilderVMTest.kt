package jez.lastfleetprotocol.prototype.components.scenariobuilder.ui

import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.scenarios.DemoScenarioPreset
import jez.lastfleetprotocol.prototype.components.gamecore.scenarios.PendingScenario
import jez.lastfleetprotocol.prototype.components.gamecore.scenarios.Scenario
import jez.lastfleetprotocol.prototype.components.gamecore.scenarios.ScenarioRepository
import jez.lastfleetprotocol.prototype.components.gamecore.scenarios.SpawnSlotConfig
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.DefaultShipDesignLoader
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ShipDesign
import jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.entities.ScenarioBuilderIntent
import jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.entities.ScenarioBuilderSideEffect
import jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.entities.ScenarioTeam
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScenarioBuilderVMTest {

    private lateinit var collectorScope: CoroutineScope
    private val captured = mutableListOf<ScenarioBuilderSideEffect>()
    private var collectorJob: Job? = null

    @BeforeTest
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        collectorScope = CoroutineScope(Dispatchers.Unconfined)
        captured.clear()
    }

    @AfterTest
    fun clearMainDispatcher() {
        collectorJob?.cancel()
        Dispatchers.resetMain()
    }

    private fun startCollecting(vm: ScenarioBuilderVM) {
        collectorJob = collectorScope.launch(start = CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.toList(captured)
        }
    }

    // --- Fakes ---

    private class FakeScenarioRepository(
        private val store: MutableMap<String, Scenario> = mutableMapOf(),
    ) : ScenarioRepository {
        var saveCount = 0
        override fun save(scenario: Scenario) {
            store[scenario.name] = scenario
            saveCount++
        }
        override fun load(name: String): Scenario? = store[name]
        override fun listAll(): List<String> = store.keys.toList()
        override fun delete(name: String) { store.remove(name) }
    }

    /**
     * Fake loader that returns a fixed library map. Subclasses
     * `DefaultShipDesignLoader` directly so the production constructor
     * signature is reused; the empty `ShipDesign` instances are sufficient
     * because the VM only inspects the map's keys.
     */
    private class FakeShipDesignLoader(
        private val library: Map<String, ShipDesign>,
    ) : DefaultShipDesignLoader() {
        override suspend fun loadAll(): Map<String, ShipDesign> = library
    }

    /** Loader that suspends until [resolve] is called — for the library-update race test. */
    private class DeferredShipDesignLoader : DefaultShipDesignLoader() {
        private val gate = CompletableDeferred<Map<String, ShipDesign>>()
        override suspend fun loadAll(): Map<String, ShipDesign> = gate.await()
        fun resolve(library: Map<String, ShipDesign>) { gate.complete(library) }
    }

    // --- Fixtures ---

    private val sampleLibrary = mapOf(
        "player_ship" to ShipDesign(name = "player_ship"),
        "enemy_light" to ShipDesign(name = "enemy_light"),
        "enemy_medium" to ShipDesign(name = "enemy_medium"),
        "enemy_heavy" to ShipDesign(name = "enemy_heavy"),
    )

    private fun makeVM(
        repo: ScenarioRepository = FakeScenarioRepository(),
        loader: DefaultShipDesignLoader = FakeShipDesignLoader(sampleLibrary),
        pending: PendingScenario = PendingScenario(),
    ) = ScenarioBuilderVM(
        scenarioRepository = repo,
        shipDesignLoader = loader,
        pendingScenario = pending,
    )

    // --- AddSlot / RemoveSlot ---

    @Test
    fun addSlot_appendsNewSlotForTheGivenTeam() {
        val vm = makeVM()
        vm.accept(ScenarioBuilderIntent.AddSlot(ScenarioTeam.PLAYER))
        vm.accept(ScenarioBuilderIntent.AddSlot(ScenarioTeam.PLAYER))
        vm.accept(ScenarioBuilderIntent.AddSlot(ScenarioTeam.ENEMY))

        val slots = vm.state.value.slots
        assertEquals(3, slots.size)
        assertEquals(2, slots.count { it.team == ScenarioTeam.PLAYER })
        assertEquals(1, slots.count { it.team == ScenarioTeam.ENEMY })
    }

    @Test
    fun addSlot_seedsDesignNameFromLibrary_andDefaultsAi() {
        val vm = makeVM()
        vm.accept(ScenarioBuilderIntent.AddSlot(ScenarioTeam.PLAYER))
        val player = vm.state.value.slots.single()
        assertContains(sampleLibrary.keys, player.designName)
        assertFalse(player.withAI, "player slots default to no AI")

        vm.accept(ScenarioBuilderIntent.AddSlot(ScenarioTeam.ENEMY))
        val enemy = vm.state.value.slots.last()
        assertTrue(enemy.withAI, "enemy slots default to AI on")
    }

    @Test
    fun removeSlot_dropsTheMatchingSlot() {
        val vm = makeVM()
        vm.accept(ScenarioBuilderIntent.AddSlot(ScenarioTeam.PLAYER))
        vm.accept(ScenarioBuilderIntent.AddSlot(ScenarioTeam.ENEMY))
        val keepId = vm.state.value.slots.first().id
        val removeId = vm.state.value.slots.last().id

        vm.accept(ScenarioBuilderIntent.RemoveSlot(removeId))

        assertEquals(listOf(keepId), vm.state.value.slots.map { it.id })
    }

    // --- UpdateSlot* ---

    @Test
    fun updateSlotShip_andUpdateSlotPosition_mutateOnlyTargetFields() {
        val vm = makeVM()
        vm.accept(ScenarioBuilderIntent.AddSlot(ScenarioTeam.ENEMY))
        val id = vm.state.value.slots.single().id
        val originalAi = vm.state.value.slots.single().withAI

        vm.accept(ScenarioBuilderIntent.UpdateSlotShip(id, "enemy_heavy"))
        vm.accept(ScenarioBuilderIntent.UpdateSlotPosition(id, 200f, 50f))

        val slot = vm.state.value.slots.single()
        assertEquals("enemy_heavy", slot.designName)
        assertEquals(200f, slot.x)
        assertEquals(50f, slot.y)
        assertEquals(originalAi, slot.withAI, "withAI must not be touched by ship/position updates")
    }

    @Test
    fun toggleSlotAi_flipsTheFlag() {
        val vm = makeVM()
        vm.accept(ScenarioBuilderIntent.AddSlot(ScenarioTeam.PLAYER))
        val id = vm.state.value.slots.single().id

        vm.accept(ScenarioBuilderIntent.ToggleSlotAi(id))
        assertTrue(vm.state.value.slots.single().withAI)
        vm.accept(ScenarioBuilderIntent.ToggleSlotAi(id))
        assertFalse(vm.state.value.slots.single().withAI)
    }

    // --- UseDemoDefaults ---

    @Test
    fun useDemoDefaults_perTeam_addsExactlyThePresetSubsetForThatTeam() {
        val vm = makeVM()
        vm.accept(ScenarioBuilderIntent.UseDemoDefaults(ScenarioTeam.PLAYER))
        val playerCountInPreset = DemoScenarioPreset.SLOTS.count { it.teamId == ScenarioTeam.PLAYER.id }
        assertEquals(playerCountInPreset, vm.state.value.slots.count { it.team == ScenarioTeam.PLAYER })

        vm.accept(ScenarioBuilderIntent.UseDemoDefaults(ScenarioTeam.ENEMY))
        val enemyCountInPreset = DemoScenarioPreset.SLOTS.count { it.teamId == ScenarioTeam.ENEMY.id }
        assertEquals(enemyCountInPreset, vm.state.value.slots.count { it.team == ScenarioTeam.ENEMY })
    }

    @Test
    fun useDemoDefaults_replacesOnlyTheTargetTeamsExistingSlots() {
        val vm = makeVM()
        vm.accept(ScenarioBuilderIntent.AddSlot(ScenarioTeam.PLAYER))
        vm.accept(ScenarioBuilderIntent.AddSlot(ScenarioTeam.ENEMY))
        val initialEnemy = vm.state.value.slots.single { it.team == ScenarioTeam.ENEMY }

        vm.accept(ScenarioBuilderIntent.UseDemoDefaults(ScenarioTeam.PLAYER))

        // The pre-existing enemy slot survives the player-team replacement.
        assertContains(vm.state.value.slots.map { it.id }, initialEnemy.id)
    }

    // --- SaveClicked + overwrite confirm ---

    @Test
    fun saveClicked_noCollision_persistsAndEmitsToast() {
        val repo = FakeScenarioRepository()
        val vm = makeVM(repo = repo)
        startCollecting(vm)
        vm.accept(ScenarioBuilderIntent.RenameScenario("two-vs-two"))

        vm.accept(ScenarioBuilderIntent.SaveClicked)

        assertEquals(1, repo.saveCount)
        val toast = captured.filterIsInstance<ScenarioBuilderSideEffect.ShowToast>().single()
        assertContains(toast.text, "Saved")
        assertNull(vm.state.value.showOverwriteConfirm)
    }

    @Test
    fun saveClicked_collision_showsConfirmDialog_doesNotPersistYet() {
        val repo = FakeScenarioRepository()
        repo.save(Scenario(name = "two-vs-two"))
        val vm = makeVM(repo = repo)
        startCollecting(vm)
        vm.accept(ScenarioBuilderIntent.RenameScenario("two-vs-two"))

        val saveCountBefore = repo.saveCount
        vm.accept(ScenarioBuilderIntent.SaveClicked)

        assertEquals(saveCountBefore, repo.saveCount, "no save fires until ConfirmOverwrite")
        assertNotNull(vm.state.value.showOverwriteConfirm)
    }

    @Test
    fun confirmOverwrite_persistsAndClearsDialog() {
        val repo = FakeScenarioRepository()
        repo.save(Scenario(name = "two-vs-two"))
        val vm = makeVM(repo = repo)
        startCollecting(vm)
        vm.accept(ScenarioBuilderIntent.RenameScenario("two-vs-two"))
        vm.accept(ScenarioBuilderIntent.SaveClicked)

        val saveCountBefore = repo.saveCount
        vm.accept(ScenarioBuilderIntent.ConfirmOverwrite)

        assertEquals(saveCountBefore + 1, repo.saveCount)
        assertNull(vm.state.value.showOverwriteConfirm)
    }

    @Test
    fun cancelOverwrite_doesNotPersist_andClearsDialog() {
        val repo = FakeScenarioRepository()
        repo.save(Scenario(name = "two-vs-two"))
        val vm = makeVM(repo = repo)
        vm.accept(ScenarioBuilderIntent.RenameScenario("two-vs-two"))
        vm.accept(ScenarioBuilderIntent.SaveClicked)

        val saveCountBefore = repo.saveCount
        vm.accept(ScenarioBuilderIntent.CancelOverwrite)

        assertEquals(saveCountBefore, repo.saveCount)
        assertNull(vm.state.value.showOverwriteConfirm)
    }

    // --- LoadScenario ---

    @Test
    fun loadScenario_populatesStateWithLoadedSlots_clearsBrokenWhenAllValid() {
        val repo = FakeScenarioRepository()
        repo.save(
            Scenario(
                name = "all-valid",
                slots = listOf(
                    SpawnSlotConfig("player_ship", SceneOffset.Zero, "player", false, 10f),
                    SpawnSlotConfig("enemy_heavy", SceneOffset.Zero, "enemy", true, 20f),
                ),
            ),
        )
        val vm = makeVM(repo = repo)

        vm.accept(ScenarioBuilderIntent.LoadScenario("all-valid"))

        assertEquals("all-valid", vm.state.value.designName)
        assertEquals(2, vm.state.value.slots.size)
        assertTrue(vm.state.value.brokenSlotIds.isEmpty())
    }

    @Test
    fun loadScenario_flagsBrokenSlotsForUnknownDesignNames() {
        val repo = FakeScenarioRepository()
        repo.save(
            Scenario(
                name = "with-stale",
                slots = listOf(
                    SpawnSlotConfig("player_ship", SceneOffset.Zero, "player", false, 10f),
                    SpawnSlotConfig("deleted_ship", SceneOffset.Zero, "enemy", true, 20f),
                ),
            ),
        )
        val vm = makeVM(repo = repo)

        vm.accept(ScenarioBuilderIntent.LoadScenario("with-stale"))

        assertEquals(1, vm.state.value.brokenSlotIds.size)
        // The broken slot still appears in state.slots (so the user can fix it).
        assertEquals(2, vm.state.value.slots.size)
    }

    @Test
    fun loadScenario_missing_emitsToast_leavesStateUnchanged() {
        val repo = FakeScenarioRepository()
        val vm = makeVM(repo = repo)
        startCollecting(vm)
        vm.accept(ScenarioBuilderIntent.AddSlot(ScenarioTeam.PLAYER))
        val priorSlots = vm.state.value.slots

        vm.accept(ScenarioBuilderIntent.LoadScenario("does-not-exist"))

        assertEquals(priorSlots, vm.state.value.slots)
        val toast = captured.filterIsInstance<ScenarioBuilderSideEffect.ShowToast>().single()
        assertContains(toast.text, "Failed to load")
    }

    // --- Library-update race ---

    @Test
    fun loadScenario_beforeLibraryResolves_recomputesBrokenWhenLibraryArrives() {
        val repo = FakeScenarioRepository()
        repo.save(
            Scenario(
                name = "race",
                slots = listOf(
                    SpawnSlotConfig("player_ship", SceneOffset.Zero, "player", false, 10f),
                ),
            ),
        )
        val deferredLoader = DeferredShipDesignLoader()
        val vm = makeVM(repo = repo, loader = deferredLoader)
        // Library hasn't resolved yet — libraryReady = false, so brokenSlotIds is
        // forced to empty regardless of slots vs library.
        assertFalse(vm.state.value.libraryReady)

        vm.accept(ScenarioBuilderIntent.LoadScenario("race"))
        // Even though libraryShipNames is empty, the slots are NOT shown as broken
        // during the load window.
        assertTrue(vm.state.value.brokenSlotIds.isEmpty())

        // Library resolves with the design present → no slots become broken.
        deferredLoader.resolve(sampleLibrary)
        assertTrue(vm.state.value.libraryReady)
        assertTrue(vm.state.value.brokenSlotIds.isEmpty())
    }

    // --- canLaunch (proactive validation, R9) ---

    @Test
    fun canLaunch_falseWhenNoSlots() {
        val vm = makeVM()
        assertFalse(vm.state.value.canLaunch)
    }

    @Test
    fun canLaunch_falseWhenOnlyOneTeamHasSlots() {
        val vm = makeVM()
        vm.accept(ScenarioBuilderIntent.AddSlot(ScenarioTeam.PLAYER))
        assertFalse(vm.state.value.canLaunch, "no enemy slots → cannot launch")
    }

    @Test
    fun canLaunch_falseWhenOneTeamSlotsAreAllBroken() {
        val repo = FakeScenarioRepository()
        repo.save(
            Scenario(
                name = "broken-player",
                slots = listOf(
                    SpawnSlotConfig("nonexistent", SceneOffset.Zero, "player", false, 10f),
                    SpawnSlotConfig("enemy_heavy", SceneOffset.Zero, "enemy", true, 20f),
                ),
            ),
        )
        val vm = makeVM(repo = repo)

        vm.accept(ScenarioBuilderIntent.LoadScenario("broken-player"))

        assertFalse(vm.state.value.canLaunch, "all-broken player team blocks launch")
    }

    @Test
    fun canLaunch_trueWithOneNonBrokenSlotPerTeam() {
        val vm = makeVM()
        vm.accept(ScenarioBuilderIntent.AddSlot(ScenarioTeam.PLAYER))
        vm.accept(ScenarioBuilderIntent.AddSlot(ScenarioTeam.ENEMY))
        assertTrue(vm.state.value.canLaunch)
    }

    @Test
    fun canLaunch_falseWhenLibraryNotReady() {
        val deferredLoader = DeferredShipDesignLoader()
        val vm = makeVM(loader = deferredLoader)
        // Manually accumulate slots (won't have valid designNames since library
        // is empty during the load window — but designName-validity is masked
        // because libraryReady=false suppresses brokenSlotIds. canLaunch still
        // requires libraryReady, so this stays false.)
        vm.accept(ScenarioBuilderIntent.AddSlot(ScenarioTeam.PLAYER))
        vm.accept(ScenarioBuilderIntent.AddSlot(ScenarioTeam.ENEMY))
        assertFalse(vm.state.value.canLaunch, "libraryReady = false blocks launch")
    }

    // --- LaunchClicked ---

    @Test
    fun launchClicked_writesNonBrokenSlotsToPendingAndEmitsLaunchScenario() {
        val pending = PendingScenario()
        val repo = FakeScenarioRepository()
        repo.save(
            Scenario(
                name = "filter-broken",
                slots = listOf(
                    SpawnSlotConfig("player_ship", SceneOffset(10f.sceneUnit, 0f.sceneUnit), "player", false, 10f),
                    SpawnSlotConfig("nonexistent", SceneOffset.Zero, "player", false, 10f),
                    SpawnSlotConfig("enemy_heavy", SceneOffset(20f.sceneUnit, 5f.sceneUnit), "enemy", true, 20f),
                ),
            ),
        )
        val vm = makeVM(repo = repo, pending = pending)
        startCollecting(vm)

        vm.accept(ScenarioBuilderIntent.LoadScenario("filter-broken"))
        vm.accept(ScenarioBuilderIntent.LaunchClicked)

        val captured = pending.slots
        assertNotNull(captured)
        assertEquals(2, captured.size, "broken slot must be filtered out")
        assertEquals(setOf("player_ship", "enemy_heavy"), captured.map { it.designName }.toSet())
        assertContains(this.captured, ScenarioBuilderSideEffect.LaunchScenario)
    }

    @Test
    fun launchClicked_doesNothingWhenCanLaunchIsFalse() {
        val pending = PendingScenario()
        val vm = makeVM(pending = pending)
        startCollecting(vm)
        // No slots → canLaunch=false
        assertFalse(vm.state.value.canLaunch)

        vm.accept(ScenarioBuilderIntent.LaunchClicked)

        assertNull(pending.slots, "pending must not be touched if launch is invalid")
        assertEquals(0, captured.count { it is ScenarioBuilderSideEffect.LaunchScenario })
    }

    // --- BackClicked ---

    @Test
    fun backClicked_emitsNavigateBack() {
        val vm = makeVM()
        startCollecting(vm)

        vm.accept(ScenarioBuilderIntent.BackClicked)

        assertContains(captured, ScenarioBuilderSideEffect.NavigateBack)
    }

    // --- Init ---

    @Test
    fun init_seedsLibraryShipNamesFromLoader() {
        val vm = makeVM()
        assertEquals(sampleLibrary.keys.toList(), vm.state.value.libraryShipNames)
        assertTrue(vm.state.value.libraryReady)
    }

    @Test
    fun init_seedsSavedScenariosAndCanShowLoadDialog() {
        val repo = FakeScenarioRepository()
        repo.save(Scenario(name = "saved-1"))
        val vm = makeVM(repo = repo)

        assertContains(vm.state.value.savedScenarios, "saved-1")
        assertTrue(vm.state.value.canShowLoadDialog)
    }

    @Test
    fun init_doesNotConsumePendingScenario() {
        // The scenario builder is the producer; consume-on-read happens in GameVM.
        // Constructing the VM must NOT clear pending (would defeat the point).
        val pending = PendingScenario().apply {
            slots = listOf(SpawnSlotConfig("player_ship", SceneOffset.Zero, "player", false, 10f))
        }
        makeVM(pending = pending)
        assertNotNull(pending.slots, "scenario builder must not clear pending — that's GameVM's job")
    }

    @Test
    fun loaderFailure_keepsLibraryEmpty_butDoesNotCrash() {
        val throwingLoader = object : DefaultShipDesignLoader() {
            override suspend fun loadAll(): Map<String, ShipDesign> = throw RuntimeException("boom")
        }
        val vm = makeVM(loader = throwingLoader)
        assertTrue(vm.state.value.libraryReady)
        assertTrue(vm.state.value.libraryShipNames.isEmpty())
    }
}
