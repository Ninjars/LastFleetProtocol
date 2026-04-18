package jez.lastfleetprotocol.prototype.components.shipbuilder.ui.composables

import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemAttributes
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.SerializableArmourStats
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Slice B Unit 7 (extends Slice A Unit 6): verifies [isValidForSave] for each
 * [ItemAttributes] variant. The rule is a builder-UI invariant stricter than the
 * data model — the data model accepts zero drag / zero lift as valid construction,
 * but the Finish button is gated on this function so unusable items can't be saved.
 */
class ItemAttributesValidationTest {

    private val validArmour = SerializableArmourStats(hardness = 5f, density = 2f)

    // --- Hull (Slice A Unit 6) ---

    @Test
    fun hull_withAllPositiveDragModifiers_isValid() {
        val hull = ItemAttributes.HullAttributes(
            armour = validArmour,
            sizeCategory = "medium",
            mass = 50f,
            forwardDragModifier = 1.0f,
            lateralDragModifier = 1.0f,
            reverseDragModifier = 1.0f,
        )
        assertTrue(hull.isValidForSave())
    }

    @Test
    fun hull_withZeroForwardDrag_isInvalid() {
        val hull = ItemAttributes.HullAttributes(
            armour = validArmour,
            sizeCategory = "medium",
            mass = 50f,
            forwardDragModifier = 0f,
        )
        assertFalse(hull.isValidForSave())
    }

    // --- Keel (Slice B Unit 7) ---

    @Test
    fun keel_withAllPositiveFields_isValid() {
        val keel = ItemAttributes.KeelAttributes(
            armour = validArmour,
            sizeCategory = "medium",
            mass = 40f,
            maxHp = 150f,
            lift = 200f,
            shipClass = "fighter",
        )
        assertTrue(keel.isValidForSave())
    }

    @Test
    fun keel_withZeroLift_isInvalid() {
        // A Keel with no lift is always unflightworthy — can't ship a design using it.
        val keel = ItemAttributes.KeelAttributes(
            armour = validArmour,
            sizeCategory = "medium",
            mass = 40f,
            lift = 0f,
        )
        assertFalse(keel.isValidForSave())
    }

    @Test
    fun keel_withZeroMaxHp_isInvalid() {
        // A Keel with 0 HP would be instantly destroyed at spawn, yielding a
        // LiftFailed ship in the demo scene. Block at save time.
        val keel = ItemAttributes.KeelAttributes(
            armour = validArmour,
            sizeCategory = "medium",
            mass = 40f,
            lift = 200f,
            maxHp = 0f,
        )
        assertFalse(keel.isValidForSave())
    }

    @Test
    fun keel_withZeroDragModifier_isInvalid() {
        val keel = ItemAttributes.KeelAttributes(
            armour = validArmour,
            sizeCategory = "medium",
            mass = 40f,
            lift = 200f,
            maxHp = 150f,
            forwardDragModifier = 0f,
        )
        assertFalse(keel.isValidForSave())
    }

    @Test
    fun keel_withEmptyShipClass_isStillValid() {
        // shipClass is a free-form label in Slice B — an empty string is
        // harmless; don't reject the save on it.
        val keel = ItemAttributes.KeelAttributes(
            armour = validArmour,
            sizeCategory = "medium",
            mass = 40f,
            lift = 200f,
            maxHp = 150f,
            shipClass = "",
        )
        assertTrue(keel.isValidForSave())
    }

    // --- Module / Turret: no strict validation today ---

    @Test
    fun module_returnsTrue_noValidationDefined() {
        val module = ItemAttributes.ModuleAttributes(
            systemType = "REACTOR",
            maxHp = 100f, density = 8f, mass = 20f,
        )
        assertTrue(module.isValidForSave())
    }

    @Test
    fun turret_returnsTrue_noValidationDefined() {
        val turret = ItemAttributes.TurretAttributes(
            sizeCategory = "medium",
            mass = 10f,
        )
        assertTrue(turret.isValidForSave())
    }
}
