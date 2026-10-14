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
                includeProcessorTemplate =
                    GhidraExtensionTemplate.PROCESSOR in spec.templates,
            )

            copySelectedJavaTemplates(
                skeleton = skeleton,
                projectDirectory = projectDirectory,
                spec = spec,
            )

            val buildFile = projectDirectory.resolve("build.gradle")
            val settingsFile = projectDirectory.resolve("settings.gradle")
            val localGradlePropertiesFile =
                projectDirectory.resolve("gradle.properties")
            val antPropertiesFile =
                projectDirectory.resolve(".antProperties.xml")

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

            if (GhidraExtensionTemplate.PROCESSOR in spec.templates) {
                GhidraSleighBuildSupport.writeAntProperties(
                    projectRoot = projectDirectory,
                    ghidraInstallationPath = spec.ghidraInstallationPath,
                )
            }

            val gitIgnoreFile = projectDirectory.resolve(".gitignore")
            gitIgnoreFile.writeText(
                skeleton.gitIgnoreTemplateFile.readText() +
                    """

                    # IntelliJ-local Ghidra target configuration
                    gradle.properties
                    .antProperties.xml
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
                antPropertiesFile = antPropertiesFile.takeIf(Files::isRegularFile),
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
        includeProcessorTemplate: Boolean,
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
                        shouldExcludeFromEmptyExtension(
                            relativePath = relativePathString,
                            includeProcessorTemplate = includeProcessorTemplate,
                        )
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

    @Throws(
        IOException::class,
        GhidraExtensionProjectGenerationException::class,
    )
    private fun copySelectedJavaTemplates(
        skeleton: GhidraSkeletonTemplate,
        projectDirectory: Path,
        spec: GhidraExtensionProjectSpec,
    ) {
        if (spec.templates.isEmpty()) {
            return
        }

        val packageName = extensionPackageName(spec.name)
        val classBaseName = extensionClassBaseName(spec.name)
        val destinationDirectory = projectDirectory
            .resolve("src/main/java")
            .resolve(packageName)

        Files.createDirectories(destinationDirectory)

        spec.templates.forEach { template ->
            val skeletonFileName = template.skeletonFileName ?: return@forEach
            val skeletonClassName = template.skeletonClassName ?: return@forEach
            val generatedClassSuffix = template.generatedClassSuffix ?: return@forEach

            val sourceFile = skeleton.javaTemplateDirectory
                .resolve(skeletonFileName)

            if (!Files.isRegularFile(sourceFile)) {
                throw GhidraExtensionProjectGenerationException(
                    "Ghidra Skeleton template is missing Java source: $sourceFile"
                )
            }

            val generatedClassName =
                classBaseName + generatedClassSuffix

            val source = Files.readString(sourceFile, StandardCharsets.UTF_8)

            val generatedSource = source
                .replace(
                    "package skeleton;",
                    "package $packageName;",
                )
                .replace(
                    skeletonClassName,
                    generatedClassName,
                )

            Files.writeString(
                destinationDirectory.resolve("$generatedClassName.java"),
                generatedSource,
                StandardCharsets.UTF_8,
            )
        }
    }

    private fun extensionPackageName(projectName: String): String =
        projectName.lowercase().replace('-', '_')

    private fun extensionClassBaseName(projectName: String): String =
        projectName
            .split('-', '_')
            .filter(String::isNotEmpty)
            .joinToString("") { component ->
                component.replaceFirstChar { character ->
                    character.uppercase()
                }
            }

    private fun shouldExcludeFromEmptyExtension(
        relativePath: String,
        includeProcessorTemplate: Boolean,
    ): Boolean =
        relativePath == "build.gradle" ||
            relativePath == "src/main/help" ||
            relativePath.startsWith("src/main/help/") ||
            relativePath == "buildTemplate.gradle" ||
            relativePath == "gitignore" ||
            relativePath == "src/main/java/skeleton" ||
            relativePath.startsWith("src/main/java/skeleton/") ||
            (
                !includeProcessorTemplate &&
                    (
                        relativePath == "data/buildLanguage.xml" ||
                            relativePath == "data/sleighArgs.txt" ||
                            relativePath == "data/languages" ||
                            relativePath.startsWith("data/languages/")
                    )
                )
}