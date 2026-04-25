package jez.lastfleetprotocol.prototype.utils.export

/**
 * Version of the slug rule below. Carried on [ExportResult.Wrote] so a future migration
 * can detect "this filename was derived under v1" without reading file contents. Bump
 * when the rule changes; the migration tool re-derives existing filenames under the new
 * rule.
 */
const val SLUG_RULE_VERSION: Int = 1

private const val MAX_SLUG_LENGTH: Int = 64
private const val FALLBACK_PREFIX: String = "untitled-"
private const val FALLBACK_SEED_TAKE: Int = 8

/**
 * Canonical slug rule for exported asset filenames. Stricter than
 * `FileShipDesignRepository.sanitizeName` (which preserves spaces and case) — the export
 * pipeline standardises on a portable, traversal-safe form.
 *
 * Rule:
 *   1. lowercase
 *   2. replace runs of `[^a-z0-9]` with a single `_`
 *   3. trim leading/trailing `_`
 *   4. cap at [MAX_SLUG_LENGTH] characters; if truncation falls inside an alnum run,
 *      back off to the last `_` boundary so the slug always ends on a clean break
 *   5. if the result is empty (Unicode-only names, all-punctuation), fall back to
 *      `untitled-<first 8 chars of fallbackSeed>`
 *
 * Path-traversal defence: `.` and `/` are non-alphanumeric, so they collapse to `_`.
 * Documented loss: semantically meaningful punctuation (`F-22` → `f_22`, `Mk.II` → `mk_ii`).
 */
fun toSlug(name: String, fallbackSeed: String): String {
    val collapsed = buildString {
        var lastWasUnderscore = false
        for (c in name.lowercase()) {
            if (c.isAsciiAlnum()) {
                append(c)
                lastWasUnderscore = false
            } else if (!lastWasUnderscore) {
                append('_')
                lastWasUnderscore = true
            }
        }
    }.trim('_')

    if (collapsed.isEmpty()) {
        return FALLBACK_PREFIX + fallbackSeed.take(FALLBACK_SEED_TAKE)
    }

    if (collapsed.length <= MAX_SLUG_LENGTH) return collapsed

    val truncated = collapsed.substring(0, MAX_SLUG_LENGTH)
    val lastUnderscore = truncated.lastIndexOf('_')
    return if (lastUnderscore > 0) truncated.substring(0, lastUnderscore) else truncated
}

private fun Char.isAsciiAlnum(): Boolean = this in 'a'..'z' || this in '0'..'9'
