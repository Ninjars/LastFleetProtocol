package jez.lastfleetprotocol.prototype.components.game.data

import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.data.ArmourStats
import jez.lastfleetprotocol.prototype.components.gamecore.data.CombatStats
import jez.lastfleetprotocol.prototype.components.gamecore.data.GunData
import jez.lastfleetprotocol.prototype.components.gamecore.data.HullDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.data.InternalSystemSpec
import jez.lastfleetprotocol.prototype.components.gamecore.data.InternalSystemType
import jez.lastfleetprotocol.prototype.components.gamecore.data.MovementConfig
import jez.lastfleetprotocol.prototype.components.gamecore.data.ProjectileStats
import jez.lastfleetprotocol.prototype.components.gamecore.data.ShipConfig
import jez.lastfleetprotocol.prototype.components.gamecore.data.TurretConfig
import lastfleetprotocol.components.design.generated.resources.Res
import lastfleetprotocol.components.design.generated.resources.ship_enemy_1
import lastfleetprotocol.components.design.generated.resources.ship_player_1
import lastfleetprotocol.components.design.generated.resources.turret_simple_1

/**
 * Pre-defined demo scenario configuration.
 * All stat values for the combat vertical slice are defined here.
 */
object DemoScenarioConfig {

    // -- Hull definitions --
    // Vertices approximate sprite silhouettes as convex polygons.
    // Coordinates are relative to center, in scene units matching pixel scale.
    // Forward = +X axis (atan2/Kubriko convention). Y axis is perpendicular (port/starboard).

    private val playerHull = HullDefinition(
        vertices = listOf(
            SceneOffset(56f.sceneUnit, 0f.sceneUnit),       // nose (forward)
            SceneOffset((-30f).sceneUnit, 37f.sceneUnit),   // starboard wing
            SceneOffset((-56f).sceneUnit, 20f.sceneUnit),   // starboard rear
            SceneOffset((-56f).sceneUnit, (-20f).sceneUnit), // port rear
            SceneOffset((-30f).sceneUnit, (-37f).sceneUnit), // port wing
        ),
        armour = ArmourStats(hardness = 5f, density = 2f),
        mass = 50f,
    )

    private val enemyHullLight = HullDefinition(
        vertices = listOf(
            SceneOffset(42f.sceneUnit, 0f.sceneUnit),
            SceneOffset((-20f).sceneUnit, 30f.sceneUnit),
            SceneOffset((-42f).sceneUnit, 15f.sceneUnit),
            SceneOffset((-42f).sceneUnit, (-15f).sceneUnit),
            SceneOffset((-20f).sceneUnit, (-30f).sceneUnit),
        ),
        armour = ArmourStats(hardness = 3f, density = 1f),
        mass = 30f,
    )

    private val enemyHullMedium = HullDefinition(
        vertices = listOf(
            SceneOffset(52f.sceneUnit, 0f.sceneUnit),
            SceneOffset((-25f).sceneUnit, 42f.sceneUnit),
            SceneOffset((-52f).sceneUnit, 25f.sceneUnit),
            SceneOffset((-52f).sceneUnit, (-25f).sceneUnit),
            SceneOffset((-25f).sceneUnit, (-42f).sceneUnit),
        ),
        armour = ArmourStats(hardness = 5f, density = 2f),
        mass = 50f,
    )

    private val enemyHullHeavy = HullDefinition(
        vertices = listOf(
            SceneOffset(52f.sceneUnit, 0f.sceneUnit),
            SceneOffset((-20f).sceneUnit, 50f.sceneUnit),
            SceneOffset((-52f).sceneUnit, 35f.sceneUnit),
            SceneOffset((-52f).sceneUnit, (-35f).sceneUnit),
            SceneOffset((-20f).sceneUnit, (-50f).sceneUnit),
        ),
        armour = ArmourStats(hardness = 8f, density = 4f),
        mass = 100f,
    )

    // -- Standard internal system sets --

    private val playerSystems = listOf(
        InternalSystemSpec(InternalSystemType.REACTOR, maxHp = 100f, density = 8f, mass = 20f),
        InternalSystemSpec(InternalSystemType.MAIN_ENGINE, maxHp = 80f, density = 4f, mass = 15f),
        InternalSystemSpec(InternalSystemType.BRIDGE, maxHp = 60f, density = 3f, mass = 10f),
    )

    private val enemySystemsLight = listOf(
        InternalSystemSpec(InternalSystemType.REACTOR, maxHp = 60f, density = 5f, mass = 10f),
        InternalSystemSpec(InternalSystemType.MAIN_ENGINE, maxHp = 50f, density = 3f, mass = 8f),
        InternalSystemSpec(InternalSystemType.BRIDGE, maxHp = 40f, density = 2f, mass = 5f),
    )

    private val enemySystemsMedium = listOf(
        InternalSystemSpec(InternalSystemType.REACTOR, maxHp = 80f, density = 6f, mass = 15f),
        InternalSystemSpec(InternalSystemType.MAIN_ENGINE, maxHp = 70f, density = 4f, mass = 12f),
        InternalSystemSpec(InternalSystemType.BRIDGE, maxHp = 50f, density = 3f, mass = 8f),
    )

    private val enemySystemsHeavy = listOf(
        InternalSystemSpec(InternalSystemType.REACTOR, maxHp = 150f, density = 10f, mass = 30f),
        InternalSystemSpec(InternalSystemType.MAIN_ENGINE, maxHp = 100f, density = 5f, mass = 20f),
        InternalSystemSpec(InternalSystemType.BRIDGE, maxHp = 80f, density = 4f, mass = 15f),
    )

    // -- Turret shared constants --
    private const val TURRET_PIVOT_X = 32f
    private const val TURRET_PIVOT_Y = 32f

    // -- Weapon configs --

    private val standardProjectile = ProjectileStats(
        damage = 15f,
        armourPiercing = 6f,
        toHitModifier = 0.15f,
        speed = 200f,
        lifetimeMs = 4000,
    )

    private val lightProjectile = ProjectileStats(
        damage = 8f,
        armourPiercing = 3f,
        toHitModifier = 0.2f,
        speed = 250f,
        lifetimeMs = 3000,
    )

    private val heavyProjectile = ProjectileStats(
        damage = 30f,
        armourPiercing = 12f,
        toHitModifier = 0.05f,
        speed = 150f,
        lifetimeMs = 5000,
    )

    private fun standardTurretConfig(offsetY: Float) = TurretConfig(
        offsetX = 0f,
        offsetY = offsetY,
        pivotX = TURRET_PIVOT_X,
        pivotY = TURRET_PIVOT_Y,
        gunData = GunData(
            drawable = Res.drawable.turret_simple_1,
            projectileStats = standardProjectile,
            aimTolerance = AngleRadians.TwoPi / 1440f,
            magazineCapacity = Int.MAX_VALUE,
            reloadMilliseconds = 2000,
            shotsPerBurst = 3,
            burstCycleMilliseconds = 100,
            cycleMilliseconds = 700,
        ),
    )

    private fun lightTurretConfig(offsetY: Float) = TurretConfig(
        offsetX = 0f,
        offsetY = offsetY,
        pivotX = TURRET_PIVOT_X,
        pivotY = TURRET_PIVOT_Y,
        gunData = GunData(
            drawable = Res.drawable.turret_simple_1,
            projectileStats = lightProjectile,
            aimTolerance = AngleRadians.TwoPi / 1440f,
            magazineCapacity = Int.MAX_VALUE,
            reloadMilliseconds = 1500,
            shotsPerBurst = 5,
            burstCycleMilliseconds = 80,
            cycleMilliseconds = 400,
        ),
    )

    private fun heavyTurretConfig(offsetY: Float) = TurretConfig(
        offsetX = 0f,
        offsetY = offsetY,
        pivotX = TURRET_PIVOT_X,
        pivotY = TURRET_PIVOT_Y,
        gunData = GunData(
            drawable = Res.drawable.turret_simple_1,
            projectileStats = heavyProjectile,
            aimTolerance = AngleRadians.TwoPi / 1440f,
            magazineCapacity = Int.MAX_VALUE,
            reloadMilliseconds = 3000,
            shotsPerBurst = 1,
            burstCycleMilliseconds = 0,
            cycleMilliseconds = 1500,
        ),
    )

    // -- Ship configs --

    val playerShipConfig = ShipConfig(
        drawable = Res.drawable.ship_player_1,
        hull = playerHull,
        combatStats = CombatStats(evasionModifier = 0.1f),
        movementConfig = MovementConfig(
            forwardThrust = 1200f,
            lateralThrust = 500f,
            reverseThrust = 500f,
            angularThrust = 300f,
        ),
        internalSystems = playerSystems,
        turretConfigs = listOf(
            standardTurretConfig(offsetY = -45f),
            standardTurretConfig(offsetY = 45f),
        ),
    )

    val enemyShipLightConfig = ShipConfig(
        drawable = Res.drawable.ship_enemy_1,
        hull = enemyHullLight,
        combatStats = CombatStats(evasionModifier = 0.2f),
        movementConfig = MovementConfig(
            forwardThrust = 1000f,
            lateralThrust = 300f,
            reverseThrust = 400f,
            angularThrust = 200f,
        ),
        internalSystems = enemySystemsLight,
        turretConfigs = listOf(
            lightTurretConfig(offsetY = -30f),
            lightTurretConfig(offsetY = 30f),
        ),
    )

    val enemyShipMediumConfig = ShipConfig(
        drawable = Res.drawable.ship_enemy_1,
        hull = enemyHullMedium,
        combatStats = CombatStats(evasionModifier = 0.1f),
        movementConfig = MovementConfig(
            forwardThrust = 700f,
            lateralThrust = 180f,
            reverseThrust = 250f,
            angularThrust = 120f,
        ),
        internalSystems = enemySystemsMedium,
        turretConfigs = listOf(
            standardTurretConfig(offsetY = -40f),
            standardTurretConfig(offsetY = 40f),
        ),
    )

    val enemyShipHeavyConfig = ShipConfig(
        drawable = Res.drawable.ship_enemy_1,
        hull = enemyHullHeavy,
        combatStats = CombatStats(evasionModifier = 0.0f),
        movementConfig = MovementConfig(
            forwardThrust = 500f,
            lateralThrust = 100f,
            reverseThrust = 150f,
            angularThrust = 60f,
        ),
        internalSystems = enemySystemsHeavy,
        turretConfigs = listOf(
            heavyTurretConfig(offsetY = -45f),
            heavyTurretConfig(offsetY = 45f),
        ),
    )
}
