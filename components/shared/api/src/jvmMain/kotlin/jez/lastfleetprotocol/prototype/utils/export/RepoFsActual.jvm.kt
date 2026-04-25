package jez.lastfleetprotocol.prototype.utils.export

/**
 * JVM stub — Unit 1 lands this no-op so the multi-platform build compiles before
 * Unit 2 wires the real `System.getProperty(...)`, sentinel check, and atomic-rename.
 * Until Unit 2 lands, `RepoExporter.isAvailable` is always `false` on JVM too, and
 * every `export(...)` call returns `RequiresClipboard`. This keeps the gate closed
 * during partial rollouts.
 */
internal actual fun resolveRepoRoot(): String? = null

internal actual fun isValidRepoRoot(absolutePath: String): Boolean = false

internal actual fun writeRepoFile(absolutePath: String, content: String) {
    throw UnsupportedOperationException("Unit 2 has not landed yet — JVM repo write is a stub.")
}

internal actual fun isExistingFile(absolutePath: String): Boolean = false
