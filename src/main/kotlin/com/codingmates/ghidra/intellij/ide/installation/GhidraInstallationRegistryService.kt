package com.codingmates.ghidra.intellij.ide.installation

import com.codingmates.ghidra.intellij.ide.extension.GhidraSkeletonTemplateDiscovery
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.nio.file.Files
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
     * Returns the configured Gradle executable, if one exists.
     */
    fun gradleExecutablePath(): Path? =
        state.gradleExecutablePath
            .takeIf(String::isNotBlank)
            ?.let(Path::of)

    /**
     * Sets or clears the optional Gradle executable used when projects do not contain a wrapper.
     */
    fun setGradleExecutablePath(executablePath: Path?) {
        if (executablePath == null) {
            state.gradleExecutablePath = ""
            return
        }

        val normalizedPath = executablePath.toAbsolutePath().normalize()

        require(Files.isRegularFile(normalizedPath)) {
            "Gradle executable does not exist: $normalizedPath"
        }

        require(Files.isExecutable(normalizedPath)) {
            "Gradle executable is not executable: $normalizedPath"
        }

        state.gradleExecutablePath = normalizedPath.toString()
    }

    /**
     * Returns the configured official Ghidra Skeleton template path, if one exists.
     */
    fun skeletonTemplatePath(): Path? =
        state.skeletonTemplatePath
            .takeIf(String::isNotBlank)
            ?.let(Path::of)

    /**
     * Sets or clears the optional official Ghidra Skeleton template path.
     */
    fun setSkeletonTemplatePath(configuredPath: Path?) {
        if (configuredPath == null) {
            state.skeletonTemplatePath = ""
            return
        }

        val template = GhidraSkeletonTemplateDiscovery.discover(configuredPath)
        state.skeletonTemplatePath = template.configuredPath.toString()
    }

    /**
     * Replaces all registered installations with [configuredPaths].
     *
     * Duplicate paths resolving to the same canonical installation are collapsed. If
     * [defaultConfiguredPath] is null, the first registered installation becomes the default.
     */
    fun replaceAll(
        configuredPaths: List<Path>,
        defaultConfiguredPath: Path?,
    ) {
        val discoveredByCanonicalPath = linkedMapOf<String, GhidraInstallation>()

        configuredPaths.forEach { configuredPath ->
            val installation = GhidraInstallationDiscovery.discover(configuredPath)
            discoveredByCanonicalPath.putIfAbsent(
                installation.canonicalPath.toString(),
                installation,
            )
        }

        val defaultCanonicalPath = when {
            defaultConfiguredPath == null ->
                discoveredByCanonicalPath.keys.firstOrNull().orEmpty()

            else -> {
                val defaultInstallation =
                    GhidraInstallationDiscovery.discover(defaultConfiguredPath)
                val canonicalPath = defaultInstallation.canonicalPath.toString()

                require(discoveredByCanonicalPath.containsKey(canonicalPath)) {
                    "Cannot select an unregistered Ghidra installation as default: $canonicalPath"
                }

                canonicalPath
            }
        }

        val existingSkeletonTemplatePath = state.skeletonTemplatePath
        val existingGradleExecutablePath = state.gradleExecutablePath

        state = GhidraInstallationRegistryState().apply {
            this.defaultCanonicalPath = defaultCanonicalPath
            this.skeletonTemplatePath = existingSkeletonTemplatePath
            this.gradleExecutablePath = existingGradleExecutablePath
            installations = discoveredByCanonicalPath.values.map { installation ->
                RegisteredGhidraInstallationState(
                    configuredPath = installation.configuredPath.toString(),
                    canonicalPath = installation.canonicalPath.toString(),
                )
            }.toMutableList()
        }
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
    var gradleExecutablePath: String = ""
    var skeletonTemplatePath: String = ""
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