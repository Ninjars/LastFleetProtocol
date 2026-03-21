package jez.lastfleetprotocol

import com.pandulapeter.kubriko.helpers.extensions.deg
import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.rotateTowards
import kotlin.test.Test
import kotlin.test.assertEquals

class AngleTests {
    @Test
    fun rotateTowards1() {
        val maxDelta = 20f.deg.rad
        val start = 0f.deg.rad

        val output = start.rotateTowards(45f.deg.rad, maxDelta)
        assertEquals(20f.deg.rad.raw, output.raw)
    }

    @Test
    fun rotateTowards2() {
        val maxDelta = 20f.deg.rad
        val start = 0f.deg.rad

        val output = start.rotateTowards(-45f.deg.rad, maxDelta)
        assertEquals(-20f.deg.rad.raw, output.raw)
    }

    @Test
    fun rotateTowards3() {
        val maxDelta = 20f.deg.rad
        val start = 0f.deg.rad

        val output = start.rotateTowards(-125f.deg.rad, maxDelta)
        assertEquals(-20f.deg.rad.raw, output.raw)
    }

    @Test
    fun rotateTowards4() {
        val maxDelta = 20f.deg.rad
        val start = 0f.deg.rad

        val output = start.rotateTowards(-175f.deg.rad, maxDelta)
        assertEquals(-20f.deg.rad.raw, output.raw)
    }

    @Test
    fun rotateTowards5() {
        val maxDelta = 20f.deg.rad
        val start = 0f.deg.rad

        val output = start.rotateTowards(185f.deg.rad, maxDelta)
        assertEquals(-20f.deg.rad.raw, output.raw)
    }
}