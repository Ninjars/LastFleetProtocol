package jez.lastfleetprotocol.prototype.components.gamecore.shipdesign

import com.pandulapeter.kubriko.helpers.extensions.cos
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.helpers.extensions.sin
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.data.ArmourStats
import jez.lastfleetprotocol.prototype.components.gamecore.data.CombatStats
import jez.lastfleetprotocol.prototype.components.gamecore.data.GunData
import jez.lastfleetprotocol.prototype.components.gamecore.data.HullDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.data.InternalSystemSpec
import jez.lastfleetprotocol.prototype.components.gamecore.data.InternalSystemType
import jez.lastfleetprotocol.prototype.components.gamecore.data.MovementConfig
import jez.lastfleetprotocol.prototype.components.gamecore.data.ShipConfig
import jez.lastfleetprotocol.prototype.components.gamecore.data.TurretConfig
import jez.lastfleetprotocol.prototype.components.gamecore.stats.calculateShipStats

/**
 * Converts a [ShipDesign] to a runtime [ShipConfig].
 *
 * This is a pure function with no side effects. All required data (item definitions,
 * turret gun specs) must be provided as parameters. Failures are returned as
 * descriptive [Result.failure] values, not thrown exceptions.
 *
 * Constraints enforced:
 * - A [ShipDesign.placedKeel] is required (Slice B: exactly-one Keel per ship).
 * - At most one module per [InternalSystemType] (multi-module-per-type rejected in v1).
 * - All module `systemType` strings must map to a known [InternalSystemType] enum value.
 * - All turret `turretConfigId` values must resolve in the provided [turretGuns] map.
 *
 * Callers that process many designs should destructure the returned `Result` rather
 * than calling `.getOrThrow()` — the combat-load path uses a Result-loop so a single
 * failure doesn't abort the whole scene setup. See Slice B plan Unit 4.
 */
fun convertShipDesign(
    design: ShipDesign,
    turretGuns: Map<String, GunData>,
    defaultEvasionModifier: Float = 1.0f,
): Result<ShipConfig> {
    val itemDefsById = design.itemDefinitions.associateBy { it.id }

    // --- Keel conversion (required) ---
    val placedKeel = design.placedKeel
        ?: return Result.failure(
            IllegalArgumentException(
                "Ship design '${design.name}' has no Keel — every ship requires exactly one"
            )
        )
    val keelItemDef = itemDefsById[placedKeel.itemDefinitionId]
        ?: return Result.failure(
            IllegalArgumentException(
                "Keel '${placedKeel.id}' references unknown item definition '${placedKeel.itemDefinitionId}'"
            )
        )
    val keelAttrs = keelItemDef.attributes as? ItemAttributes.KeelAttributes
        ?: return Result.failure(
            IllegalArgumentException(
                "Item definition '${placedKeel.itemDefinitionId}' is not a keel type"
            )
        )

    val hulls = mutableListOf<HullDefinition>()

    // Emit the Keel as a hull piece — it participates in geometry, collision, and drag
    // like any other placed hull piece.
    hulls.add(
        HullDefinition(
            vertices = keelItemDef.vertices.map { transformVertex(it, placedKeel) },
            armour = ArmourStats(
                hardness = keelAttrs.armour.hardness,
                density = keelAttrs.armour.density,
            ),
            mass = keelAttrs.mass,
        )
    )

    // --- Additional hull pieces (optional post-Keel) ---
    for (placed in design.placedHulls) {
        val itemDef = itemDefsById[placed.itemDefinitionId]
            ?: return Result.failure(
                IllegalArgumentException(
                    "Hull piece '${placed.id}' references unknown item definition '${placed.itemDefinitionId}'"
                )
            )
        val attrs = itemDef.attributes as? ItemAttributes.HullAttributes
            ?: return Result.failure(
                IllegalArgumentException(
                    "Item definition '${placed.itemDefinitionId}' is not a hull type"
                )
            )

        val transformedVertices = itemDef.vertices.map { vertex ->
            transformVertex(vertex, placed)
        }

        hulls.add(
            HullDefinition(
                vertices = transformedVertices,
                armour = ArmourStats(
                    hardness = attrs.armour.hardness,
                    density = attrs.armour.density,
                ),
                mass = attrs.mass,
            )
        )
    }

    // --- Module conversion ---
    val systemTypeGroups = design.placedModules.groupBy { it.systemType }
    val internalSystems = mutableListOf<InternalSystemSpec>()

    // Slice B: Keel is tracked as an internal system (KEEL) with its own HP. Damage
    // routing in Unit 3 promotes it to the side-arc primary.
    internalSystems.add(
        InternalSystemSpec(
            type = InternalSystemType.KEEL,
            maxHp = keelAttrs.maxHp,
            density = keelAttrs.armour.density,
            mass = keelAttrs.mass,
        )
    )

    for ((typeString, modules) in systemTypeGroups) {
        if (modules.size > 1) {
            return Result.failure(
                IllegalArgumentException(
                    "Ship design '${design.name}' has ${modules.size} modules of type '$typeString' — only one per type is allowed in v1"
                )
            )
        }

        val enumType = try {
            InternalSystemType.valueOf(typeString)
        } catch (_: IllegalArgumentException) {
            return Result.failure(
                IllegalArgumentException(
                    "Unknown module systemType '$typeString' in ship design '${design.name}'"
                )
            )
        }

        val placed = modules.first()
        val itemDef = itemDefsById[placed.itemDefinitionId]
        val attrs = itemDef?.attributes as? ItemAttributes.ModuleAttributes

        if (attrs != null) {
            internalSystems.add(
                InternalSystemSpec(
                    type = enumType,
                    maxHp = attrs.maxHp,
                    density = attrs.density,
                    mass = attrs.mass,
                )
            )
        } else {
            // Legacy module without an ItemDefinition — use fallback values from stats core
            // (the stats core handles this case internally; the converter just needs valid specs)
            internalSystems.add(
                InternalSystemSpec(
                    type = enumType,
                    maxHp = LEGACY_MODULE_HP[enumType] ?: 50f,
                    density = 5f,
                    mass = LEGACY_MODULE_MASS[typeString] ?: 10f,
                )
            )
        }
    }

    // --- Turret conversion ---
    val turretConfigs = mutableListOf<TurretConfig>()
    for (placed in design.placedTurrets) {
        val gunData = turretGuns[placed.turretConfigId]
            ?: return Result.failure(
                IllegalArgumentException(
                    "Turret '${placed.id}' references unknown turretConfigId '${placed.turretConfigId}'"
                )
            )

        // Derive offset from the placed position (ship-space coordinates)
        turretConfigs.add(
            TurretConfig(
                offsetX = placed.position.x.raw,
                offsetY = placed.position.y.raw,
                // Pivot at turret centre — derived from item definition vertices if available,
                // otherwise use a sensible default
                pivotX = computeTurretPivotX(itemDefsById[placed.itemDefinitionId]),
                pivotY = computeTurretPivotY(itemDefsById[placed.itemDefinitionId]),
                gunData = gunData,
            )
        )
    }

    // --- Movement config via shared stats core ---
    val stats = calculateShipStats(
        placedHulls = design.placedHulls,
        placedModules = design.placedModules,
        placedTurrets = design.placedTurrets,
        placedKeel = design.placedKeel,
        resolveItem = { itemDefsById[it] },
    )

    val movementConfig = MovementConfig(
        forwardThrust = stats.forwardThrust,
        lateralThrust = stats.lateralThrust,
        reverseThrust = stats.reverseThrust,
        angularThrust = stats.angularThrust,
        forwardDragCoeff = stats.forwardDragCoeff,
        lateralDragCoeff = stats.lateralDragCoeff,
        reverseDragCoeff = stats.reverseDragCoeff,
    )

    return Result.success(
        ShipConfig(
            hulls = hulls,
            combatStats = CombatStats(evasionModifier = defaultEvasionModifier),
            movementConfig = movementConfig,
            internalSystems = internalSystems,
            turretConfigs = turretConfigs,
        )
    )
}

/**
 * Apply a placed item's transform (mirror, rotation, position) to a vertex.
 */
private fun transformVertex(vertex: SceneOffset, placed: PlacedItem): SceneOffset {
    // 1. Apply mirror
    var x = vertex.x.raw
    var y = vertex.y.raw
    if (placed.mirrorX) x = -x
    if (placed.mirrorY) y = -y

    // 2. Apply rotation
    val cos = placed.rotation.cos
    val sin = placed.rotation.sin
    val rotX = x * cos - y * sin
    val rotY = x * sin + y * cos

    // 3. Apply position offset
    return SceneOffset(
        (rotX + placed.position.x.raw).sceneUnit,
        (rotY + placed.position.y.raw).sceneUnit,
    )
}

/**
 * Compute turret pivot X from item definition vertices (centroid X), or a default.
 */
private fun computeTurretPivotX(itemDef: ItemDefinition?): Float {
    val verts = itemDef?.vertices ?: return DEFAULT_TURRET_PIVOT
    if (verts.isEmpty()) return DEFAULT_TURRET_PIVOT
    return verts.map { it.x.raw }.average().toFloat()
}

private fun computeTurretPivotY(itemDef: ItemDefinition?): Float {
    val verts = itemDef?.vertices ?: return DEFAULT_TURRET_PIVOT
    if (verts.isEmpty()) return DEFAULT_TURRET_PIVOT
    return verts.map { it.y.raw }.average().toFloat()
}

private const val DEFAULT_TURRET_PIVOT = 16f

// Legacy module fallback values (matching ShipStatsCore's legacy path)
private val LEGACY_MODULE_MASS = mapOf(
    "REACTOR" to 20f,
    "MAIN_ENGINE" to 15f,
    "BRIDGE" to 10f,
)
private val LEGACY_MODULE_HP = mapOf(
    InternalSystemType.REACTOR to 100f,
    InternalSystemType.MAIN_ENGINE to 80f,
    InternalSystemType.BRIDGE to 60f,
)
