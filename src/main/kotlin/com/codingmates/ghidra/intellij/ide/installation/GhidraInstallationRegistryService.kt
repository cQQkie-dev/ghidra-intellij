package com.codingmates.ghidra.intellij.ide.installation

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.nio.file.Path

/**
 * Application-level registry of Ghidra runtime installations.
 *
 * Paths selected by users are persisted independently of projects. Projects will later select
 * one registered installation as their target.
 */
@Service(Service.Level.APP)
@State(
    name = "GhidraInstallationRegistry",
    storages = [Storage("ghidra-installations.xml")]
)
class GhidraInstallationRegistryService :
    PersistentStateComponent<GhidraInstallationRegistryState> {

    private var state = GhidraInstallationRegistryState()

    override fun getState(): GhidraInstallationRegistryState = state

    override fun loadState(state: GhidraInstallationRegistryState) {
        this.state = state
    }

    /**
     * Registers [configuredPath] after validating and resolving it as a Ghidra installation.
     *
     * If an equivalent canonical installation is already registered, its configured path is
     * updated instead of adding a duplicate entry.
     */
    fun register(
        configuredPath: Path,
        makeDefault: Boolean = state.installations.isEmpty(),
    ): GhidraInstallation {
        val installation = GhidraInstallationDiscovery.discover(configuredPath)
        val canonicalPath = installation.canonicalPath.toString()
        val configuredPathString = installation.configuredPath.toString()

        val existing = state.installations.firstOrNull {
            it.canonicalPath == canonicalPath
        }

        if (existing != null) {
            existing.configuredPath = configuredPathString
        }
        else {
            state.installations.add(
                RegisteredGhidraInstallationState(
                    configuredPath = configuredPathString,
                    canonicalPath = canonicalPath,
                )
            )
        }

        if (makeDefault || state.defaultCanonicalPath.isBlank()) {
            state.defaultCanonicalPath = canonicalPath
        }

        return installation
    }

    /**
     * Removes the installation identified by its canonical path.
     */
    fun unregister(canonicalPath: Path) {
        val canonicalPathString = canonicalPath.toAbsolutePath().normalize().toString()

        state.installations.removeIf {
            it.canonicalPath == canonicalPathString
        }

        if (state.defaultCanonicalPath == canonicalPathString) {
            state.defaultCanonicalPath =
                state.installations.firstOrNull()?.canonicalPath.orEmpty()
        }
    }

    /**
     * Makes an already registered installation the default for newly created projects.
     */
    fun setDefault(canonicalPath: Path) {
        val canonicalPathString = canonicalPath.toAbsolutePath().normalize().toString()

        require(state.installations.any { it.canonicalPath == canonicalPathString }) {
            "Cannot select an unregistered Ghidra installation as default: $canonicalPathString"
        }

        state.defaultCanonicalPath = canonicalPathString
    }

    /**
     * Returns the user-configured paths in registration order.
     */
    fun configuredPaths(): List<Path> =
        state.installations.map { Path.of(it.configuredPath) }

    /**
     * Returns the configured path for the default installation, if one exists.
     */
    fun defaultConfiguredPath(): Path? {
        val defaultCanonicalPath = state.defaultCanonicalPath
        if (defaultCanonicalPath.isBlank()) {
            return null
        }

        return state.installations
            .firstOrNull { it.canonicalPath == defaultCanonicalPath }
            ?.configuredPath
            ?.let(Path::of)
    }
}

/**
 * Serializable persistent state for [GhidraInstallationRegistryService].
 */
class GhidraInstallationRegistryState {
    var defaultCanonicalPath: String = ""
    var installations: MutableList<RegisteredGhidraInstallationState> = mutableListOf()
}

/**
 * Serializable persistent entry for one registered Ghidra installation.
 */
class RegisteredGhidraInstallationState {
    var configuredPath: String = ""
    var canonicalPath: String = ""

    constructor()

    constructor(
        configuredPath: String,
        canonicalPath: String,
    ) {
        this.configuredPath = configuredPath
        this.canonicalPath = canonicalPath
    }
}