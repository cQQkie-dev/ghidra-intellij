package com.codingmates.ghidra.intellij.ide.runConfiguration

import com.codingmates.ghidra.intellij.ide.installation.GhidraPlatform
import java.nio.file.Path

data class PyGhidraGuiCommand(
    val executable: Path,
    val arguments: List<String>,
    val workingDirectory: Path,
)

object PyGhidraGuiCommandBuilder {

    fun gui(
        ghidraInstallationPath: Path,
        workingDirectory: Path,
        extensionProjectRoot: Path?,
        platform: GhidraPlatform = GhidraPlatform.current(),
    ): PyGhidraGuiCommand {
        require(platform == GhidraPlatform.LINUX) {
            "PyGhidra GUI launching is currently supported on Linux only."
        }

        val arguments = buildList {
            add("--console")

            if (extensionProjectRoot != null) {
                add("-D")
                add(
                    "binaryPath=" +
                        "build/classes/java/main:" +
                        "build/resources/main"
                )

                add("-D")
                add(
                    "ghidra.external.modules=" +
                        extensionProjectRoot.toAbsolutePath().normalize()
                )
            }
        }

        return PyGhidraGuiCommand(
            executable = ghidraInstallationPath
                .toAbsolutePath()
                .normalize()
                .resolve("support/pyghidraRun"),
            arguments = arguments,
            workingDirectory = workingDirectory.toAbsolutePath().normalize(),
        )
    }
}