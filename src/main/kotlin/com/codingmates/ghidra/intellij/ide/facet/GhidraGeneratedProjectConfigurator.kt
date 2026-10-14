package com.codingmates.ghidra.intellij.ide.facet

import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallationRegistryService
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectInstallationDiscovery
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectKind
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectLayoutDiscovery
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetTypeRegistry
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import java.nio.file.Path

/**
 * Configures the Ghidra facet for a generated project opened through the Ghidra plug-in.
 *
 * This is intentionally invoked only from the generated project's Open Project action. It does
 * not modify unrelated Ghidra Gradle projects opened through IntelliJ normally.
 */
object GhidraGeneratedProjectConfigurator {

    /**
     * Configures the generated project's primary Gradle module.
     *
     * @return true when a recognized Ghidra project module was found and configured.
     */
    fun configure(project: Project): Boolean {
        val projectRoot = project.basePath
            ?.let(Path::of)
            ?.toAbsolutePath()
            ?.normalize()
            ?: return false

        val layout = GhidraProjectLayoutDiscovery.discover(projectRoot)
        if (
            layout.kind != GhidraProjectKind.EXTENSION &&
            layout.kind != GhidraProjectKind.SCRIPT
        ) {
            return false
        }

        val configuredInstallationPath =
            GhidraProjectInstallationDiscovery.discover(projectRoot)
                ?: return false

        val module = findProjectModule(
            project = project,
            projectRoot = projectRoot,
        ) ?: return false

        val installationRegistry = ApplicationManager.getApplication()
            .getService(GhidraInstallationRegistryService::class.java)

        val installation = installationRegistry.register(configuredInstallationPath)
        val facet = findOrCreateFacet(module)

        GhidraFacetConfigurator.configure(
            project = project,
            module = module,
            state = facet.configuration.ghidraState,
            installation = installation,
        )

        return true
    }

    private fun findProjectModule(
        project: Project,
        projectRoot: Path,
    ) = ModuleManager.getInstance(project)
        .modules
        .firstOrNull { module ->
            ModuleRootManager.getInstance(module)
                .contentRoots
                .any { contentRoot ->
                    Path.of(contentRoot.path)
                        .toAbsolutePath()
                        .normalize() == projectRoot
                }
        }

    private fun findOrCreateFacet(module: Module): GhidraFacet {
        val facetManager = FacetManager.getInstance(module)

        facetManager.getFacetByType(FACET_TYPE_ID)?.let {
            return it
        }

        val facetType = FacetTypeRegistry.getInstance()
            .findFacetType(FACET_TYPE_ID)
            ?: error("Ghidra facet type is not registered.")

        val facet = facetManager.createFacet(
            facetType,
            GhidraFacetType.FACET_NAME,
            GhidraFacetConfiguration(),
            null,
        )

        runWriteAction {
            facetManager.createModifiableModel().apply {
                addFacet(facet)
                commit()
            }
        }

        return facet
    }
}