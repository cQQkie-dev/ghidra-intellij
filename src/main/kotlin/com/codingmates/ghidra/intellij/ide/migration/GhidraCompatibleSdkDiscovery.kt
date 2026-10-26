package com.codingmates.ghidra.intellij.ide.migration

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk

/**
 * Finds registered IntelliJ SDKs compatible with the selected Ghidra runtime requirements.
 */
object GhidraCompatibleSdkDiscovery {

    fun discover(
        requirements: GhidraTargetRequirements,
    ): List<GhidraCompatibleSdk> =
        ProjectJdkTable.getInstance()
            .allJdks
            .asSequence()
            .mapNotNull(::toCompatibleSdkOrNull)
            .filter { sdk ->
                requirements.isJavaCompatible(sdk.featureVersion)
            }
            .distinctBy { sdk ->
                canonicalHomePath(sdk.homePath)
            }
            .sortedWith(
                compareBy<GhidraCompatibleSdk> { it.featureVersion }
                    .thenBy { it.name }
            )
            .toList()

    private fun canonicalHomePath(
        homePath: String,
    ): String =
        runCatching {
            java.nio.file.Path.of(homePath)
                .toRealPath()
                .toString()
        }.getOrElse {
            java.nio.file.Path.of(homePath)
                .toAbsolutePath()
                .normalize()
                .toString()
        }

    private fun toCompatibleSdkOrNull(
        sdk: Sdk,
    ): GhidraCompatibleSdk? {
        val homePath = sdk.homePath ?: return null

        val featureVersion = runCatching {
            ExternalSystemJdkUtil.getJavaVersion(homePath)?.feature
        }.getOrNull() ?: return null

        return GhidraCompatibleSdk(
            name = sdk.name,
            homePath = homePath,
            featureVersion = featureVersion,
            sdk = sdk,
        )
    }
}

/**
 * A registered IntelliJ SDK that satisfies the selected Ghidra Java requirement.
 *
 * The [sdk] instance is intentionally retained because Stage 2 changes use the original SDK
 * object when setting the project SDK.
 */
data class GhidraCompatibleSdk(
    val name: String,
    val homePath: String,
    val featureVersion: Int,
    val sdk: Sdk,
) {
    override fun toString(): String =
        "$name (Java $featureVersion: $homePath)"
}