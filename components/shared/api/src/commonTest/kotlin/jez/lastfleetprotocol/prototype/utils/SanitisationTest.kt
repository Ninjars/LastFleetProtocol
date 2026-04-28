package jez.lastfleetprotocol.prototype.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class SanitisationTest {

    @Test
    fun preservesAlphanumericAndSpacesAndCase() {
        assertEquals("Heavy Cruiser", sanitizeFilenameStem("Heavy Cruiser"))
    }

    @Test
    fun replacesPunctuationWithUnderscore() {
        assertEquals("Mk_II", sanitizeFilenameStem("Mk.II"))
    }

    @Test
    fun replacesPathTraversalSeparatorsWithUnderscore() {
        assertEquals("a_b_c_d", sanitizeFilenameStem("a/b\\c:d"))
    }

    @Test
    fun emptyStringIsUnchanged() {
        assertEquals("", sanitizeFilenameStem(""))
    }

    @Test
    fun preservesUnderscoreAndHyphenAndSpaceOnly() {
        assertEquals("_- _", sanitizeFilenameStem("_- _"))
    }
}
