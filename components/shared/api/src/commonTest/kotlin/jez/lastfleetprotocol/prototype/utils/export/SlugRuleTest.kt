package jez.lastfleetprotocol.prototype.utils.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SlugRuleTest {

    private val seed = "abcdef1234567890"

    // --- Happy path ---

    @Test
    fun simpleNameProducesSnakeCase() {
        assertEquals("heavy_cruiser", toSlug("Heavy Cruiser", seed))
    }

    @Test
    fun nameWithPunctuationCollapses() {
        assertEquals("player_ship", toSlug("Player Ship!", seed))
    }

    @Test
    fun leadingAndTrailingWhitespaceIsTrimmed() {
        assertEquals("trailing", toSlug("  trailing  ", seed))
    }

    // --- Lossy by design ---

    @Test
    fun hyphenInNameIsLost() {
        // Documented limitation: F-22 → f_22 (the hyphen is non-alphanumeric).
        assertEquals("f_22", toSlug("F-22", seed))
    }

    @Test
    fun periodInNameIsLost() {
        // Documented limitation: Mk.II → mk_ii.
        assertEquals("mk_ii", toSlug("Mk.II", seed))
    }

    // --- Length cap ---

    @Test
    fun longInputIsTruncatedAtCleanBoundary() {
        // 100 chars of alternating alnum and space; collapsed yields 50 underscore-separated
        // runs; cap of 64 should stop on a `_` boundary.
        val input = (1..100).joinToString(" ") { "x" }
        val result = toSlug(input, seed)
        assertTrue(result.length <= 64, "result length ${result.length} exceeds 64")
        assertTrue(!result.endsWith("_"), "result must not end on '_': '$result'")
    }

    @Test
    fun veryLongAlnumRunTruncatesAt64() {
        // No `_` boundary inside the truncation point — should truncate at 64.
        val input = "a".repeat(200)
        val result = toSlug(input, seed)
        assertEquals(64, result.length)
        assertEquals("a".repeat(64), result)
    }

    // --- Fallback ---

    @Test
    fun emptyNameUsesFallback() {
        assertEquals("untitled-abcdef12", toSlug("", seed))
    }

    @Test
    fun allNonAlnumNameUsesFallback() {
        assertEquals("untitled-abcdef12", toSlug("???", seed))
    }

    @Test
    fun unicodeOnlyNameUsesFallback() {
        // CJK / Hebrew / Cyrillic etc. — none match [a-z0-9] so they collapse entirely.
        assertEquals("untitled-abcdef12", toSlug("戦艦", seed))
    }

    @Test
    fun fallbackSeedShorterThan8IsUsedVerbatim() {
        assertEquals("untitled-abc", toSlug("???", "abc"))
    }

    // --- Path-traversal defence ---

    @Test
    fun parentDirectoryTraversalIsNeutralized() {
        // No `..` or `/` survives — collapses to underscores then trims.
        assertEquals("etc_passwd", toSlug("../etc/passwd", seed))
    }

    @Test
    fun absolutePathLikeNameIsNeutralized() {
        assertEquals("usr_local_bin", toSlug("/usr/local/bin", seed))
    }

    @Test
    fun backslashAndColonAreCollapsed() {
        // Windows-flavoured path separators.
        assertEquals("c_windows_system32", toSlug("C:\\Windows\\System32", seed))
    }

    // --- Slug rule version constant exposed ---

    @Test
    fun slugRuleVersionIsExposed() {
        assertEquals(1, SLUG_RULE_VERSION)
    }
}
