package jez.lastfleetprotocol.prototype.utils.export

import me.tatarka.inject.annotations.Inject

/**
 * Gate for surfacing dev-only tooling on consumer screens (e.g., the
 * "Scenario Builder (dev)" link on the landing screen). The gate's value is
 * fixed for the process lifetime — read once at VM init and frozen into
 * state, no live updating required.
 *
 * Today the default implementation [DevToolsGateImpl] wraps
 * [RepoExporter.isAvailable] (Item A's repo-root resolution gate). Future
 * dev tools that don't need repo-write can depend on `DevToolsGate`
 * directly and a different impl can swap in without restructuring the
 * consumer side.
 */
interface DevToolsGate {
    val isAvailable: Boolean
}

@Inject
class DevToolsGateImpl(
    private val repoExporter: RepoExporter,
) : DevToolsGate {
    override val isAvailable: Boolean
        get() = repoExporter.isAvailable
}
