package jez.lastfleetprotocol.prototype.utils.export

import me.tatarka.inject.annotations.Inject

/**
 * Distinguishes the two kinds of asset that the export pipeline understands. The slug
 * lives in different subdirectories under `composeResources/files/` for each.
 */
enum class ExportSourceKind {
    /** A library item (HULL / MODULE / TURRET / KEEL). Lands in `default_parts/`. */
    ItemDefinition,

    /** A complete ship design. Lands in `default_ships/`. */
    ShipDesign,
}

/**
 * Identifies the asset being exported so the bundle-collision guard can compare an
 * incoming asset's id against the id of any bundled asset that already occupies the
 * target slug.
 */
data class ExportSubject(
    val id: String,
    val sourceKind: ExportSourceKind,
)

/**
 * Outcome of a single [RepoExporter.export] call.
 *
 * - [Wrote] — the JSON landed at [relativeRepoPath]. [isOverwrite] distinguishes a
 *   first export from a re-export; the toast layer uses this to switch wording.
 *   [slugRuleVersion] mirrors [SLUG_RULE_VERSION] at the time of write so a future
 *   rule change can detect and migrate older exports.
 * - [RequiresClipboard] — the gate is closed (no repo-root resolved). The caller
 *   should put [content] on the system clipboard and surface [suggestionRelativePath]
 *   so the dev can paste-and-create-file manually.
 * - [Error] — the gate was open but the write itself failed (permissions, missing
 *   parent directory, atomic-move not supported on the target filesystem). [reason]
 *   is human-readable and surfaces in the error toast.
 * - [BundleCollision] — the slug matches an existing bundled-asset slug for a
 *   *different* logical asset. Refuses the write to prevent silent shadowing of the
 *   bundled catalogue. [bundledAssetName] surfaces in the rename-required toast.
 */
sealed interface ExportResult {
    data class Wrote(
        val relativeRepoPath: String,
        val isOverwrite: Boolean,
        val slugRuleVersion: Int,
    ) : ExportResult

    data class RequiresClipboard(
        val content: String,
        val suggestionRelativePath: String,
    ) : ExportResult

    data class Error(val reason: String) : ExportResult

    data class BundleCollision(val bundledAssetName: String) : ExportResult
}

/**
 * Promotes hull-part and ship-design JSON from app-data into the repo's
 * `composeResources/files/` tree.
 *
 * `isAvailable` is fixed at construction. The runtime gate combines:
 *   1. Platform = JVM (Android always returns false).
 *   2. `lfp.repo.root` system property is set and non-blank.
 *   3. The resolved root contains a `settings.gradle.kts` sentinel — defends against
 *      a misconfigured `lfp.repo.root=/` clearing the gate.
 *
 * When `isAvailable == false`, every `export(...)` call returns [ExportResult.RequiresClipboard]
 * so the caller can fall back to clipboard-and-manual-paste.
 */
interface RepoExporter {
    val isAvailable: Boolean

    fun export(
        content: String,
        targetSubdir: String,
        slug: String,
        replacing: ExportSubject?,
    ): ExportResult
}

/**
 * Shape of the per-subdirectory bundle index passed into [RepoExporterImpl] so the
 * collision guard knows which committed slugs are already taken (and by which logical
 * asset id). For v1 the only bundled-asset directory is `default_ships/`; the parts
 * directory is empty until the first export lands content into it. The exporter does
 * not enumerate `composeResources/` itself — that requires `Res.readBytes` which is a
 * UI-thread suspend function. Callers (via DI) construct the index at app startup.
 */
data class BundleIndex(
    /** Map of `targetSubdir` → (slug → bundled asset id). */
    val bySubdir: Map<String, Map<String, String>>,
) {
    fun lookupId(subdir: String, slug: String): String? = bySubdir[subdir]?.get(slug)

    fun lookupName(subdir: String, slug: String): String? =
        // Names mirror slugs for human-readable toasts. If the bundled assets ever
        // diverge, this can grow into a full id-to-name map.
        if (lookupId(subdir, slug) != null) slug else null

    companion object {
        val EMPTY = BundleIndex(emptyMap())
    }
}

/**
 * Default [RepoExporter] implementation. Composes the gate from the platform, the
 * `lfp.repo.root` system property, and a sentinel-file check; delegates write
 * mechanics to [resolveRepoRoot] / [isValidRepoRoot] / [writeRepoFile] expect/actual
 * functions.
 *
 * The exporter resolves the gate once at construction. The system property and
 * filesystem state don't change at runtime, so re-evaluating on every call would only
 * add cost.
 */
@Inject
class RepoExporterImpl(
    private val bundleIndex: BundleIndex = BundleIndex.EMPTY,
) : RepoExporter {

    private val resolvedRoot: String? = run {
        val root = resolveRepoRoot()
        when {
            root == null -> null
            isValidRepoRoot(root) -> root
            else -> {
                // Set but invalid: dev moved the repo, drive unmounted, or the path
                // doesn't contain settings.gradle.kts. Log once so the silent
                // clipboard fallback isn't a complete mystery — without this, the
                // dev sees export-disappears-from-UI / clipboard-fallback with no
                // indication that the property is the cause.
                println(
                    "[RepoExporter] lfp.repo.root='$root' is set but invalid " +
                        "(directory missing or no settings.gradle.kts sentinel). " +
                        "Export action will be hidden / fall back to clipboard.",
                )
                null
            }
        }
    }

    override val isAvailable: Boolean
        get() = resolvedRoot != null

    override fun export(
        content: String,
        targetSubdir: String,
        slug: String,
        replacing: ExportSubject?,
    ): ExportResult {
        // Gate first. When the runtime gate is closed (Android, packaged Desktop,
        // IDE Run without lfp.repo.root), every export returns RequiresClipboard —
        // including the bundle-collision case. The collision guard exists to defend
        // the in-repo bundled assets from being silently shadowed by a write; with
        // no write happening, there's nothing to defend against.
        val root = resolvedRoot
            ?: return ExportResult.RequiresClipboard(
                content = content,
                suggestionRelativePath = "$targetSubdir/$slug.json",
            )

        val bundledId = bundleIndex.lookupId(targetSubdir, slug)
        if (bundledId != null && bundledId != replacing?.id) {
            val name = bundleIndex.lookupName(targetSubdir, slug) ?: slug
            return ExportResult.BundleCollision(name)
        }

        val relative = "$targetSubdir/$slug.json"
        val absolute = "$root/$BUNDLED_ASSETS_RELATIVE_ROOT/$relative"
        val isOverwrite = isExistingFile(absolute)

        return try {
            writeRepoFile(absolute, content)
            ExportResult.Wrote(
                relativeRepoPath = relative,
                isOverwrite = isOverwrite,
                slugRuleVersion = SLUG_RULE_VERSION,
            )
        } catch (t: Throwable) {
            ExportResult.Error(t.message ?: t::class.simpleName ?: "unknown error")
        }
    }

    private companion object {
        const val BUNDLED_ASSETS_RELATIVE_ROOT =
            "components/game-core/api/src/commonMain/composeResources/files"
    }
}
