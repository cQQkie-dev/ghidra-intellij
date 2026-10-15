package com.codingmates.ghidra.intellij.ide.runConfiguration

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

object PyGhidraPythonDebugSupport {

    const val PYTHON_CORE_PLUGIN_ID = "PythonCore"
    const val PYTHONPATH_ENVIRONMENT_VARIABLE = "PYTHONPATH"

    fun pydevHelperDirectory(): Path {
        val pluginId = PluginId.getId(PYTHON_CORE_PLUGIN_ID)

        val descriptor = PluginManagerCore.getPlugin(pluginId)
            ?: error(
                "IntelliJ Python Community Edition support is not installed. " +
                    "Install and enable the Python plug-in before preparing PyGhidra Python debugging."
            )

        if (!descriptor.isEnabled) {
            error(
                "IntelliJ Python Community Edition support is disabled. " +
                    "Enable the Python plug-in before preparing PyGhidra Python debugging."
            )
        }

        val helperDirectory = descriptor.pluginPath
            .resolve("helpers")
            .resolve("pydev")
            .toAbsolutePath()
            .normalize()

        val pydevd = helperDirectory.resolve("pydevd.py")
        val pydevdPycharm = helperDirectory.resolve("pydevd_pycharm.py")

        require(Files.isRegularFile(pydevd)) {
            "The IntelliJ Python debugger helper is missing:\n$pydevd"
        }

        require(Files.isRegularFile(pydevdPycharm)) {
            "The IntelliJ Python debugger helper is missing:\n$pydevdPycharm"
        }

        return helperDirectory
    }

    /**
     * Prepends [helperDirectory] to PYTHONPATH while retaining existing entries and avoiding an
     * exact duplicate path.
     */
    internal fun environmentWithPydevHelper(
        environment: Map<String, String>,
        helperDirectory: Path,
    ): Map<String, String> {
        val normalizedHelperDirectory =
            helperDirectory.toAbsolutePath().normalize().toString()

        val existingEntries = environment[PYTHONPATH_ENVIRONMENT_VARIABLE]
            .orEmpty()
            .split(File.pathSeparator)
            .filter(String::isNotBlank)

        val retainedEntries = existingEntries.filter { entry ->
            runCatching {
                Path.of(entry).toAbsolutePath().normalize().toString() !=
                    normalizedHelperDirectory
            }.getOrDefault(true)
        }

        return LinkedHashMap(environment).apply {
            put(
                PYTHONPATH_ENVIRONMENT_VARIABLE,
                (listOf(normalizedHelperDirectory) + retainedEntries)
                    .joinToString(File.pathSeparator),
            )
        }
    }
}