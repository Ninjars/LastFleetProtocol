package jez.lastfleetprotocol.prototype.utils.export

/**
 * Resolve the repo root from a platform-specific source.
 *
 * - **JVM:** reads the `lfp.repo.root` system property (set by `./gradlew :composeApp:run`
 *   via the `compose.desktop.application.jvmArgs` injection).
 * - **Android:** returns `null` — Android has no repo filesystem; the export action is
 *   gated off entirely.
 *
 * Returns `null` when the property is unset, blank, or the platform doesn't support
 * the export feature.
 */
internal expect fun resolveRepoRoot(): String?

/**
 * Validate that [absolutePath] is plausibly the project's repo root by checking for a
 * sentinel file (`settings.gradle.kts`). This defends against a misconfigured
 * `-Dlfp.repo.root=/` clearing the gate — without the sentinel check, the exporter
 * would happily write into `/components/game-core/...` on the system root.
 *
 * Returns `false` when the path doesn't exist, isn't a directory, or lacks the sentinel.
 */
internal expect fun isValidRepoRoot(absolutePath: String): Boolean

/**
 * Atomic write of [content] to [absolutePath].
 *
 * Implementations:
 *   1. Create the target's parent directory if missing.
 *   2. Write to `<absolutePath>.tmp` *in the same parent directory* (guarantees
 *      same-filesystem move).
 *   3. `Files.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING)`.
 *   4. On any exception, the `.tmp` file is removed via `try/finally` so a partial
 *      write doesn't leave a zombie file in the source tree.
 *
 * Throws on I/O failure. The caller (`RepoExporterImpl`) catches and translates to
 * [ExportResult.Error].
 */
internal expect fun writeRepoFile(absolutePath: String, content: String)

/**
 * Quick existence-and-regular-file check for an absolute path. Used by the exporter
 * to decide whether `Wrote(...)` is a first export or an overwrite.
 *
 * Returns `false` on Android (no repo filesystem) and on any path that doesn't exist
 * or isn't a regular file.
 */
internal expect fun isExistingFile(absolutePath: String): Boolean
