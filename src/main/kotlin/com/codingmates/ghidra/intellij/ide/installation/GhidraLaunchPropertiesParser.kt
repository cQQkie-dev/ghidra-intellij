package com.codingmates.ghidra.intellij.ide.installation

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object GhidraLaunchPropertiesParser {

    @Throws(GhidraInstallationException::class)
    fun parse(path: Path): GhidraLaunchProperties {
        if (!Files.isRegularFile(path)) {
            throw GhidraInstallationException(
                "Ghidra launch properties file does not exist: $path"
            )
        }

        val commonVmArguments = mutableListOf<String>()
        val linuxVmArguments = mutableListOf<String>()
        val macosVmArguments = mutableListOf<String>()
        val windowsVmArguments = mutableListOf<String>()

        try {
            Files.readAllLines(path).forEach { rawLine ->
                val line = rawLine.trim()

                if (line.isEmpty() || line.startsWith("#")) {
                    return@forEach
                }

                val separatorIndex = line.indexOf('=')
                if (separatorIndex <= 0) {
                    return@forEach
                }

                val key = line.substring(0, separatorIndex).trim()
                val value = line.substring(separatorIndex + 1).trim()

                when (key) {
                    "VMARGS" -> commonVmArguments.add(value)
                    "VMARGS_LINUX" -> linuxVmArguments.add(value)
                    "VMARGS_MACOS" -> macosVmArguments.add(value)
                    "VMARGS_WINDOWS" -> windowsVmArguments.add(value)
                }
            }
        }
        catch (e: IOException) {
            throw GhidraInstallationException(
                "Cannot read Ghidra launch properties file: $path",
                e,
            )
        }

        return GhidraLaunchProperties(
            commonVmArguments = commonVmArguments,
            linuxVmArguments = linuxVmArguments,
            macosVmArguments = macosVmArguments,
            windowsVmArguments = windowsVmArguments,
        )
    }
}