package jez.lastfleetprotocol.prototype.components.gamecore.data

import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectileStatsSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun legacyJson_deserialisesUnchanged_dragFieldsDefaultToZero() {
        val legacyJson = """
            {
              "damage": 10.0,
              "armourPiercing": 50.0,
              "toHitModifier": 1.0,
              "speed": 200.0,
              "lifetimeMs": 4000
            }
        """.trimIndent()

        val stats = json.decodeFromString(ProjectileStats.serializer(), legacyJson)

        assertEquals(10f, stats.damage)
        assertEquals(50f, stats.armourPiercing)
        assertEquals(200f, stats.speed)
        assertEquals(4000, stats.lifetimeMs)
        assertEquals(0f, stats.dragK, "dragK must default to 0 when absent in JSON")
        assertEquals(0f, stats.expirationVelocityFraction, "expirationVelocityFraction must default to 0 when absent")
    }

    @Test
    fun newFieldsRoundTrip() {
        val original = ProjectileStats(
            damage = 12f,
            armourPiercing = 80f,
            toHitModifier = 1.0f,
            speed = 600f,
            lifetimeMs = 30_000,
            dragK = 0.12f,
            expirationVelocityFraction = 0.3f,
        )
        val encoded = json.encodeToString(ProjectileStats.serializer(), original)
        val decoded = json.decodeFromString(ProjectileStats.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun effectiveRangeM_withDragUsesIntegratedFormula() {
        val stats = ProjectileStats(
            damage = 10f, armourPiercing = 50f, toHitModifier = 1f,
            speed = 600f, lifetimeMs = 30_000,
            dragK = 0.6f,
            expirationVelocityFraction = 0.3f,
        )
        // (600 / 0.6) * (1 - 0.3) = 1000 * 0.7 = 700 m
        val range = stats.effectiveRangeM()
        assertTrue(abs(range - 700f) < 1e-3f, "expected ~700m, got $range")
    }

    @Test
    fun effectiveRangeM_legacyFallback_usesLifetimeProduct() {
        val stats = ProjectileStats(
            damage = 10f, armourPiercing = 50f, toHitModifier = 1f,
            speed = 200f, lifetimeMs = 4000,
            dragK = 0f, expirationVelocityFraction = 0f,
        )
        // 200 * 4000 / 1000 = 800 m
        assertEquals(800f, stats.effectiveRangeM())
    }

    @Test
    fun effectiveRangeM_dragKWithoutExpirationFraction_fallsBackToLegacy() {
        // Defensive: both drag fields must be set to enable drag-aware range.
        val stats = ProjectileStats(
            damage = 10f, armourPiercing = 50f, toHitModifier = 1f,
            speed = 600f, lifetimeMs = 30_000,
            dragK = 0.6f,
            expirationVelocityFraction = 0f,
        )
        // Legacy: 600 * 30000 / 1000 = 18000 m
        assertEquals(18000f, stats.effectiveRangeM())
    }

    @Test
    fun speedAt_zeroDragReturnsMuzzleSpeed() {
        val stats = ProjectileStats(10f, 50f, 1f, 600f, 30_000, dragK = 0f)
        assertEquals(600f, stats.speedAt(5f))
    }

    @Test
    fun speedAt_decaysExponentially() {
        val stats = ProjectileStats(10f, 50f, 1f, 600f, 30_000, dragK = 0.6f)
        // After 1 s: 600 * exp(-0.6) ≈ 329.29
        val v = stats.speedAt(1f)
        assertTrue(abs(v - 329.29f) < 0.5f, "expected ~329.3 m/s, got $v")
    }

    @Test
    fun timeToExpirationVelocity_matchesDragMath() {
        val stats = ProjectileStats(
            damage = 10f, armourPiercing = 50f, toHitModifier = 1f,
            speed = 600f, lifetimeMs = 30_000,
            dragK = 0.6f, expirationVelocityFraction = 0.3f,
        )
        // -ln(0.3) / 0.6 ≈ 2.007 s
        val t = stats.timeToExpirationVelocity()
        assertTrue(abs(t - 2.007f) < 1e-2f, "expected ~2.007 s, got $t")
    }

    @Test
    fun timeToExpirationVelocity_zeroDragIsInfinite() {
        val stats = ProjectileStats(10f, 50f, 1f, 600f, 30_000, dragK = 0f)
        assertTrue(stats.timeToExpirationVelocity().isInfinite())
    }
}
