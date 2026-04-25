package jez.lastfleetprotocol.prototype.utils.export

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

private const val REPO_ROOT_PROPERTY: String = "lfp.repo.root"
private const val SENTINEL_FILE: String = "settings.gradle.kts"
private const val TMP_SUFFIX: String = ".tmp"

internal actual fun resolveRepoRoot(): String? =
    System.getProperty(REPO_ROOT_PROPERTY)?.takeIf { it.isNotBlank() }

internal actual fun isValidRepoRoot(absolutePath: String): Boolean {
    val path = Paths.get(absolutePath)
    return Files.isDirectory(path) && Files.exists(path.resolve(SENTINEL_FILE))
}

/**
 * Atomic write of [content] to [absolutePath].
 *
 * Order matters:
 *   1. Create parent directories if missing — auto-create satisfies R2 ("default_parts/
 *      created on first export").
 *   2. Allocate a temp file *in the same parent directory* as the target. Same-filesystem
 *      placement is what makes [StandardCopyOption.ATOMIC_MOVE] actually atomic.
 *   3. Write content to the temp file. (Errors here surface as IOException; the finally
 *      block cleans up.)
 *   4. Atomic rename the temp into place, replacing any existing file.
 *
 * The `try/finally` ensures a partial-write tmp file never lingers in the source tree,
 * which would otherwise risk being committed accidentally via `git add -A`. The
 * `composeResources/files/.gitignore` adds a second line of defence.
 *
 * Throws on any I/O failure. The caller (`RepoExporterImpl`) wraps the throwable into
 * [ExportResult.Error].
 */
internal actual fun writeRepoFile(absolutePath: String, content: String) {
    val target: Path = Paths.get(absolutePath)
    val parent: Path = target.parent
        ?: throw IOException("Target path has no parent directory: $absolutePath")

    Files.createDirectories(parent)

    val tmp: Path = parent.resolve(target.fileName.toString() + TMP_SUFFIX)
    try {
        Files.writeString(tmp, content)
        Files.move(
            tmp,
            target,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } finally {
        // No-op when the move succeeded (tmp is gone). On failure, removes the
        // half-written file so it doesn't pollute the source tree.
        Files.deleteIfExists(tmp)
    }
}

internal actual fun isExistingFile(absolutePath: String): Boolean {
    val path = Paths.get(absolutePath)
    return Files.isRegularFile(path)
}
