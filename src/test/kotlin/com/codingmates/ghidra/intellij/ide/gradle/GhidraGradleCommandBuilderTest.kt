package com.codingmates.ghidra.intellij.ide.gradle

import com.codingmates.ghidra.intellij.ide.installation.GhidraPlatform
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GhidraGradleCommandBuilderTest {

    @Test
    fun `uses Unix Gradle Wrapper when present`() {
        val projectRoot = Files.createTempDirectory("ghidra-extension")
        projectRoot.resolve("gradlew").writeText("#!/bin/sh")

        val ghidraInstallation = Files.createTempDirectory("ghidra-installation")

        val command = GhidraGradleCommandBuilder.distributeExtension(
            projectRoot = projectRoot,
            ghidraInstallationPath = ghidraInstallation,
            platform = GhidraPlatform.LINUX,
        )

        assertEquals(projectRoot.resolve("gradlew").toString(), command.executable)
        assertEquals(
            listOf(
                "-PGHIDRA_INSTALL_DIR=${ghidraInstallation.toAbsolutePath().normalize()}",
                "distributeExtension",
            ),
            command.arguments,
        )
        assertEquals(projectRoot.toAbsolutePath().normalize(), command.workingDirectory)
        assertTrue(command.usesWrapper)
    }

    @Test
    fun `uses Windows Gradle Wrapper when present`() {
        val projectRoot = Files.createTempDirectory("ghidra-extension")
        projectRoot.resolve("gradlew.bat").writeText("@echo off")

        val ghidraInstallation = Files.createTempDirectory("ghidra-installation")

        val command = GhidraGradleCommandBuilder.distributeExtension(
            projectRoot = projectRoot,
            ghidraInstallationPath = ghidraInstallation,
            platform = GhidraPlatform.WINDOWS,
        )

        assertEquals(projectRoot.resolve("gradlew.bat").toString(), command.executable)
        assertTrue(command.usesWrapper)
    }

    @Test
    fun `falls back to Gradle from PATH when no wrapper exists`() {
        val projectRoot = Files.createTempDirectory("ghidra-extension")
        val ghidraInstallation = Files.createTempDirectory("ghidra-installation")

        val command = GhidraGradleCommandBuilder.distributeExtension(
            projectRoot = projectRoot,
            ghidraInstallationPath = ghidraInstallation,
            platform = GhidraPlatform.LINUX,
        )

        assertEquals("gradle", command.executable)
        assertFalse(command.usesWrapper)
    }
}