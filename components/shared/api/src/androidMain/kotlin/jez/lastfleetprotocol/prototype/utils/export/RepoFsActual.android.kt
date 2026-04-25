package jez.lastfleetprotocol.prototype.utils.export

/**
 * Android has no repo filesystem at runtime — the export feature is gated off via
 * [resolveRepoRoot] returning `null`, which keeps `RepoExporter.isAvailable` false.
 * The other actuals throw or return false as belt-and-suspenders defence; the gate
 * should prevent them from ever being invoked in production.
 */
internal actual fun resolveRepoRoot(): String? = null

internal actual fun isValidRepoRoot(absolutePath: String): Boolean = false

internal actual fun writeRepoFile(absolutePath: String, content: String) {
    throw UnsupportedOperationException("Repo export is not supported on Android.")
}

internal actual fun isExistingFile(absolutePath: String): Boolean = false
