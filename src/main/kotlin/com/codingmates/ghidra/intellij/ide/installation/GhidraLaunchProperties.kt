package com.codingmates.ghidra.intellij.ide.installation

enum class GhidraPlatform {
    LINUX,
    MACOS,
    WINDOWS,
    OTHER;

    companion object {
        fun current(): GhidraPlatform {
            val osName = System.getProperty("os.name").lowercase()

            return when {
                osName.contains("linux") -> LINUX
                osName.contains("mac") || osName.contains("darwin") -> MACOS
                osName.contains("windows") -> WINDOWS
                else -> OTHER
            }
        }
    }
}

/**
 * Parsed VM arguments from Ghidra's `support/launch.properties`.
 *
 * Ghidra permits repeated `VMARGS` keys, so the parser preserves every occurrence
 * in source-file order.
 */
data class GhidraLaunchProperties(
    val commonVmArguments: List<String>,
    val linuxVmArguments: List<String>,
    val macosVmArguments: List<String>,
    val windowsVmArguments: List<String>,
) {
    fun vmArgumentsFor(platform: GhidraPlatform): List<String> =
        commonVmArguments + when (platform) {
            GhidraPlatform.LINUX -> linuxVmArguments
            GhidraPlatform.MACOS -> macosVmArguments
            GhidraPlatform.WINDOWS -> windowsVmArguments
            GhidraPlatform.OTHER -> emptyList()
        }
}