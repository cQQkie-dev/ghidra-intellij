package com.codingmates.ghidra.intellij.ide.extension

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object GhidraSkeletonTemplateDiscovery {

    @Throws(GhidraSkeletonTemplateException::class)
    fun discover(configuredPath: Path): GhidraSkeletonTemplate {
        val normalizedConfiguredPath =
            configuredPath.toAbsolutePath().normalize()

        val canonicalPath = try {
            normalizedConfiguredPath.toRealPath()
        }
        catch (e: IOException) {
            throw GhidraSkeletonTemplateException(
                "Cannot resolve Ghidra Skeleton template directory: $normalizedConfiguredPath",
                e,
            )
        }

        if (!Files.isDirectory(canonicalPath)) {
            throw GhidraSkeletonTemplateException(
                "Ghidra Skeleton template is not a directory: $canonicalPath"
            )
        }

        val template = GhidraSkeletonTemplate(
            configuredPath = normalizedConfiguredPath,
            canonicalPath = canonicalPath,
        )

        requireRegularFile(template.buildTemplateFile)
        requireRegularFile(template.extensionPropertiesFile)
        requireRegularFile(template.moduleManifestFile)
        requireRegularFile(template.gitIgnoreTemplateFile)
        requireDirectory(template.javaTemplateDirectory)

        return template
    }

    private fun requireRegularFile(path: Path) {
        if (!Files.isRegularFile(path)) {
            throw GhidraSkeletonTemplateException(
                "Invalid Ghidra Skeleton template: missing $path"
            )
        }
    }

    private fun requireDirectory(path: Path) {
        if (!Files.isDirectory(path)) {
            throw GhidraSkeletonTemplateException(
                "Invalid Ghidra Skeleton template: missing $path"
            )
        }
    }
}