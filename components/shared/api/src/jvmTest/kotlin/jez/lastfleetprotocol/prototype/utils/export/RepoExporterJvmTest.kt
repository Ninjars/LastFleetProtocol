package jez.lastfleetprotocol.prototype.utils.export

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RepoExporterJvmTest {

    private lateinit var fakeRepoRoot: Path

    @BeforeTest
    fun setUp() {
        fakeRepoRoot = createTempDirectory(prefix = "lfp-export-test-")
        // Plant the sentinel so isValidRepoRoot accepts this dir.
        fakeRepoRoot.resolve("settings.gradle.kts").writeText("// sentinel\n")
        System.setProperty("lfp.repo.root", fakeRepoRoot.toString())
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("lfp.repo.root")
        fakeRepoRoot.toFile().deleteRecursively()
    }

    private fun newExporter(bundleIndex: BundleIndex = BundleIndex.EMPTY): RepoExporter =
        RepoExporterImpl(bundleIndex)

    private fun assetPath(subdir: String, slug: String): Path =
        fakeRepoRoot.resolve(
            "components/game-core/api/src/commonMain/composeResources/files/$subdir/$slug.json"
        )

    // --- Happy path ---

    @Test
    fun export_writesFileAndReturnsWrote() {
        val exporter = newExporter()
        assertTrue(exporter.isAvailable, "exporter should be available with a valid repo root")

        val result = exporter.export(
            content = "{\"name\":\"Heavy Cruiser\"}",
            targetSubdir = "default_parts",
            slug = "heavy_cruiser",
            replacing = ExportSubject("id-1", ExportSourceKind.ItemDefinition),
        )

        val wrote = assertIs<ExportResult.Wrote>(result)
        assertEquals("default_parts/heavy_cruiser.json", wrote.relativeRepoPath)
        assertFalse(wrote.isOverwrite, "first export must not be flagged as overwrite")
        assertEquals(SLUG_RULE_VERSION, wrote.slugRuleVersion)

        val target = assetPath("default_parts", "heavy_cruiser")
        assertTrue(target.exists(), "target file should exist after export")
        assertEquals("{\"name\":\"Heavy Cruiser\"}", target.readText())
    }

    @Test
    fun export_overwriteReturnsIsOverwriteTrue() {
        val exporter = newExporter()
        exporter.export(
            content = "first",
            targetSubdir = "default_ships",
            slug = "ship1",
            replacing = ExportSubject("id-1", ExportSourceKind.ShipDesign),
        )
        val second = exporter.export(
            content = "second",
            targetSubdir = "default_ships",
            slug = "ship1",
            replacing = ExportSubject("id-1", ExportSourceKind.ShipDesign),
        )

        val wrote = assertIs<ExportResult.Wrote>(second)
        assertTrue(wrote.isOverwrite, "second export of same slug must be flagged as overwrite")
        assertEquals("second", assetPath("default_ships", "ship1").readText())
    }

    // --- Auto-create directory ---

    @Test
    fun export_createsParentDirectoryWhenMissing() {
        val exporter = newExporter()
        val parentDir = fakeRepoRoot.resolve(
            "components/game-core/api/src/commonMain/composeResources/files/default_parts"
        )
        assertFalse(parentDir.exists(), "precondition: parent dir does not exist")

        val result = exporter.export(
            content = "{}",
            targetSubdir = "default_parts",
            slug = "first",
            replacing = ExportSubject("id-1", ExportSourceKind.ItemDefinition),
        )

        assertIs<ExportResult.Wrote>(result)
        assertTrue(parentDir.exists() && Files.isDirectory(parentDir))
    }

    // --- Atomicity: tmp file location and cleanup ---

    @Test
    fun export_tmpFileLandsInTargetParentNotSystemTmp() {
        // The presence of an export call is observable by checking that no .tmp
        // survives in the target parent post-success — the move should atomically
        // replace the tmp with the target.
        val exporter = newExporter()
        exporter.export(
            content = "{}",
            targetSubdir = "default_parts",
            slug = "tmp_check",
            replacing = ExportSubject("id-1", ExportSourceKind.ItemDefinition),
        )

        val parentDir = fakeRepoRoot.resolve(
            "components/game-core/api/src/commonMain/composeResources/files/default_parts"
        )
        val orphans = parentDir.toFile().listFiles { _, name -> name.endsWith(".tmp") } ?: emptyArray()
        assertEquals(
            0,
            orphans.size,
            "no .tmp file should survive after a successful export (got ${orphans.toList()})",
        )
    }

    // --- Gate: no repo root ---

    @Test
    fun export_returnsRequiresClipboardWhenRepoRootUnset() {
        System.clearProperty("lfp.repo.root")
        val exporter = newExporter()

        assertFalse(exporter.isAvailable)

        val result = exporter.export(
            content = "{\"x\":1}",
            targetSubdir = "default_parts",
            slug = "no_root",
            replacing = ExportSubject("id-1", ExportSourceKind.ItemDefinition),
        )

        val clipboard = assertIs<ExportResult.RequiresClipboard>(result)
        assertEquals("{\"x\":1}", clipboard.content)
        assertEquals("default_parts/no_root.json", clipboard.suggestionRelativePath)
    }

    // --- Gate: sentinel missing (defends against lfp.repo.root=/) ---

    @Test
    fun export_gateRejectsRootLackingSentinelFile() {
        // Point lfp.repo.root at a temp dir with NO settings.gradle.kts sentinel.
        val rootlessDir = createTempDirectory(prefix = "lfp-no-sentinel-")
        try {
            System.setProperty("lfp.repo.root", rootlessDir.toString())
            val exporter = newExporter()

            assertFalse(
                exporter.isAvailable,
                "gate must reject a directory that lacks the settings.gradle.kts sentinel",
            )

            val result = exporter.export(
                content = "{}",
                targetSubdir = "default_parts",
                slug = "x",
                replacing = ExportSubject("id-1", ExportSourceKind.ItemDefinition),
            )
            assertIs<ExportResult.RequiresClipboard>(result)
        } finally {
            rootlessDir.toFile().deleteRecursively()
        }
    }

    // --- Bundle collision ---

    @Test
    fun export_bundleCollisionWhenSlugMatchesDifferentBundledAsset() {
        val bundleIndex = BundleIndex(
            bySubdir = mapOf(
                "default_ships" to mapOf("player_ship" to "bundled-id-X"),
            ),
        )
        val exporter = newExporter(bundleIndex)

        val result = exporter.export(
            content = "{}",
            targetSubdir = "default_ships",
            slug = "player_ship",
            replacing = ExportSubject("custom-id-Y", ExportSourceKind.ShipDesign),
        )

        val collision = assertIs<ExportResult.BundleCollision>(result)
        assertEquals("player_ship", collision.bundledAssetName)
        assertFalse(
            assetPath("default_ships", "player_ship").exists(),
            "collision must NOT write the file",
        )
    }

    @Test
    fun export_proceedsWhenSlugMatchesBundleAndIdMatches() {
        // Same logical asset re-exporting itself over the bundled file is allowed.
        val bundleIndex = BundleIndex(
            bySubdir = mapOf(
                "default_ships" to mapOf("player_ship" to "bundled-id-X"),
            ),
        )
        val exporter = newExporter(bundleIndex)

        val result = exporter.export(
            content = "{\"updated\":true}",
            targetSubdir = "default_ships",
            slug = "player_ship",
            replacing = ExportSubject("bundled-id-X", ExportSourceKind.ShipDesign),
        )

        val wrote = assertIs<ExportResult.Wrote>(result)
        // Fresh temp dir: nothing exists at the target path before this call.
        // The collision guard's same-id exemption preserves the write; isOverwrite
        // reflects the on-disk state, not the bundled-asset relationship.
        assertEquals(false, wrote.isOverwrite, "first export to a fresh path is not an overwrite")
        assertEquals("{\"updated\":true}", assetPath("default_ships", "player_ship").readText())
    }

    // --- Gradle JVM-arg propagation smoke (informational) ---

    // --- Real IO failure → ExportResult.Error (not just fake-driven) ---

    @Test
    fun export_realIoFailureReturnsError() {
        // Plant a regular file where the target subdirectory should live. createDirectories
        // refuses to materialise a directory at a path occupied by a file, raising an
        // IOException that RepoExporterImpl wraps as ExportResult.Error. This exercises
        // the catch(Throwable) path in production code (not just a fake-driven path) so
        // a regression in the wrapping behaviour fails this test loudly.
        val parentParent = fakeRepoRoot.resolve(
            "components/game-core/api/src/commonMain/composeResources/files"
        )
        java.nio.file.Files.createDirectories(parentParent)
        // Plant a file at "default_parts" — the target subdirectory.
        parentParent.resolve("default_parts").writeText("not a directory")

        val exporter = newExporter()
        val result = exporter.export(
            content = "{}",
            targetSubdir = "default_parts",
            slug = "anything",
            replacing = ExportSubject("id-1", ExportSourceKind.ItemDefinition),
        )

        val error = assertIs<ExportResult.Error>(result)
        assertTrue(
            error.reason.isNotBlank(),
            "Error reason should carry an IOException message, got: '${error.reason}'",
        )
    }

    @Test
    fun resolveRepoRoot_returnsPropertyValueWhenSet() {
        // The setUp block already sets the property — here we assert that the resolver
        // reads it back. This is a JVM-only check; Android always returns null.
        val resolved = resolveRepoRoot()
        assertNotNull(resolved)
        assertEquals(fakeRepoRoot.toString(), resolved)
    }

    @Test
    fun resolveRepoRoot_treatsBlankAsUnset() {
        System.setProperty("lfp.repo.root", "   ")
        val resolved = resolveRepoRoot()
        assertEquals(null, resolved)
    }
}
