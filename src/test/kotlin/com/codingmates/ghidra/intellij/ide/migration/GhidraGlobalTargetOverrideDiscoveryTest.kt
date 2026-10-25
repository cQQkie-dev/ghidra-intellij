package com.codingmates.ghidra.intellij.ide.migration

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GhidraGlobalTargetOverrideDiscoveryTest {

    @Test
    fun `discovers Ghidra target from process environment`() {
        val override = assertNotNull(
            GhidraGlobalTargetOverrideDiscovery.discoverEnvironmentOverride(
                mapOf(
                    "GHIDRA_INSTALL_DIR" to "/example/ghidra/current",
                    "PATH" to "/usr/bin",
                )
            )
        )

        assertEquals(
            GhidraTargetOverrideSource.ENVIRONMENT,
            override.source,
        )
        assertEquals("/example/ghidra/current", override.configuredValue)
        assertEquals(
            "/example/ghidra/current",
            override.installationPath.toString(),
        )
        assertNull(override.propertiesFile)
    }

    @Test
    fun `ignores missing or blank environment target`() {
        assertNull(
            GhidraGlobalTargetOverrideDiscovery.discoverEnvironmentOverride(
                emptyMap()
            )
        )

        assertNull(
            GhidraGlobalTargetOverrideDiscovery.discoverEnvironmentOverride(
                mapOf("GHIDRA_INSTALL_DIR" to "   ")
            )
        )
    }

    @Test
    fun `discovers Ghidra target from user Gradle properties`() {
        val gradleUserHome = Files.createTempDirectory("gradle-user-home")

        gradleUserHome.resolve("gradle.properties").writeText(
            """
            org.gradle.parallel=true
            GHIDRA_INSTALL_DIR=/example/ghidra/current
            """.trimIndent() + "\n"
        )

        val override = assertNotNull(
            GhidraGlobalTargetOverrideDiscovery
                .discoverUserGradlePropertiesOverride(gradleUserHome)
        )

        assertEquals(
            GhidraTargetOverrideSource.USER_GRADLE_PROPERTIES,
            override.source,
        )
        assertEquals("/example/ghidra/current", override.configuredValue)
        assertEquals(
            gradleUserHome.resolve("gradle.properties"),
            override.propertiesFile,
        )
    }

    @Test
    fun `returns null when user Gradle properties have no Ghidra target`() {
        val gradleUserHome = Files.createTempDirectory("gradle-user-home")

        gradleUserHome.resolve("gradle.properties").writeText(
            "org.gradle.parallel=true\n"
        )

        assertNull(
            GhidraGlobalTargetOverrideDiscovery
                .discoverUserGradlePropertiesOverride(gradleUserHome)
        )
    }

    @Test
    fun `returns null when user Gradle properties do not exist`() {
        val gradleUserHome = Files.createTempDirectory("gradle-user-home")

        assertNull(
            GhidraGlobalTargetOverrideDiscovery
                .discoverUserGradlePropertiesOverride(gradleUserHome)
        )
    }
}