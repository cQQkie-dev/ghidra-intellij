package com.codingmates.ghidra.intellij.ide.extension

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.streams.toList

object GhidraExtensionProjectGenerator {

    private val validProjectName = Regex("[A-Za-z_][A-Za-z0-9_-]*")

    @Throws(GhidraExtensionProjectGenerationException::class)
    fun create(
        spec: GhidraExtensionProjectSpec,
    ): GeneratedGhidraExtensionProject {
        validate(spec)

        val skeleton = try {
            GhidraSkeletonTemplateDiscovery.discover(spec.skeletonTemplatePath)
        }
        catch (e: GhidraSkeletonTemplateException) {
            throw GhidraExtensionProjectGenerationException(
                e.message ?: "Invalid Ghidra Skeleton template.",
                e,
            )
        }

        val projectParentDirectory =
            spec.projectParentDirectory.toAbsolutePath().normalize()

        val projectDirectory = projectParentDirectory.resolve(spec.name)

        if (Files.exists(projectDirectory)) {
            throw GhidraExtensionProjectGenerationException(
                "Ghidra extension project already exists: $projectDirectory"
            )
        }

        try {
            Files.createDirectories(projectDirectory)

            copyEmptyExtensionSkeleton(
                skeletonDirectory = skeleton.canonicalPath,
                projectDirectory = projectDirectory,
            )

            val buildFile = projectDirectory.resolve("build.gradle")
            val settingsFile = projectDirectory.resolve("settings.gradle")
            val localGradlePropertiesFile =
                projectDirectory.resolve("gradle.properties")

            Files.copy(
                skeleton.buildTemplateFile,
                buildFile,
            )

            buildFile.writeText(
                buildFile.readText() +
                    """

                    // Prevent local IntelliJ and Gradle metadata from being packaged.
                    buildExtension.exclude '.idea/**'
                    buildExtension.exclude '*.iml'
                    buildExtension.exclude 'gradle/**'
                    buildExtension.exclude 'gradle.properties'
                    """.trimIndent() +
                    "\n",
                StandardCharsets.UTF_8,
            )

            settingsFile.writeText(
                "rootProject.name = \"${spec.name}\"\n",
                StandardCharsets.UTF_8,
            )

            localGradlePropertiesFile.writeText(
                "GHIDRA_INSTALL_DIR=${spec.ghidraInstallationPath.toAbsolutePath().normalize()}\n",
                StandardCharsets.UTF_8,
            )

            val gitIgnoreFile = projectDirectory.resolve(".gitignore")
            gitIgnoreFile.writeText(
                skeleton.gitIgnoreTemplateFile.readText() +
                    """

                    # IntelliJ-local Ghidra target configuration
                    gradle.properties
                    .idea/
                    *.iml
                    """.trimIndent() +
                    "\n",
                StandardCharsets.UTF_8,
            )

            projectDirectory.resolve("README.md").writeText(
                "# ${spec.name}\n",
                StandardCharsets.UTF_8,
            )

            return GeneratedGhidraExtensionProject(
                projectDirectory = projectDirectory,
                buildFile = buildFile,
                settingsFile = settingsFile,
                localGradlePropertiesFile = localGradlePropertiesFile,
            )
        }
        catch (e: IOException) {
            throw GhidraExtensionProjectGenerationException(
                "Cannot create Ghidra extension project: $projectDirectory",
                e,
            )
        }
    }

    private fun validate(spec: GhidraExtensionProjectSpec) {
        if (!validProjectName.matches(spec.name)) {
            throw GhidraExtensionProjectGenerationException(
                "Invalid Ghidra extension project name '${spec.name}'. " +
                    "Use a letter or underscore followed by letters, digits, underscores, or hyphens."
            )
        }
    }

    @Throws(IOException::class)
    private fun copyEmptyExtensionSkeleton(
        skeletonDirectory: Path,
        projectDirectory: Path,
    ) {
        Files.walk(skeletonDirectory).use { paths ->
            paths
                .toList()
                .sorted()
                .forEach { sourcePath ->
                    val relativePath = skeletonDirectory.relativize(sourcePath)
                    val relativePathString = relativePath.toString().replace('\\', '/')

                    if (
                        relativePathString.isEmpty() ||
                        shouldExcludeFromEmptyExtension(relativePathString)
                    ) {
                        return@forEach
                    }

                    val destinationPath = projectDirectory.resolve(relativePathString)

                    if (Files.isDirectory(sourcePath)) {
                        destinationPath.createDirectories()
                    }
                    else {
                        destinationPath.parent.createDirectories()
                        Files.copy(sourcePath, destinationPath)
                    }
                }
        }
    }

    private fun shouldExcludeFromEmptyExtension(
        relativePath: String,
    ): Boolean =
        relativePath == "build.gradle" ||
            relativePath == "buildTemplate.gradle" ||
            relativePath == "gitignore" ||
            relativePath == "src/main/java/skeleton" ||
            relativePath.startsWith("src/main/java/skeleton/") ||
            relativePath == "data/buildLanguage.xml" ||
            relativePath == "data/sleighArgs.txt" ||
            relativePath == "data/languages" ||
            relativePath.startsWith("data/languages/")
}