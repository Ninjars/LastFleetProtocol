package jez.lastfleetprotocol.prototype.components.shipbuilder.ui

import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemAttributes
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemLibraryRepository
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.SerializableArmourStats
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ShipDesign
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ShipDesignRepository
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.ShipBuilderIntent
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.ShipBuilderSideEffect
import jez.lastfleetprotocol.prototype.utils.export.ExportResult
import jez.lastfleetprotocol.prototype.utils.export.ExportSourceKind
import jez.lastfleetprotocol.prototype.utils.export.ExportSubject
import jez.lastfleetprotocol.prototype.utils.export.RepoExporter
import jez.lastfleetprotocol.prototype.utils.export.SLUG_RULE_VERSION
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
import kotlin.test.assertTrue

/**
 * Asset export (Item A) Unit 4 — verifies the VM intent → use-case → side-effect mapping
 * against a fake [RepoExporter]. Exhaustively covers each [ExportResult] variant plus the
 * gate-state seeding via `state.canExport`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShipBuilderVMExportTest {

    private lateinit var collectorScope: CoroutineScope
    private val captured = mutableListOf<ShipBuilderSideEffect>()
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

    private fun startCollecting(vm: ShipBuilderVM) {
        // Start the side-effect collector BEFORE the test triggers any intent. The
        // VM's MutableSharedFlow has no replay buffer, so emissions only reach
        // collectors that are already active. CoroutineStart.UNDISPATCHED ensures
        // the launch enters the suspending collect{} call before returning.
        collectorJob = collectorScope.launch(start = CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.toList(captured)
        }
    }

    // --- Fakes ---

    private class FakeDesignRepository : ShipDesignRepository {
        val saved = mutableListOf<ShipDesign>()
        override fun save(design: ShipDesign) { saved += design }
        override fun load(name: String): ShipDesign? = null
        override fun listAll(): List<String> = emptyList()
        override fun delete(name: String) {}
    }

    private class FakeItemLibraryRepository(
        private val items: MutableMap<String, ItemDefinition> = mutableMapOf(),
    ) : ItemLibraryRepository {
        override fun save(item: ItemDefinition) { items[item.id] = item }
        override fun load(id: String): ItemDefinition? = items[id]
        override fun loadAll(): List<ItemDefinition> = items.values.toList()
        override fun delete(id: String) { items.remove(id) }
    }

    /** Fake exporter that records calls and returns a configurable result. */
    private class FakeRepoExporter(
        override val isAvailable: Boolean = true,
        var nextResult: ExportResult = ExportResult.Wrote(
            relativeRepoPath = "default_parts/x.json",
            isOverwrite = false,
            slugRuleVersion = SLUG_RULE_VERSION,
        ),
    ) : RepoExporter {
        var lastContent: String? = null
        var lastSubdir: String? = null
        var lastSlug: String? = null
        var lastReplacing: ExportSubject? = null

        override fun export(
            content: String,
            targetSubdir: String,
            slug: String,
            replacing: ExportSubject?,
        ): ExportResult {
            lastContent = content
            lastSubdir = targetSubdir
            lastSlug = slug
            lastReplacing = replacing
            return nextResult
        }
    }

    // --- Fixtures ---

    private fun makeHullItem(
        id: String = "item-1",
        name: String = "Heavy Cruiser",
    ) = ItemDefinition(
        id = id,
        name = name,
        vertices = emptyList(),
        attributes = ItemAttributes.HullAttributes(
            mass = 100f,
            armour = SerializableArmourStats(hardness = 1f, density = 1f),
            sizeCategory = "medium",
        ),
    )

    private fun makeVM(
        repoExporter: RepoExporter = FakeRepoExporter(),
    ): ShipBuilderVM = ShipBuilderVM(
        repository = FakeDesignRepository(),
        itemLibrary = FakeItemLibraryRepository(),
        repoExporter = repoExporter,
    )

    // --- Gate state seeding ---

    @Test
    fun init_canExportFalseWhenExporterUnavailable() {
        val vm = makeVM(FakeRepoExporter(isAvailable = false))
        assertFalse(vm.state.value.canExport)
    }

    @Test
    fun init_canExportTrueWhenExporterAvailable() {
        val vm = makeVM(FakeRepoExporter(isAvailable = true))
        assertTrue(vm.state.value.canExport)
    }

    // --- Wrote(isOverwrite=false) → Exported to toast ---

    @Test
    fun exportLibraryItem_wroteNewFile_emitsExportedToToast() {
        val exporter = FakeRepoExporter(
            nextResult = ExportResult.Wrote(
                relativeRepoPath = "default_parts/heavy_cruiser.json",
                isOverwrite = false,
                slugRuleVersion = SLUG_RULE_VERSION,
            ),
        )
        val vm = makeVM(exporter)
        startCollecting(vm)

        vm.accept(ShipBuilderIntent.ExportLibraryItem(makeHullItem()))

        assertEquals(1, captured.size, "expected exactly one side effect, got $captured")
        val effect = assertIs<ShipBuilderSideEffect.ShowToast>(captured[0])
        assertContains(effect.text, "Exported to")
        assertContains(effect.text, "default_parts/heavy_cruiser.json")
    }

    // --- Wrote(isOverwrite=true) → Overwrote toast ---

    @Test
    fun exportLibraryItem_wroteOverwrite_emitsOverwroteToast() {
        val exporter = FakeRepoExporter(
            nextResult = ExportResult.Wrote(
                relativeRepoPath = "default_parts/heavy_cruiser.json",
                isOverwrite = true,
                slugRuleVersion = SLUG_RULE_VERSION,
            ),
        )
        val vm = makeVM(exporter)
        startCollecting(vm)

        vm.accept(ShipBuilderIntent.ExportLibraryItem(makeHullItem()))

        val effect = assertIs<ShipBuilderSideEffect.ShowToast>(captured[0])
        assertContains(effect.text, "Overwrote")
        assertContains(effect.text, "default_parts/heavy_cruiser.json")
    }

    // --- RequiresClipboard → CopyToClipboard ---

    @Test
    fun exportLibraryItem_requiresClipboard_emitsCopyToClipboard() {
        val exporter = FakeRepoExporter(
            isAvailable = false,
            nextResult = ExportResult.RequiresClipboard(
                content = "{\"item\":\"json\"}",
                suggestionRelativePath = "default_parts/heavy_cruiser.json",
            ),
        )
        val vm = makeVM(exporter)
        startCollecting(vm)

        vm.accept(ShipBuilderIntent.ExportLibraryItem(makeHullItem()))

        val effect = assertIs<ShipBuilderSideEffect.CopyToClipboard>(captured[0])
        assertEquals("{\"item\":\"json\"}", effect.text)
        assertContains(effect.toastMessage, "JSON copied")
        assertContains(effect.toastMessage, "default_parts/heavy_cruiser.json")
    }

    // --- Error → Export failed toast ---

    @Test
    fun exportLibraryItem_error_emitsExportFailedToast() {
        val exporter = FakeRepoExporter(
            nextResult = ExportResult.Error("permission denied"),
        )
        val vm = makeVM(exporter)
        startCollecting(vm)

        vm.accept(ShipBuilderIntent.ExportLibraryItem(makeHullItem()))

        val effect = assertIs<ShipBuilderSideEffect.ShowToast>(captured[0])
        assertContains(effect.text, "Export failed")
        assertContains(effect.text, "permission denied")
    }

    // --- BundleCollision → rename guidance toast ---

    @Test
    fun exportLibraryItem_bundleCollision_emitsBundleCollisionToast() {
        val exporter = FakeRepoExporter(
            nextResult = ExportResult.BundleCollision("Player Ship"),
        )
        val vm = makeVM(exporter)
        startCollecting(vm)

        vm.accept(ShipBuilderIntent.ExportLibraryItem(makeHullItem()))

        val effect = assertIs<ShipBuilderSideEffect.ShowToast>(captured[0])
        assertContains(effect.text, "Player Ship")
        assertContains(effect.text, "rename")
    }

    // --- Slug derivation: VM passes raw name + id, not pre-slugged value ---

    @Test
    fun exportLibraryItem_passesRawNameAndIdToExporter() {
        val exporter = FakeRepoExporter()
        val vm = makeVM(exporter)
        startCollecting(vm)

        val item = makeHullItem(id = "abc-123", name = "Heavy Cruiser!!!")
        vm.accept(ShipBuilderIntent.ExportLibraryItem(item))

        // VM must derive slug + pass id on the ExportSubject. Slug derivation routes
        // through SlugRule.toSlug(name, id). The fake captures the slug arg.
        assertEquals("default_parts", exporter.lastSubdir)
        assertEquals("heavy_cruiser", exporter.lastSlug, "VM should derive slug from raw name")
        val subject = exporter.lastReplacing
        assertEquals("abc-123", subject?.id)
        assertEquals(ExportSourceKind.ItemDefinition, subject?.sourceKind)
    }

    // --- Round-trip serialization ---

    @Test
    fun exportLibraryItem_serializesItemDefinition() {
        val exporter = FakeRepoExporter()
        val vm = makeVM(exporter)
        startCollecting(vm)

        val item = makeHullItem(id = "abc-123", name = "Heavy Cruiser")
        vm.accept(ShipBuilderIntent.ExportLibraryItem(item))

        val content = exporter.lastContent
        assertTrue(content != null && content.isNotBlank(), "VM should pass non-empty content")
        // Content is JSON containing the item id and name.
        assertContains(content, "abc-123")
        assertContains(content, "Heavy Cruiser")
    }

    // --- Design export targets default_ships ---

    @Test
    fun exportCurrentDesign_targetsDefaultShipsSubdir() {
        val exporter = FakeRepoExporter()
        val vm = makeVM(exporter)
        startCollecting(vm)

        vm.accept(ShipBuilderIntent.ExportCurrentDesign)

        assertEquals("default_ships", exporter.lastSubdir)
        assertEquals(ExportSourceKind.ShipDesign, exporter.lastReplacing?.sourceKind)
    }
}
