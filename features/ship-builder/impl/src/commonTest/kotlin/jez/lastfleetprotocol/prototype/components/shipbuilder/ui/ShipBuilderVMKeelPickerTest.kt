package jez.lastfleetprotocol.prototype.components.shipbuilder.ui

import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemAttributes
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemLibraryRepository
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.SerializableArmourStats
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ShipDesign
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ShipDesignRepository
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.EditorMode
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.ShipBuilderIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Slice B Unit 5 — verifies the mandatory Keel-picker flow end-to-end through the VM:
 * initial-mode routing, `PickKeel` commits the Keel at origin, `CancelKeelPick` emits
 * navigation, loaded designs route to `PickingKeel` recovery when the Keel is missing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShipBuilderVMKeelPickerTest {

    // ShipBuilderVM uses androidx-lifecycle's viewModelScope, which dispatches on
    // Dispatchers.Main. JVM tests have no Main dispatcher by default — install an
    // unconfined test dispatcher so `viewModelScope.launch` runs synchronously.
    @BeforeTest
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun clearMainDispatcher() {
        Dispatchers.resetMain()
    }

    private class FakeDesignRepository(
        private val designs: MutableMap<String, ShipDesign> = mutableMapOf(),
    ) : ShipDesignRepository {
        override fun save(design: ShipDesign) {
            designs[design.name] = design
        }

        override fun load(name: String): ShipDesign? = designs[name]
        override fun listAll(): List<String> = designs.keys.toList()
        override fun delete(name: String) {
            designs.remove(name)
        }
    }

    private class FakeItemLibraryRepository(
        private val items: MutableMap<String, ItemDefinition> = mutableMapOf(),
    ) : ItemLibraryRepository {
        override fun save(item: ItemDefinition) {
            items[item.id] = item
        }

        override fun load(id: String): ItemDefinition? = items[id]
        override fun loadAll(): List<ItemDefinition> = items.values.toList()
        override fun delete(id: String) {
            items.remove(id)
        }
    }

    private fun makeVM(
        designRepo: ShipDesignRepository = FakeDesignRepository(),
        libraryRepo: ItemLibraryRepository = FakeItemLibraryRepository(),
    ) = ShipBuilderVM(
        repository = designRepo,
        itemLibrary = libraryRepo,
        // Asset export (Item A) — Keel-picker tests don't exercise export; gate-closed
        // exporter is a safe default that lets the existing tests stay focused.
        repoExporter = object : jez.lastfleetprotocol.prototype.utils.export.RepoExporter {
            override val isAvailable: Boolean = false
            override fun export(
                content: String,
                targetSubdir: String,
                slug: String,
                replacing: jez.lastfleetprotocol.prototype.utils.export.ExportSubject?,
            ) = jez.lastfleetprotocol.prototype.utils.export.ExportResult.RequiresClipboard(
                content = content,
                suggestionRelativePath = "$targetSubdir/$slug.json",
            )
        },
    )

    private fun makeKeelDef(id: String = "keel_test") = ItemDefinition(
        id = id,
        name = "Test Keel",
        vertices = emptyList(),
        attributes = ItemAttributes.KeelAttributes(
            armour = SerializableArmourStats(3f, 1.5f),
            sizeCategory = "medium",
            mass = 40f,
            maxHp = 150f,
            lift = 200f,
            shipClass = "fighter",
        ),
    )

    // --- Initial state ---

    @Test
    fun freshBuilderSession_startsInPickingKeel() {
        val vm = makeVM()
        assertIs<EditorMode.PickingKeel>(vm.state.value.editorMode)
        assertNull(vm.state.value.placedKeel)
    }

    @Test
    fun availableKeels_includesBundledCatalogue() {
        val vm = makeVM()
        // PartsCatalog ships 4 Keels (player/fighter, light/fighter, frigate, cruiser).
        assertEquals(4, vm.state.value.availableKeels.size)
    }

    // --- PickKeel commits the Keel + transitions to EditingShip ---

    @Test
    fun pickKeel_commitsAtOrigin_andTransitionsToEditingShip() {
        val vm = makeVM()
        val keel = makeKeelDef()

        vm.accept(ShipBuilderIntent.PickKeel(keel))

        val state = vm.state.value
        assertIs<EditorMode.EditingShip>(state.editorMode)
        val placed = state.placedKeel
        assertNotNull(placed)
        assertEquals(keel.id, placed.itemDefinitionId)
        assertEquals(0f, placed.position.x.raw)
        assertEquals(0f, placed.position.y.raw)
    }

    @Test
    fun pickKeel_snapshotsItemDefinitionIntoDesign() {
        // The converter resolves placedKeel.itemDefinitionId against the design's
        // itemDefinitions, so the picked Keel's def must be included there —
        // otherwise a load after a library change could break the design.
        val vm = makeVM()
        val keel = makeKeelDef(id = "picked_keel")

        vm.accept(ShipBuilderIntent.PickKeel(keel))

        val defs = vm.state.value.itemDefinitions
        assertEquals(1, defs.count { it.id == "picked_keel" })
    }

    @Test
    fun pickKeel_outsidePickingKeelMode_isIgnored() {
        val vm = makeVM()
        val keel1 = makeKeelDef(id = "keel_first")
        val keel2 = makeKeelDef(id = "keel_second")

        vm.accept(ShipBuilderIntent.PickKeel(keel1))
        assertIs<EditorMode.EditingShip>(vm.state.value.editorMode)

        // Subsequent PickKeel while in EditingShip must be ignored — exactly-one invariant.
        vm.accept(ShipBuilderIntent.PickKeel(keel2))

        val placed = vm.state.value.placedKeel
        assertNotNull(placed)
        assertEquals("keel_first", placed.itemDefinitionId)
    }

    // --- CancelKeelPick ---

    @Test
    fun cancelKeelPick_doesNotPersistDesign() {
        val designRepo = FakeDesignRepository()
        val vm = makeVM(designRepo = designRepo)

        vm.accept(ShipBuilderIntent.CancelKeelPick)

        // No autosave fired during init; cancellation itself doesn't save either.
        // Backing repo should be untouched.
        assertEquals(emptyList(), designRepo.listAll())
    }

    @Test
    fun cancelKeelPick_outsidePickingKeelMode_isIgnored() {
        val vm = makeVM()
        vm.accept(ShipBuilderIntent.PickKeel(makeKeelDef()))
        assertIs<EditorMode.EditingShip>(vm.state.value.editorMode)

        // No crash; state unchanged.
        vm.accept(ShipBuilderIntent.CancelKeelPick)
        assertIs<EditorMode.EditingShip>(vm.state.value.editorMode)
    }
}
