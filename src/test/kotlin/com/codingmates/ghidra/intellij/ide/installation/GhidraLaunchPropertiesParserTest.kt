package com.codingmates.ghidra.intellij.ide.installation

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GhidraLaunchPropertiesParserTest {

    @Test
    fun `preserves repeated and platform-specific VM arguments`() {
        val launchProperties = Files.createTempFile("launch", ".properties")
        launchProperties.writeText(
            """
            # Comment
            VMARGS=-Djava.system.class.loader=ghidra.GhidraClassLoader
            VMARGS=-Dfile.encoding=UTF8
            VMARGS=-Duser.variant=
            VMARGS_LINUX=-Dsun.java2d.xrender=true
            VMARGS_MACOS=-Dapple.laf.useScreenMenuBar=false
            VMARGS_WINDOWS=-Dlog4j.skipJansi=true
            ENVVARS_LINUX=IGNORED=true
            """.trimIndent()
        )

        val parsed = GhidraLaunchPropertiesParser.parse(launchProperties)

        assertEquals(
            listOf(
                "-Djava.system.class.loader=ghidra.GhidraClassLoader",
                "-Dfile.encoding=UTF8",
                "-Duser.variant=",
            ),
            parsed.commonVmArguments,
        )
        assertEquals(
            listOf("-Dsun.java2d.xrender=true"),
            parsed.linuxVmArguments,
        )
        assertEquals(
            listOf("-Dapple.laf.useScreenMenuBar=false"),
            parsed.macosVmArguments,
        )
        assertEquals(
            listOf("-Dlog4j.skipJansi=true"),
            parsed.windowsVmArguments,
        )
        assertEquals(
            listOf(
                "-Djava.system.class.loader=ghidra.GhidraClassLoader",
                "-Dfile.encoding=UTF8",
                "-Duser.variant=",
                "-Dsun.java2d.xrender=true",
            ),
            parsed.vmArgumentsFor(GhidraPlatform.LINUX),
        )
    }

    @Test
    fun `returns common arguments for unsupported platform`() {
        val properties = GhidraLaunchProperties(
            commonVmArguments = listOf("-Dfile.encoding=UTF8"),
            linuxVmArguments = listOf("-Dlinux=true"),
            macosVmArguments = listOf("-Dmacos=true"),
            windowsVmArguments = listOf("-Dwindows=true"),
        )

        assertEquals(
            listOf("-Dfile.encoding=UTF8"),
            properties.vmArgumentsFor(GhidraPlatform.OTHER),
        )
    }

    @Test
    fun `rejects missing launch properties file`() {
        val missingFile = Files.createTempDirectory("missing-launch-properties")
            .resolve("launch.properties")

        assertFailsWith<GhidraInstallationException> {
            GhidraLaunchPropertiesParser.parse(missingFile)
        }
    }
}