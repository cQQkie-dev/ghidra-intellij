package com.codingmates.ghidra.intellij.ide.script

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object GhidraScriptProjectGenerator {

    private val validProjectName = Regex("[A-Za-z_][A-Za-z0-9_-]*")

    @Throws(GhidraScriptProjectGenerationException::class)
    fun create(
        spec: GhidraScriptProjectSpec,
    ): GeneratedGhidraScriptProject {
        validate(spec)

        val projectParentDirectory =
            spec.projectParentDirectory.toAbsolutePath().normalize()

        val projectDirectory = projectParentDirectory.resolve(spec.name)

        if (Files.exists(projectDirectory)) {
            throw GhidraScriptProjectGenerationException(
                "Ghidra script project already exists: $projectDirectory"
            )
        }

        try {
            Files.createDirectories(projectDirectory)

            val buildFile = projectDirectory.resolve("build.gradle")
            val settingsFile = projectDirectory.resolve("settings.gradle")
            val gitIgnoreFile = projectDirectory.resolve(".gitignore")
            val localGhidraPropertiesFile = projectDirectory.resolve("ghidra-intellij.properties")
            val scriptDirectory = projectDirectory.resolve("ghidra_scripts")

            Files.writeString(
                buildFile,
                buildGradleTemplate(),
                StandardCharsets.UTF_8,
            )

            Files.writeString(
                settingsFile,
                "rootProject.name = \"${spec.name}\"\n",
                StandardCharsets.UTF_8,
            )

            Files.writeString(
                gitIgnoreFile,
                gitIgnoreTemplate(),
                StandardCharsets.UTF_8,
            )

            Files.writeString(
                localGhidraPropertiesFile,
                "GHIDRA_INSTALL_DIR=${spec.ghidraInstallationPath.toAbsolutePath().normalize()}\n",
                StandardCharsets.UTF_8,
            )

            Files.createDirectories(scriptDirectory)

            return GeneratedGhidraScriptProject(
                projectDirectory = projectDirectory,
                buildFile = buildFile,
                settingsFile = settingsFile,
                localGhidraPropertiesFile = localGhidraPropertiesFile,
                scriptDirectory = scriptDirectory,
            )
        }
        catch (e: IOException) {
            throw GhidraScriptProjectGenerationException(
                "Cannot create Ghidra script project: $projectDirectory",
                e,
            )
        }
    }

    private fun validate(spec: GhidraScriptProjectSpec) {
        if (!validProjectName.matches(spec.name)) {
            throw GhidraScriptProjectGenerationException(
                "Invalid Ghidra script project name '${spec.name}'. " +
                    "Use a letter or underscore followed by letters, digits, underscores, or hyphens."
            )
        }
    }

    private fun buildGradleTemplate(): String =
        """
        // Ghidra Script Project
        //
        // Set GHIDRA_INSTALL_DIR through one of these mechanisms:
        //
        //   ./gradlew -PGHIDRA_INSTALL_DIR=/path/to/ghidra classes
        //
        // or:
        //
        //   GHIDRA_INSTALL_DIR=/path/to/ghidra ./gradlew classes
        //
        // The IntelliJ Ghidra plugin supplies this property automatically for supported actions.

        def localGhidraProperties = new Properties()
        def localGhidraPropertiesFile = file("ghidra-intellij.properties")

        if (localGhidraPropertiesFile.isFile()) {
            localGhidraPropertiesFile.withInputStream {
                localGhidraProperties.load(it)
            }
        }

        def ghidraInstallDir =
            System.getenv("GHIDRA_INSTALL_DIR") ?:
            providers.gradleProperty("GHIDRA_INSTALL_DIR").orNull ?:
            localGhidraProperties.getProperty("GHIDRA_INSTALL_DIR")

        if (!ghidraInstallDir) {
            throw new GradleException(
                "GHIDRA_INSTALL_DIR is not defined. " +
                "Set it as an environment variable, pass " +
                "-PGHIDRA_INSTALL_DIR=<path>, or configure the IntelliJ Ghidra plugin."
            )
        }

        apply from: new File(ghidraInstallDir).getCanonicalPath() +
            "/support/buildExtension.gradle"

        sourceSets {
            main {
                java {
                    srcDir "ghidra_scripts"
                }
            }
        }
        """.trimIndent() + "\n"

    private fun gitIgnoreTemplate(): String =
        """
        .gradle/
        build/
        .idea/
        *.iml
        ghidra-intellij.properties
        """.trimIndent() + "\n"
}