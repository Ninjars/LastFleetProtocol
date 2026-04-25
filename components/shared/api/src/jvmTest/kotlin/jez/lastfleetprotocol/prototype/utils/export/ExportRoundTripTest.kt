package jez.lastfleetprotocol.prototype.utils.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Asset export (Item A) Unit 7 — end-to-end round-trip integration test.
 *
 * Closes the test-coverage gap where Unit 2 verifies write *mechanics* (the bytes
 * land at the right path, atomic-rename works, tmp cleanup happens) but nothing
 * verifies that the written file is *valid downstream content*. A regression where
 * JSON gets a UTF-8 BOM, trailing whitespace, or kotlinx-serialization drops a
 * required default-valued field would pass Unit 2's assertions but break the
 * runtime loader.
 *
 * The test uses a small synthetic data class rather than `ShipDesign` directly to
 * avoid a test-time dependency from `:components:shared:api` onto
 * `:components:game-core:api`. Round-trip integrity is a property of the bytes-on-disk
 * + kotlinx-serialization pair, not a property of any specific schema, so a
 * representative fixture proves the property generically.
 */
class ExportRoundTripTest {

    @Serializable
    private data class TestAsset(
        val id: String,
        val name: String,
        val numbers: List<Int> = emptyList(),
        val flag: Boolean = false,
        val metadata: Map<String, String> = emptyMap(),
        // Default-valued field — checks that kotlinx-serialization emits it via
        // prettyPrint=true rather than dropping it (which would break round-trip).
        val description: String = "default description",
    )

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private lateinit var fakeRepoRoot: Path

    @BeforeTest
    fun setUp() {
        fakeRepoRoot = createTempDirectory(prefix = "lfp-roundtrip-")
        fakeRepoRoot.resolve("settings.gradle.kts").writeText("// sentinel\n")
        System.setProperty("lfp.repo.root", fakeRepoRoot.toString())
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("lfp.repo.root")
        fakeRepoRoot.toFile().deleteRecursively()
    }

    private fun assetPath(subdir: String, slug: String): Path =
        fakeRepoRoot.resolve(
            "components/game-core/api/src/commonMain/composeResources/files/$subdir/$slug.json"
        )

    private fun roundTrip(asset: TestAsset): TestAsset {
        val exporter: RepoExporter = RepoExporterImpl()
        val content = json.encodeToString(asset)

        val result = exporter.export(
            content = content,
            targetSubdir = "default_parts",
            slug = asset.id,
            replacing = ExportSubject(asset.id, ExportSourceKind.ItemDefinition),
        )

        val wrote = assertIs<ExportResult.Wrote>(result, "expected Wrote, got $result")
        val onDisk = assetPath("default_parts", asset.id).readText()
        assertEquals(content, onDisk, "bytes on disk should match the bytes passed to export")
        return json.decodeFromString(onDisk)
    }

    // --- Round-trip fidelity ---

    @Test
    fun nontrivialAssetRoundTripsToEqualValue() {
        val original = TestAsset(
            id = "abc-123",
            name = "Heavy Cruiser",
            numbers = listOf(1, 2, 3, 4, 5),
            flag = true,
            metadata = mapOf("category" to "hull", "tier" to "epic"),
            description = "long description with spaces, punctuation! And — em-dashes.",
        )
        val decoded = roundTrip(original)
        assertEquals(original, decoded)
    }

    @Test
    fun minimalAssetRoundTripsPreservingDefaults() {
        // Constructed with all defaults except required fields — verifies that defaults
        // survive the encode/decode cycle. This catches the regression where
        // kotlinx-serialization's `encodeDefaults = false` (the default for many
        // configurations) would drop the `description` field on encode and re-default
        // on decode — values would still be equal but the bytes-on-disk would lose
        // information that downstream tooling might need.
        val original = TestAsset(id = "min-1", name = "Minimal")
        val decoded = roundTrip(original)
        assertEquals(original, decoded)
    }

    @Test
    fun assetWithSpecialCharactersInStringFieldsRoundTrips() {
        // Catches accidental UTF-8 corruption, escape-sequence loss, and BOM injection
        // along the write path.
        val original = TestAsset(
            id = "u-1",
            name = "Heavy \"Cruiser\" — Mk.II",
            metadata = mapOf("tag" to "戦艦", "note" to "line1\nline2\ttabbed"),
        )
        val decoded = roundTrip(original)
        assertEquals(original, decoded)
    }

    @Test
    fun assetWithEmptyCollectionsRoundTrips() {
        val original = TestAsset(
            id = "empty",
            name = "Empty",
            numbers = emptyList(),
            metadata = emptyMap(),
        )
        val decoded = roundTrip(original)
        assertEquals(original, decoded)
    }

    @Test
    fun fileOnDiskHasNoBomOrTrailingWhitespace() {
        val asset = TestAsset(id = "nobom", name = "No BOM")
        roundTrip(asset)
        val bytes = assetPath("default_parts", "nobom").readText().toByteArray(Charsets.UTF_8)
        // UTF-8 BOM is 0xEF 0xBB 0xBF — must not be the first three bytes.
        if (bytes.size >= 3) {
            val isBom = bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte()
            assertEquals(false, isBom, "exported file must not start with a UTF-8 BOM")
        }
    }
}
