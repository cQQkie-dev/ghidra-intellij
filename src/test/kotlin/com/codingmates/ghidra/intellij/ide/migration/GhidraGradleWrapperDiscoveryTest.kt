package com.codingmates.ghidra.intellij.ide.migration

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GhidraGradleWrapperDiscoveryTest {

    @Test
    fun `discovers Gradle version from bin wrapper distribution`() {
        val projectRoot = Files.createTempDirectory("ghidra-wrapper-project")

        projectRoot
            .resolve("gradle/wrapper")
            .createDirectories()

        projectRoot
            .resolve("gradle/wrapper/gradle-wrapper.properties")
            .writeText(
                """
                distributionBase=GRADLE_USER_HOME
                distributionPath=wrapper/dists
                distributionUrl=https\://services.gradle.org/distributions/gradle-9.6.0-bin.zip
                zipStoreBase=GRADLE_USER_HOME
                zipStorePath=wrapper/dists
                """.trimIndent() + "\n"
            )

        val wrapper = assertNotNull(
            GhidraGradleWrapperDiscovery.discover(projectRoot)
        )

        assertEquals(
            "https://services.gradle.org/distributions/gradle-9.6.0-bin.zip",
            wrapper.distributionUrl,
        )
        assertEquals("9.6.0", wrapper.version.toString())
    }

    @Test
    fun `discovers Gradle version from all wrapper distribution`() {
        val projectRoot = Files.createTempDirectory("ghidra-wrapper-project")

        projectRoot
            .resolve("gradle/wrapper")
            .createDirectories()

        projectRoot
            .resolve("gradle/wrapper/gradle-wrapper.properties")
            .writeText(
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.13-all.zip\n"
            )

        val wrapper = assertNotNull(
            GhidraGradleWrapperDiscovery.discover(projectRoot)
        )

        assertEquals("8.13", wrapper.version.toString())
    }

    @Test
    fun `returns null when project has no Gradle wrapper`() {
        val projectRoot = Files.createTempDirectory("ghidra-wrapper-project")

        assertNull(
            GhidraGradleWrapperDiscovery.discover(projectRoot)
        )
    }

    @Test
    fun `reports unknown version for unsupported wrapper distribution URL`() {
        val projectRoot = Files.createTempDirectory("ghidra-wrapper-project")

        projectRoot
            .resolve("gradle/wrapper")
            .createDirectories()

        projectRoot
            .resolve("gradle/wrapper/gradle-wrapper.properties")
            .writeText(
                "distributionUrl=https\\://example.invalid/gradle-custom.zip\n"
            )

        val wrapper = assertNotNull(
            GhidraGradleWrapperDiscovery.discover(projectRoot)
        )

        assertEquals(
            "https://example.invalid/gradle-custom.zip",
            wrapper.distributionUrl,
        )
        assertNull(wrapper.version)
    }
}