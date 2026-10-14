package com.codingmates.ghidra.intellij.ide.extension

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object GhidraSleighBuildSupport {

    private const val BUILD_LANGUAGE_FILE = "data/buildLanguage.xml"
    private const val ANT_PROPERTIES_FILE = ".antProperties.xml"

    fun buildLanguageFile(projectRoot: Path): Path =
        projectRoot.resolve(BUILD_LANGUAGE_FILE)

    fun hasBuildLanguageFile(projectRoot: Path): Boolean =
        Files.isRegularFile(buildLanguageFile(projectRoot))

    @Throws(IOException::class)
    fun writeAntProperties(
        projectRoot: Path,
        ghidraInstallationPath: Path,
    ): Path {
        val antPropertiesFile = projectRoot.resolve(ANT_PROPERTIES_FILE)

        Files.writeString(
            antPropertiesFile,
            antPropertiesTemplate(ghidraInstallationPath),
            StandardCharsets.UTF_8,
        )

        return antPropertiesFile
    }

    private fun antPropertiesTemplate(
        ghidraInstallationPath: Path,
    ): String {
        val installationPath = ghidraInstallationPath
            .toAbsolutePath()
            .normalize()
            .toString()
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
              <property name="ghidra.install.dir" value="$installationPath" />
            </project>
            """.trimIndent() + "\n"
    }
}