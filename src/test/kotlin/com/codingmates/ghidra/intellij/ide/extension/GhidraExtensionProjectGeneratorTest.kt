package com.codingmates.ghidra.intellij.ide.extension

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GhidraExtensionProjectGeneratorTest {

    @Test
    fun `creates empty Gradle Ghidra extension project`() {
        val parentDirectory = Files.createTempDirectory("ghidra-extension-projects")
        val skeletonDirectory = createSkeletonTemplate()

        val generated = GhidraExtensionProjectGenerator.create(
            GhidraExtensionProjectSpec(
                name = "ExampleExtension",
                projectParentDirectory = parentDirectory,
                skeletonTemplatePath = skeletonDirectory,
                ghidraInstallationPath = Path.of("/example/ghidra"),
            )
        )

        val projectDirectory = parentDirectory.resolve("ExampleExtension")

        assertEquals(projectDirectory, generated.projectDirectory)
        assertTrue(generated.buildFile.exists())
        assertTrue(generated.settingsFile.exists())
        assertTrue(generated.localGradlePropertiesFile.exists())

        assertTrue(projectDirectory.resolve("extension.properties").exists())
        assertTrue(projectDirectory.resolve("Module.manifest").exists())
        assertTrue(projectDirectory.resolve("ghidra_scripts/README.txt").exists())

        assertFalse(projectDirectory.resolve("src/main/java/skeleton").exists())
        assertFalse(projectDirectory.resolve("data/buildLanguage.xml").exists())
        assertFalse(projectDirectory.resolve("data/languages").exists())

        assertEquals(
            "rootProject.name = \"ExampleExtension\"\n",
            generated.settingsFile.readText(),
        )
        assertEquals(
            "GHIDRA_INSTALL_DIR=/example/ghidra\n",
            generated.localGradlePropertiesFile.readText(),
        )
        assertTrue(
            generated.buildFile.readText().contains("buildExtension.exclude '.idea/**'")
        )
        assertTrue(
            projectDirectory.resolve(".gitignore")
                .readText()
                .contains("gradle.properties")
        )
    }

    @Test
    fun `creates selected Plugin and Analyzer templates`() {
        val parentDirectory = Files.createTempDirectory("ghidra-extension-projects")
        val skeletonDirectory = createSkeletonTemplate()

        val generated = GhidraExtensionProjectGenerator.create(
            GhidraExtensionProjectSpec(
                name = "ExampleExtension",
                projectParentDirectory = parentDirectory,
                skeletonTemplatePath = skeletonDirectory,
                ghidraInstallationPath = Path.of("/example/ghidra"),
                templates = setOf(
                    GhidraExtensionTemplate.PLUGIN,
                    GhidraExtensionTemplate.ANALYZER,
                    GhidraExtensionTemplate.LOADER,
                    GhidraExtensionTemplate.FILE_SYSTEM,
                    GhidraExtensionTemplate.EXPORTER,
                ),
            )
        )

        val sourceDirectory = generated.projectDirectory
            .resolve("src/main/java/exampleextension")

        val pluginSource = sourceDirectory
            .resolve("ExampleExtensionPlugin.java")

        val analyzerSource = sourceDirectory
            .resolve("ExampleExtensionAnalyzer.java")

        val loaderSource = sourceDirectory
            .resolve("ExampleExtensionLoader.java")

        val fileSystemSource = sourceDirectory
            .resolve("ExampleExtensionFileSystem.java")

        val exporterSource = sourceDirectory
            .resolve("ExampleExtensionExporter.java")

        assertTrue(pluginSource.exists())
        assertTrue(analyzerSource.exists())
        assertTrue(loaderSource.exists())
        assertTrue(fileSystemSource.exists())
        assertTrue(exporterSource.exists())

        assertTrue(
            pluginSource.readText().contains(
                "package exampleextension;"
            )
        )
        assertTrue(
            pluginSource.readText().contains(
                "class ExampleExtensionPlugin"
            )
        )
        assertTrue(
            analyzerSource.readText().contains(
                "package exampleextension;"
            )
        )
        assertTrue(
            analyzerSource.readText().contains(
                "class ExampleExtensionAnalyzer"
            )
        )

        assertTrue(
            loaderSource.readText().contains(
                "class ExampleExtensionLoader"
            )
        )
        assertTrue(
            fileSystemSource.readText().contains(
                "class ExampleExtensionFileSystem"
            )
        )
        assertTrue(
            exporterSource.readText().contains(
                "class ExampleExtensionExporter"
            )
        )        
    }

    @Test
    fun `rejects invalid extension project name`() {
        val exception =
            assertFailsWith<GhidraExtensionProjectGenerationException> {
                GhidraExtensionProjectGenerator.create(
                    GhidraExtensionProjectSpec(
                        name = "123 invalid extension",
                        projectParentDirectory =
                            Files.createTempDirectory("ghidra-extension-projects"),
                        skeletonTemplatePath = createSkeletonTemplate(),
                        ghidraInstallationPath = Path.of("/example/ghidra"),
                    )
                )
            }

        assertTrue(exception.message!!.contains("Invalid Ghidra extension project name"))
    }

    @Test
    fun `does not overwrite existing extension project directory`() {
        val parentDirectory = Files.createTempDirectory("ghidra-extension-projects")
        Files.createDirectories(parentDirectory.resolve("ExampleExtension"))

        assertFailsWith<GhidraExtensionProjectGenerationException> {
            GhidraExtensionProjectGenerator.create(
                GhidraExtensionProjectSpec(
                    name = "ExampleExtension",
                    projectParentDirectory = parentDirectory,
                    skeletonTemplatePath = createSkeletonTemplate(),
                    ghidraInstallationPath = Path.of("/example/ghidra"),
                )
            )
        }
    }

    private fun createSkeletonTemplate(): Path {
        val skeletonDirectory = Files.createTempDirectory("ghidra-skeleton")

        skeletonDirectory.resolve("build.gradle").writeText("// source-tree build")
        skeletonDirectory.resolve("buildTemplate.gradle").writeText(
            """
            def ghidraInstallDir
            task distributeExtension {
                apply from: new File(ghidraInstallDir).getCanonicalPath() +
                    "/support/buildExtension.gradle"
            }
            """.trimIndent() + "\n"
        )
        skeletonDirectory.resolve("extension.properties").writeText(
            "name=@extname@\nversion=@extversion@\n"
        )
        skeletonDirectory.resolve("Module.manifest").writeText("")
        skeletonDirectory.resolve("gitignore").writeText("build/\n")
        skeletonDirectory.resolve("README.md").writeText("# Skeleton\n")

        skeletonDirectory.resolve("ghidra_scripts").createDirectories()
        skeletonDirectory.resolve("ghidra_scripts/README.txt").writeText("Scripts\n")

        skeletonDirectory.resolve("src/main/java/skeleton").createDirectories()

        skeletonDirectory.resolve("src/main/java/skeleton/SkeletonPlugin.java")
            .writeText(
                """
                package skeleton;

                public class SkeletonPlugin {
                }
                """.trimIndent() + "\n"
            )

        skeletonDirectory.resolve("src/main/java/skeleton/SkeletonAnalyzer.java")
            .writeText(
                """
                package skeleton;

                public class SkeletonAnalyzer {
                }
                """.trimIndent() + "\n"
            )

        skeletonDirectory.resolve("src/main/java/skeleton/SkeletonLoader.java")
            .writeText(
                """
                package skeleton;

                public class SkeletonLoader {
                }
                """.trimIndent() + "\n"
            )

        skeletonDirectory.resolve("src/main/java/skeleton/SkeletonFileSystem.java")
            .writeText(
                """
                package skeleton;

                public class SkeletonFileSystem {
                }
                """.trimIndent() + "\n"
            )

        skeletonDirectory.resolve("src/main/java/skeleton/SkeletonExporter.java")
            .writeText(
                """
                package skeleton;

                public class SkeletonExporter {
                }
                """.trimIndent() + "\n"
            )

        skeletonDirectory.resolve("data/languages").createDirectories()
        skeletonDirectory.resolve("data/languages/skel.slaspec").writeText("language\n")
        skeletonDirectory.resolve("data/buildLanguage.xml").writeText("<project />")
        skeletonDirectory.resolve("data/sleighArgs.txt").writeText("# args\n")

        return skeletonDirectory
    }
}